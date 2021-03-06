package bindiego;

import bindiego.BindiegoStreamingOptions;
import bindiego.ethereum.TrimRawBlockData;
import bindiego.ethereum.TrimRawTransactionData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import java.sql.*;
import java.net.URL;
import java.io.ByteArrayInputStream;
import java.io.IOException;

// Import SLF4J packages.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.api.services.bigquery.model.TableFieldSchema;
import com.google.api.services.bigquery.model.TableSchema;
import com.google.api.services.bigquery.model.TableReference;
import com.google.api.services.bigquery.model.TableRow;
import com.google.api.services.bigquery.model.Clustering;
import com.google.api.services.bigquery.model.TimePartitioning;

import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.TextIO;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.CreateDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.BigQueryIO.Write.WriteDisposition;
import org.apache.beam.sdk.io.gcp.bigquery.InsertRetryPolicy;
import org.apache.beam.sdk.io.gcp.bigquery.TableRowJsonCoder;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;
import org.apache.beam.sdk.io.FileBasedSink;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.io.fs.ResourceId;
import org.apache.beam.sdk.io.jdbc.JdbcIO;
import org.apache.beam.sdk.io.GenerateSequence;
import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Distribution;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.StreamingOptions;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.options.ValueProvider.NestedValueProvider;
import org.apache.beam.sdk.transforms.Count;
import org.apache.beam.sdk.transforms.Sum;
import org.apache.beam.sdk.transforms.Min;
import org.apache.beam.sdk.transforms.Max;
import org.apache.beam.sdk.transforms.Mean;
import org.apache.beam.sdk.transforms.Latest;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.transforms.ParDo;
import org.apache.beam.sdk.transforms.Partition;
import org.apache.beam.sdk.transforms.Partition.PartitionFn;
import org.apache.beam.sdk.transforms.DoFn.MultiOutputReceiver;
import org.apache.beam.sdk.transforms.SerializableFunction;
import org.apache.beam.sdk.transforms.SimpleFunction;
import org.apache.beam.sdk.transforms.windowing.Window;
import org.apache.beam.sdk.transforms.windowing.FixedWindows;
import org.apache.beam.sdk.transforms.windowing.AfterEach;
import org.apache.beam.sdk.transforms.windowing.AfterProcessingTime;
import org.apache.beam.sdk.transforms.windowing.AfterWatermark;
import org.apache.beam.sdk.transforms.windowing.BoundedWindow;
import org.apache.beam.sdk.transforms.windowing.IntervalWindow;
import org.apache.beam.sdk.transforms.windowing.Repeatedly;
import org.apache.beam.sdk.transforms.windowing.AfterPane;
import org.apache.beam.sdk.transforms.windowing.Window.ClosingBehavior;
import org.apache.beam.sdk.transforms.windowing.GlobalWindows;
import org.apache.beam.sdk.transforms.ToString;
import org.apache.beam.sdk.transforms.WithTimestamps;
import org.apache.beam.sdk.transforms.GroupByKey;
import org.apache.beam.sdk.transforms.Combine;
import org.apache.beam.sdk.transforms.View;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.apache.beam.sdk.values.PCollectionList;
import org.apache.beam.sdk.values.TupleTag;
import org.apache.beam.sdk.values.TupleTagList;
import org.apache.beam.sdk.values.PCollectionView;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.KvCoder;
import org.apache.beam.sdk.coders.StringUtf8Coder;
import org.apache.beam.sdk.coders.Coder.Context;
import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.json.JSONArray;
import org.json.JSONObject;
import org.joda.time.Duration;
import org.joda.time.Instant;
// import org.codehaus.jackson.map.ObjectMapper;

import org.apache.commons.io.FilenameUtils;

import bindiego.io.WindowedFilenamePolicy;
import bindiego.utils.DurationUtils;
import bindiego.utils.SchemaParser;
import bindiego.io.ElasticsearchIO;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;

public class BindiegoStreaming {
    /* extract the csv payload from message */
    public static class ExtractPayload extends DoFn<PubsubMessage, String> {

        public ExtractPayload() {}

        @Setup 
        public void setup() {
            mapper = new ObjectMapper();
        }

        @ProcessElement
        public void processElement(ProcessContext ctx, MultiOutputReceiver r) 
                throws IllegalArgumentException {

            String payload = null;

            // TODO: data validation here to prevent later various outputs inconsistency
            try {
                PubsubMessage psmsg = ctx.element();
                // polkdadot node logs data
                payload = new String(psmsg.getPayload(), StandardCharsets.UTF_8);

                logger.debug("Extracted raw message: " + payload);

                // JsonNode json = mapper.readValue(payload, JsonNode.class);
                JsonNode json = mapper.readTree(payload);
                ObjectNode jsonRoot = (ObjectNode) json;

                // Optional: add timestamp in Elasticsearch's native tongue
                jsonRoot.put("@timestamp", jsonRoot.get("timestamp").asText());
                jsonRoot.remove("timestamp");

                /*
                // Extract host & protocol from URL
                URL url = new URL(jsonRoot.get("httpRequest").get("requestUrl").asText());
                ((ObjectNode) jsonRoot.get("httpRequest")).put("requestDomain", url.getHost());
                ((ObjectNode) jsonRoot.get("httpRequest")).put("requestProtocol", url.getProtocol());

                // REVISIT: quick impl, not ideal but works
                // Extract resource type from URL, e.g. .txt .m4s, .m3u8, .ts, .tar.gz, .js, .html etc.
                int cutoffLength = 6;
                String urlStr = jsonRoot.get("httpRequest").get("requestUrl").asText();
                int urlLength = urlStr.length();
                if (cutoffLength < urlLength) {
                    String partialUrl = urlStr.substring(urlLength - cutoffLength);

                    if (partialUrl.contains(".")) {
                        ((ObjectNode) jsonRoot.get("httpRequest"))
                            .put("resourceType", FilenameUtils.getExtension(partialUrl));
                    }
                }

                // Extract the backend latency, latency between GFE_layer1 and origin
                String latency = null;
                Double backendLatency = null;
                if (null != jsonRoot.get("httpRequest").get("latency")) {
                    latency = jsonRoot.get("httpRequest").get("latency").asText();
                    ((ObjectNode) jsonRoot.get("httpRequest")).remove("latency");
                } else if (null != jsonRoot.get("jsonPayload").get("latencySeconds")) {
                    latency = jsonRoot.get("jsonPayload").get("latencySeconds").asText();
                    ((ObjectNode) jsonRoot.get("jsonPayload")).remove("latencySeconds");
                }
                if (null != latency) {
                    backendLatency = Double.valueOf(latency.substring(0, latency.length() - 1));
                    ((ObjectNode) jsonRoot.get("httpRequest"))
                        .put("backendLatency", backendLatency);
                }

                // backednLatency2, latency between GFE_layer2 and origin
                Double backendLatency2 = null;
                if (null != jsonRoot.get("jsonPayload").get("backendLatency")) {
                    String latency2 = new String(jsonRoot.get("jsonPayload").get("backendLatency").asText());
                    backendLatency2 = Double.valueOf(latency2.substring(0, latency2.length() - 1));
                    ((ObjectNode) jsonRoot.get("httpRequest"))
                        .put("backendLatency2", backendLatency2);
                    ((ObjectNode) jsonRoot.get("jsonPayload")).remove("backendLatency");

                    // calculate latency between GFE_layer1 and GFE_layer2
                    if (null != backendLatency) {
                        ((ObjectNode) jsonRoot.get("httpRequest"))
                            .put("gfeLatency", (backendLatency - backendLatency2));
                    }
                }

                // Frontend SRTT, latency between client and GFE_layer1
                Double feSrtt = null;
                if (null != jsonRoot.get("jsonPayload").get("frontendSrtt")) {
                    String feSrttStr = new String(jsonRoot.get("jsonPayload").get("frontendSrtt").asText());
                    feSrtt = Double.valueOf(feSrttStr.substring(0, feSrttStr.length() - 1));
                    ((ObjectNode) jsonRoot.get("httpRequest"))
                        .put("frontendSrtt", feSrtt);
                    ((ObjectNode) jsonRoot.get("jsonPayload")).remove("frontendSrtt");
                }

                // Get CachedID / Pop location ISO3166-1 3-letter city code
                if (null != jsonRoot.get("jsonPayload").get("cacheId")) {
                    // REVISIT: test string length
                    String cachedIdCityCode = new String(jsonRoot.get("jsonPayload").get("cacheId").asText())
                        .substring(0, 3);
                    ((ObjectNode) jsonRoot.get("jsonPayload"))
                        .put("cacheIdCityCode", cachedIdCityCode);
                }
                */

                r.get(STR_OUT).output(mapper.writeValueAsString(json));

                // use this only if the element doesn't have an event timestamp attached to it
                // e.g. extract 'extractedTs' from psmsg.split(",")[0] from a CSV payload
                // r.get(STR_OUT).outputWithTimestamp(str, extractedTs);
            } catch (Exception ex) {
                if (null == payload)
                    payload = "Failed to extract pubsub payload";

                r.get(STR_FAILURE_OUT).output(payload);

                logger.error("Failed extract pubsub message", ex);
            }
        }

        private ObjectMapper mapper;
    }

    public static class ExtractDataPayload extends DoFn<PubsubMessage, String> {

        public ExtractDataPayload() {}

        @Setup 
        public void setup() {
            mapper = new ObjectMapper();
        }

        @ProcessElement
        public void processElement(ProcessContext ctx, OutputReceiver<String> r) 
                throws IllegalArgumentException {

            String payload = null;

            try {
                PubsubMessage psmsg = ctx.element();
                // Polkadot RPC extracted json data
                payload = new String(psmsg.getPayload(), StandardCharsets.UTF_8);

                logger.debug("Extracted raw message: " + payload);

                JsonNode json = mapper.readTree(payload);
                ObjectNode jsonRoot = (ObjectNode) json;

                // Optional: add timestamp in Elasticsearch's native tongue
                jsonRoot.put("@timestamp", jsonRoot.get("blocktime").asLong());
                // jsonRoot.remove("blocktime");

                r.output(mapper.writeValueAsString(json));
            } catch (Exception ex) {
                if (null == payload)
                    payload = "Failed to extract pubsub payload";

                logger.error("Failed extract pubsub message", ex);
            }
        }

        private ObjectMapper mapper;
    }

    static void run(BindiegoStreamingOptions options) throws Exception {
        // FileSystems.setDefaultPipelineOptions(options);

        Pipeline p = Pipeline.create(options);

        /* Ethereum data */

        // Etherum Blocks
        PCollection<PubsubMessage> ethBlkRaw = p.apply("Read Pubsub - Ethereum Blocks data", 
            PubsubIO.readMessages()
                .fromSubscription(options.getEthdataBlkSub()));
        PCollection<String> ethBlkJson = ethBlkRaw.apply("Extract Blocks Data",
            ParDo.of(new TrimRawBlockData()));

        // Blocks to Elasticsearch
        ethBlkJson.apply(options.getWindowSize() + " window for block data",
            Window.<String>into(FixedWindows.of(DurationUtils.parseDuration(options.getWindowSize())))
                .triggering(
                    AfterWatermark.pastEndOfWindow()
                        .withEarlyFirings(
                            AfterProcessingTime
                                .pastFirstElementInPane() 
                                .plusDelayOf(DurationUtils.parseDuration(options.getEarlyFiringPeriod())))
                        .withLateFirings(
                            AfterPane.elementCountAtLeast(
                                options.getLateFiringCount().intValue()))
                )
                .discardingFiredPanes() // e.g. .accumulatingFiredPanes() etc.
                .withAllowedLateness(DurationUtils.parseDuration(options.getAllowedLateness()),
                    ClosingBehavior.FIRE_IF_NON_EMPTY))
            .apply("Append block data to Elasticsearch",
                ElasticsearchIO.append()
                    .withMaxBatchSize(options.getEsMaxBatchSize())
                    .withMaxBatchSizeBytes(options.getEsMaxBatchBytes())
                    .withConnectionConf(
                        ElasticsearchIO.ConnectionConf.create(
                            options.getEsHost(),
                            options.getEthBlkIdx())
                                .withUsername(options.getEsUser())
                                .withPassword(options.getEsPass())
                                .withNumThread(options.getEsNumThread()))
                                //.withTrustSelfSignedCerts(true)) // false by default
                    .withRetryConf(
                        ElasticsearchIO.RetryConf.create(6, Duration.standardSeconds(60))));

        // Blocks to BigQuery
        ethBlkJson.apply("Prepare Block BQ TableRow",
            ParDo.of(
                new DoFn<String, TableRow>() {
                    @ProcessElement
                    public void processElement(ProcessContext ctx) {
                        String jsonBlk = ctx.element();

                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode json = mapper.readTree(jsonBlk);
                            ObjectNode jsonRoot = (ObjectNode) json;
                            jsonRoot.remove("@timestamp");

                            TableRow tb = TableRowJsonCoder.of().decode(
                                new ByteArrayInputStream(
                                    mapper.writeValueAsString(json)
                                        .getBytes(StandardCharsets.UTF_8)),
                                Context.OUTER
                            );
                            logger.info("Block:" + tb.toPrettyString());

                            ctx.output(tb);
                        } catch (java.io.IOException ex) {
                            logger.error("Failed creating BQ Block TableRow", ex);
                        }
                    } // End processElement
                } // End DoFn
            ) // End ParDo
        ).apply("Insert BigQuery - Block",
            BigQueryIO.writeTableRows()
                .withSchema(
                    NestedValueProvider.of(
                        options.getEthBqBlk(),
                        new SerializableFunction<String, TableSchema>() {
                            @Override
                            public TableSchema apply(String jsonPath) {
                                TableSchema tableSchema = new TableSchema();
                                List<TableFieldSchema> fields = new ArrayList<>();
                                SchemaParser schemaParser = new SchemaParser();
                                JSONObject jsonSchema;

                                try {
                                    jsonSchema = schemaParser.parseSchema(jsonPath);

                                    JSONArray bqSchemaJsonArray =
                                        jsonSchema.getJSONArray(BIGQUERY_SCHEMA);

                                    for (int i = 0; i < bqSchemaJsonArray.length(); i++) {
                                        JSONObject inputField = bqSchemaJsonArray.getJSONObject(i);
                                        TableFieldSchema field =
                                            new TableFieldSchema()
                                                .setName(inputField.getString(NAME))
                                                .setType(inputField.getString(TYPE));
                                        if (inputField.has(MODE)) {
                                            field.setMode(inputField.getString(MODE));
                                        }

                                        fields.add(field);
                                    }
                                    tableSchema.setFields(fields);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                                return tableSchema;
                            }
                        }))
                    .withTimePartitioning(
                        new TimePartitioning().setField("timestamp")
                            .setType("DAY")
                            .setExpirationMs(null)
                    )
                    .withCreateDisposition(CreateDisposition.CREATE_IF_NEEDED)
                    .withWriteDisposition(WriteDisposition.WRITE_APPEND)
                    .to(options.getEthBqBlkTbl())
                    .withExtendedErrorInfo()
                    .withoutValidation()
                    .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
                    .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                    .withCustomGcsTempLocation(options.getGcsTempLocation()));

        // Etherum Transactions
        PCollection<PubsubMessage> ethTxRaw = p.apply("Read Pubsub - Ethereum Transactions data", 
            PubsubIO.readMessages()
                .fromSubscription(options.getEthdataTxSub()));
        PCollection<String> ethTxJson = ethTxRaw.apply("Extract Transactions Data",
            ParDo.of(new TrimRawTransactionData()));

        // Transactions to Elasticsearch
        ethTxJson.apply(options.getWindowSize() + " window for transaction data",
            Window.<String>into(FixedWindows.of(DurationUtils.parseDuration(options.getWindowSize())))
                .triggering(
                    AfterWatermark.pastEndOfWindow()
                        .withEarlyFirings(
                            AfterProcessingTime
                                .pastFirstElementInPane() 
                                .plusDelayOf(DurationUtils.parseDuration(options.getEarlyFiringPeriod())))
                        .withLateFirings(
                            AfterPane.elementCountAtLeast(
                                options.getLateFiringCount().intValue()))
                )
                .discardingFiredPanes() // e.g. .accumulatingFiredPanes() etc.
                .withAllowedLateness(DurationUtils.parseDuration(options.getAllowedLateness()),
                    ClosingBehavior.FIRE_IF_NON_EMPTY))
            .apply("Append transaction data to Elasticsearch",
                ElasticsearchIO.append()
                    .withMaxBatchSize(options.getEsMaxBatchSize())
                    .withMaxBatchSizeBytes(options.getEsMaxBatchBytes())
                    .withConnectionConf(
                        ElasticsearchIO.ConnectionConf.create(
                            options.getEsHost(),
                            options.getEthTxIdx())
                                .withUsername(options.getEsUser())
                                .withPassword(options.getEsPass())
                                .withNumThread(options.getEsNumThread()))
                                //.withTrustSelfSignedCerts(true)) // false by default
                    .withRetryConf(
                        ElasticsearchIO.RetryConf.create(6, Duration.standardSeconds(60))));

        // Transactions to BigQuery
        ethTxJson.apply("Prepare Transaction BQ TableRow",
            ParDo.of(
                new DoFn<String, TableRow>() {
                    @ProcessElement
                    public void processElement(ProcessContext ctx) {
                        String jsonTx = ctx.element();

                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode json = mapper.readTree(jsonTx);
                            //ObjectNode jsonRoot = (ObjectNode) json;
                            //sonRoot.remove("@timestamp");

                            TableRow tb = TableRowJsonCoder.of().decode(
                                new ByteArrayInputStream(
                                    mapper.writeValueAsString(json)
                                        .getBytes(StandardCharsets.UTF_8)),
                                Context.OUTER
                            );
                            logger.info("Transaction:" + tb.toPrettyString());

                            ctx.output(tb);
                        } catch (java.io.IOException ex) {
                            logger.error("Failed creating BQ Transaction TableRow", ex);
                        }
                    } // End processElement
                } // End DoFn
            ) // End ParDo
        ).apply("Insert BigQuery - Transaction",
            BigQueryIO.writeTableRows()
                .withSchema(
                    NestedValueProvider.of(
                        options.getEthBqTx(),
                        new SerializableFunction<String, TableSchema>() {
                            @Override
                            public TableSchema apply(String jsonPath) {
                                TableSchema tableSchema = new TableSchema();
                                List<TableFieldSchema> fields = new ArrayList<>();
                                SchemaParser schemaParser = new SchemaParser();
                                JSONObject jsonSchema;

                                try {
                                    jsonSchema = schemaParser.parseSchema(jsonPath);

                                    JSONArray bqSchemaJsonArray =
                                        jsonSchema.getJSONArray(BIGQUERY_SCHEMA);

                                    for (int i = 0; i < bqSchemaJsonArray.length(); i++) {
                                        JSONObject inputField = bqSchemaJsonArray.getJSONObject(i);
                                        TableFieldSchema field =
                                            new TableFieldSchema()
                                                .setName(inputField.getString(NAME))
                                                .setType(inputField.getString(TYPE));
                                        if (inputField.has(MODE)) {
                                            field.setMode(inputField.getString(MODE));
                                        }

                                        fields.add(field);
                                    }
                                    tableSchema.setFields(fields);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                                return tableSchema;
                            }
                        }))
                    .withTimePartitioning(
                        new TimePartitioning().setField("block_timestamp")
                            .setType("DAY")
                            .setExpirationMs(null)
                    )
                    .withCreateDisposition(CreateDisposition.CREATE_IF_NEEDED)
                    .withWriteDisposition(WriteDisposition.WRITE_APPEND)
                    .to(options.getEthBqTxTbl())
                    .withExtendedErrorInfo()
                    .withoutValidation()
                    .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
                    .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                    .withCustomGcsTempLocation(options.getGcsTempLocation()));

        /* End Ethereum data */

        /* Polkadot data */
        PCollection<PubsubMessage> polkadataMsg = p.apply("Read Pubsub - Polkadot data", 
            PubsubIO.readMessages()
                .fromSubscription(options.getPolkadatasub()));

        // json data as string
        PCollection<String> polkadata = polkadataMsg.apply("Get json data",
            ParDo.of(new ExtractDataPayload()));

        // identify json, e.g. block or transaction
        PCollectionList<String> allJson = polkadata.apply("Identify Json types", 
            Partition.of(JsonData.values().length, new PartitionFn<String>() {
                public int partitionFor(String data, int numPartitions) {
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode json = mapper.readTree(data);
                        ObjectNode jsonRoot = (ObjectNode) json;
                    
                        if (null != json.get("transactionCount")) {
                            return JsonData.BLK.ordinal();
                        } else {
                            return JsonData.TX.ordinal();
                        }
                    } catch (JsonProcessingException ex) {
                        return JsonData.ERR.ordinal();
                    }
                }}));
        // block data
        PCollection<String> blkJson = allJson.get(JsonData.BLK.ordinal());
        blkJson.apply(options.getWindowSize() + " window for block data",
            Window.<String>into(FixedWindows.of(DurationUtils.parseDuration(options.getWindowSize())))
                .triggering(
                    AfterWatermark.pastEndOfWindow()
                        .withEarlyFirings(
                            AfterProcessingTime
                                .pastFirstElementInPane() 
                                .plusDelayOf(DurationUtils.parseDuration(options.getEarlyFiringPeriod())))
                        .withLateFirings(
                            AfterPane.elementCountAtLeast(
                                options.getLateFiringCount().intValue()))
                )
                .discardingFiredPanes() // e.g. .accumulatingFiredPanes() etc.
                .withAllowedLateness(DurationUtils.parseDuration(options.getAllowedLateness()),
                    ClosingBehavior.FIRE_IF_NON_EMPTY))
            .apply("Append block data to Elasticsearch",
                ElasticsearchIO.append()
                    .withMaxBatchSize(options.getEsMaxBatchSize())
                    .withMaxBatchSizeBytes(options.getEsMaxBatchBytes())
                    .withConnectionConf(
                        ElasticsearchIO.ConnectionConf.create(
                            options.getEsHost(),
                            options.getBlkIdx())
                                .withUsername(options.getEsUser())
                                .withPassword(options.getEsPass())
                                .withNumThread(options.getEsNumThread()))
                                //.withTrustSelfSignedCerts(true)) // false by default
                    .withRetryConf(
                        ElasticsearchIO.RetryConf.create(6, Duration.standardSeconds(60))));

        blkJson.apply("Prepare Block BQ TableRow",
            ParDo.of(
                new DoFn<String, TableRow>() {
                    @ProcessElement
                    public void processElement(ProcessContext ctx) {
                        String jsonBlk = ctx.element();

                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode json = mapper.readTree(jsonBlk);
                            ObjectNode jsonRoot = (ObjectNode) json;
                            jsonRoot.remove("@timestamp");

                            TableRow tb = TableRowJsonCoder.of().decode(
                                new ByteArrayInputStream(
                                    mapper.writeValueAsString(json)
                                        .getBytes(StandardCharsets.UTF_8)),
                                Context.OUTER
                            );
                            logger.debug("Block:" + tb.toPrettyString());

                            ctx.output(tb);
                        } catch (java.io.IOException ex) {
                            logger.error("Failed creating BQ Block TableRow", ex);
                        }
                    } // End processElement
                } // End DoFn
            ) // End ParDo
        ).apply("Insert BigQuery - Block",
            BigQueryIO.writeTableRows()
                .withSchema(
                    NestedValueProvider.of(
                        options.getBqBlk(),
                        new SerializableFunction<String, TableSchema>() {
                            @Override
                            public TableSchema apply(String jsonPath) {
                                TableSchema tableSchema = new TableSchema();
                                List<TableFieldSchema> fields = new ArrayList<>();
                                SchemaParser schemaParser = new SchemaParser();
                                JSONObject jsonSchema;

                                try {
                                    jsonSchema = schemaParser.parseSchema(jsonPath);

                                    JSONArray bqSchemaJsonArray =
                                        jsonSchema.getJSONArray(BIGQUERY_SCHEMA);

                                    for (int i = 0; i < bqSchemaJsonArray.length(); i++) {
                                        JSONObject inputField = bqSchemaJsonArray.getJSONObject(i);
                                        TableFieldSchema field =
                                            new TableFieldSchema()
                                                .setName(inputField.getString(NAME))
                                                .setType(inputField.getString(TYPE));
                                        if (inputField.has(MODE)) {
                                            field.setMode(inputField.getString(MODE));
                                        }

                                        fields.add(field);
                                    }
                                    tableSchema.setFields(fields);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                                return tableSchema;
                            }
                        }))
                    .withTimePartitioning(
                        new TimePartitioning().setField("blocktime")
                            .setType("DAY")
                            .setExpirationMs(null)
                    )
                    .withCreateDisposition(CreateDisposition.CREATE_IF_NEEDED)
                    .withWriteDisposition(WriteDisposition.WRITE_APPEND)
                    .to(options.getBqBlkTbl())
                    .withExtendedErrorInfo()
                    .withoutValidation()
                    .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
                    .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                    .withCustomGcsTempLocation(options.getGcsTempLocation()));

        // transaction data
        PCollection<String> txJson = allJson.get(JsonData.TX.ordinal());
        txJson.apply(options.getWindowSize() + " window for transaction data",
            Window.<String>into(FixedWindows.of(DurationUtils.parseDuration(options.getWindowSize())))
                .triggering(
                    AfterWatermark.pastEndOfWindow()
                        .withEarlyFirings(
                            AfterProcessingTime
                                .pastFirstElementInPane() 
                                .plusDelayOf(DurationUtils.parseDuration(options.getEarlyFiringPeriod())))
                        .withLateFirings(
                            AfterPane.elementCountAtLeast(
                                options.getLateFiringCount().intValue()))
                )
                .discardingFiredPanes() // e.g. .accumulatingFiredPanes() etc.
                .withAllowedLateness(DurationUtils.parseDuration(options.getAllowedLateness()),
                    ClosingBehavior.FIRE_IF_NON_EMPTY))
            .apply("Append transaction data to Elasticsearch",
                ElasticsearchIO.append()
                    .withMaxBatchSize(options.getEsMaxBatchSize())
                    .withMaxBatchSizeBytes(options.getEsMaxBatchBytes())
                    .withConnectionConf(
                        ElasticsearchIO.ConnectionConf.create(
                            options.getEsHost(),
                            options.getTxIdx())
                                .withUsername(options.getEsUser())
                                .withPassword(options.getEsPass())
                                .withNumThread(options.getEsNumThread()))
                                //.withTrustSelfSignedCerts(true)) // false by default
                    .withRetryConf(
                        ElasticsearchIO.RetryConf.create(6, Duration.standardSeconds(60))));

            txJson.apply("Prepare Transaction BQ TableRow",
                ParDo.of(
                    new DoFn<String, TableRow>() {
                        @ProcessElement
                        public void processElement(ProcessContext ctx) {
                            String jsonTx = ctx.element();
    
                            try {
                                ObjectMapper mapper = new ObjectMapper();
                                JsonNode json = mapper.readTree(jsonTx);
                                ObjectNode jsonRoot = (ObjectNode) json;
                                jsonRoot.remove("@timestamp");

                                TableRow tb = TableRowJsonCoder.of().decode(
                                    new ByteArrayInputStream(
                                        mapper.writeValueAsString(json)
                                            .getBytes(StandardCharsets.UTF_8)),
                                    Context.OUTER
                                );
                                logger.debug("Transaction:" + tb.toPrettyString());

                                ctx.output(tb);
                            } catch (java.io.IOException ex) {
                                logger.error("Failed creating BQ Transaction TableRow", ex);
                            }
                        } // End processElement
                    } // End DoFn
                ) // End ParDo
            ).apply("Insert BigQuery - Transaction",
                BigQueryIO.writeTableRows()
                    .withSchema(
                        NestedValueProvider.of(
                            options.getBqTx(),
                            new SerializableFunction<String, TableSchema>() {
                                @Override
                                public TableSchema apply(String jsonPath) {
                                    TableSchema tableSchema = new TableSchema();
                                    List<TableFieldSchema> fields = new ArrayList<>();
                                    SchemaParser schemaParser = new SchemaParser();
                                    JSONObject jsonSchema;
    
                                    try {
                                        jsonSchema = schemaParser.parseSchema(jsonPath);
    
                                        JSONArray bqSchemaJsonArray =
                                            jsonSchema.getJSONArray(BIGQUERY_SCHEMA);
    
                                        for (int i = 0; i < bqSchemaJsonArray.length(); i++) {
                                            JSONObject inputField = bqSchemaJsonArray.getJSONObject(i);
                                            TableFieldSchema field =
                                                new TableFieldSchema()
                                                    .setName(inputField.getString(NAME))
                                                    .setType(inputField.getString(TYPE));
                                            if (inputField.has(MODE)) {
                                                field.setMode(inputField.getString(MODE));
                                            }
    
                                            fields.add(field);
                                        }
                                        tableSchema.setFields(fields);
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                    return tableSchema;
                                }
                            }))
                        .withTimePartitioning(
                            new TimePartitioning().setField("blocktime")
                                .setType("DAY")
                                .setExpirationMs(null)
                        )
                        .withCreateDisposition(CreateDisposition.CREATE_IF_NEEDED)
                        .withWriteDisposition(WriteDisposition.WRITE_APPEND)
                        .to(options.getBqTxTbl())
                        .withExtendedErrorInfo()
                        .withoutValidation()
                        .withMethod(BigQueryIO.Write.Method.STREAMING_INSERTS)
                        .withFailedInsertRetryPolicy(InsertRetryPolicy.retryTransientErrors())
                        .withCustomGcsTempLocation(options.getGcsTempLocation()));

        // TODO: error data
        PCollection<String> errJson = allJson.get(JsonData.ERR.ordinal());
        /* Polkadot data */

        /* Polkadot logs */
        PCollection<PubsubMessage> messages = p.apply("Read Pubsub - Polkadot logs", 
            PubsubIO.readMessages()
                .fromSubscription(options.getSubscription()));

        PCollectionTuple processedData = messages.apply("Polkadot logs ETL",
            ParDo.of(new ExtractPayload())
                .withOutputTags(STR_OUT, TupleTagList.of(STR_FAILURE_OUT)));

        // REVISIT: we may apply differnet window for error data?
        PCollection<String> errData = processedData.get(STR_FAILURE_OUT)
            .apply(options.getWindowSize() + " window for error data",
                Window.<String>into(FixedWindows.of(DurationUtils.parseDuration(options.getWindowSize()))));

        /* Elasticsearch */
        processedData.get(STR_OUT)
            .apply(options.getWindowSize() + " window for healthy data",
                Window.<String>into(FixedWindows.of(DurationUtils.parseDuration(options.getWindowSize())))
                    .triggering(
                        AfterWatermark.pastEndOfWindow()
                            .withEarlyFirings(
                                AfterProcessingTime
                                    .pastFirstElementInPane() 
                                    .plusDelayOf(DurationUtils.parseDuration(options.getEarlyFiringPeriod())))
                            .withLateFirings(
                                AfterPane.elementCountAtLeast(
                                    options.getLateFiringCount().intValue()))
                    )
                    .discardingFiredPanes() // e.g. .accumulatingFiredPanes() etc.
                    .withAllowedLateness(DurationUtils.parseDuration(options.getAllowedLateness()),
                        ClosingBehavior.FIRE_IF_NON_EMPTY))
            .apply("Append data to Elasticsearch",
                ElasticsearchIO.append()
                    .withMaxBatchSize(options.getEsMaxBatchSize())
                    .withMaxBatchSizeBytes(options.getEsMaxBatchBytes())
                    .withConnectionConf(
                        ElasticsearchIO.ConnectionConf.create(
                            options.getEsHost(),
                            options.getEsIndex())
                                .withUsername(options.getEsUser())
                                .withPassword(options.getEsPass())
                                .withNumThread(options.getEsNumThread()))
                                //.withTrustSelfSignedCerts(true)) // false by default
                    .withRetryConf(
                        ElasticsearchIO.RetryConf.create(6, Duration.standardSeconds(60))));
        /* Polkadot logs */

        /* END - Elasticsearch */

/*
        healthData.apply("Write windowed healthy CSV files", 
            TextIO.write()
                .withNumShards(options.getNumShards())
                .withWindowedWrites()
                .to(
                    new WindowedFilenamePolicy(
                        options.getOutputDir(),
                        options.getFilenamePrefix(),
                        options.getOutputShardTemplate(),
                        options.getLogFilenameSuffix()
                    ))
                .withTempDirectory(
                    FileBasedSink.convertToFileResourceIfPossible(options.getTempLocation())));
*/

        errData.apply("Write windowed error data", 
            TextIO.write()
                .withNumShards(options.getNumShards())
                .withWindowedWrites()
                .to(
                    new WindowedFilenamePolicy(
                        options.getErrOutputDir(),
                        options.getFilenamePrefix(),
                        options.getOutputShardTemplate(),
                        options.getLogFilenameSuffix()
                    ))
                .withTempDirectory(
                    FileBasedSink.convertToFileResourceIfPossible(options.getTempLocation())));

        p.run();
        //p.run().waitUntilFinish();
    }


    public static void main(String... args) {
        PipelineOptionsFactory.register(BindiegoStreamingOptions.class);

        BindiegoStreamingOptions options = PipelineOptionsFactory
            .fromArgs(args)
            .withValidation()
            .as(BindiegoStreamingOptions.class);
        options.setStreaming(true);
        // options.setRunner(DataflowRunner.class);
        // options.setNumWorkers(2);
        // options.setUsePublicIps(true);
        
        try {
            run(options);
        } catch (Exception ex) {
            //System.err.println(ex);
            //ex.printStackTrace();
            logger.error(ex.getMessage(), ex);
        }
    }

    // Instantiate Logger
    private static final Logger logger = LoggerFactory.getLogger(BindiegoStreaming.class);

    /* tag for main output when extracting pubsub message payload*/
    private static final TupleTag<String> STR_OUT = 
        new TupleTag<String>() {};
    /* tag for failure output from the UDF */
    private static final TupleTag<String> STR_FAILURE_OUT = 
        new TupleTag<String>() {};

    private static final String BIGQUERY_SCHEMA = "BigQuery Schema";
    private static final String NAME = "name";
    private static final String TYPE = "type";
    private static final String MODE = "mode";

    enum JsonData {
        BLK, // Block json data
        TX, // Transaction json data
        ERR // Failed elements
    }
}
