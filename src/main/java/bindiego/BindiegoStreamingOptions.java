package bindiego;

import org.apache.beam.sdk.options.Default;
import org.apache.beam.sdk.options.Description;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.beam.sdk.options.PipelineOptionsFactory;
import org.apache.beam.sdk.options.StreamingOptions;
import org.apache.beam.sdk.options.Validation.Required;
import org.apache.beam.sdk.options.ValueProvider;
import org.apache.beam.sdk.options.ValueProvider.NestedValueProvider;
import org.apache.beam.runners.dataflow.options.DataflowPipelineOptions;
import org.apache.beam.runners.dataflow.options.DataflowWorkerLoggingOptions;

public interface BindiegoStreamingOptions 
        extends PipelineOptions, StreamingOptions, 
                DataflowPipelineOptions, DataflowWorkerLoggingOptions {
    @Description("Topic of pubsub")
    @Default.String("projects/google.com:bin-wus-learning-center/topics/dingoactions")
    ValueProvider<String> getTopic();
    void setTopic(ValueProvider<String> value);

    @Description("Subcriptions of pubsub")
    @Required
    ValueProvider<String> getSubscription();
    void setSubscription(ValueProvider<String> value);

    @Description("The directory to output files to. Must end with a slash.")
    @Required
    ValueProvider<String> getOutputDir();
    void setOutputDir(ValueProvider<String> value);

    @Description("The directory to output error files to. Must end with a slash.")
    @Required
    ValueProvider<String> getErrOutputDir();
    void setErrOutputDir(ValueProvider<String> value);

    @Description("File name prefix.")
    @Default.String("bindiego")
    ValueProvider<String> getFilenamePrefix();
    void setFilenamePrefix(ValueProvider<String> value);

    @Description("Log File name suffix.")
    @Default.String(".log")
    ValueProvider<String> getLogFilenameSuffix();
    void setLogFilenameSuffix(ValueProvider<String> value);

    @Default.String("W-P-SS-of-NN")
    ValueProvider<String> getOutputShardTemplate();
    void setOutputShardTemplate(ValueProvider<String> value);

    @Description("The maximum number of output shards produced when writing.")
    @Default.Integer(1)
    Integer getNumShards();
    void setNumShards(Integer value);

    @Description("Output window size.")
    @Default.String("5m")
    String getWindowSize();
    void setWindowSize(String value);

    @Description("Allowed late data for a window")
    @Default.String("5m")
    String getAllowedLateness();
    void setAllowedLateness(String value);

    @Description("Early firing period")
    @Default.String("1m")
    String getEarlyFiringPeriod();
    void setEarlyFiringPeriod(String value);

    @Description("Late firing count")
    @Default.String("1")
    Integer getLateFiringCount();
    void setLateFiringCount(Integer value);

    @Description("CSV file delimiter.")
    @Default.String(",")
    String getCsvDelimiter();
    void setCsvDelimiter(String value);

    @Description("GCS temp location for BigQuery")
    @Required
    ValueProvider<String> getGcsTempLocation();
    void setGcsTempLocation(ValueProvider<String> value);

    @Description("PubsubMessage ID attribute.")
    @Default.String("id")
    String getMessageIdAttr();
    void setMessageIdAttr(String value);

    @Description("PubsubMessage timestamp attribute.")
    @Default.String("timestamp")
    String getMessageTsAttr();
    void setMessageTsAttr(String value);

    @Description("Elasticsearch host, usually a LB for coordinating nodes. e.g. https://es.ingest.abc.com")
    @Required
    String getEsHost();
    void setEsHost(String value);

    @Description("Elasticsearch user")
    @Required
    String getEsUser();
    void setEsUser(String value);

    @Description("Elasticsearch password")
    @Required
    String getEsPass();
    void setEsPass(String value);

    @Description("Elasticsearch index, usually an alias for index lifecycle management")
    @Required
    String getEsIndex();
    void setEsIndex(String value);

    @Description("Elasticsearch Rest client max batch size")
    @Default.Long(1000L)
    Long getEsMaxBatchSize();
    void setEsMaxBatchSize(Long value);

    @Description("Elasticsearch Rest client max batch bytes")
    @Default.Long(5L * 1024L * 1024L)
    Long getEsMaxBatchBytes();
    void setEsMaxBatchBytes(Long value);

    @Description("Elasticsearch Rest client threads")
    @Default.Integer(1)
    Integer getEsNumThread();
    void setEsNumThread(Integer value);

    @Description("Polkadot data (from agent RPC query) subcription of pubsub")
    @Required
    @Default.String("polkadot-data-sub")
    ValueProvider<String> getPolkadatasub();
    void setPolkadatasub(ValueProvider<String> value);

    @Description("Elasticsearch index used for polkadot blocks data")
    @Required
    String getBlkIdx();
    void setBlkIdx(String value);

    @Description("Elasticsearch index used for polkadot transactions data")
    @Required
    String getTxIdx();
    void setTxIdx(String value);

    @Description("BigQuery Schema - polkadot Block")
    @Required
    ValueProvider<String> getBqBlk();
    void setBqBlk(ValueProvider<String> value);

    @Description("BigQuery Schema - polkadot Transaction")
    @Required
    ValueProvider<String> getBqTx();
    void setBqTx(ValueProvider<String> value);

    @Description("BigQuery table - polkadot Block")
    @Required
    ValueProvider<String> getBqBlkTbl();
    void setBqBlkTbl(ValueProvider<String> value);

    @Description("BigQuery table - polkadot Transaction")
    @Required
    ValueProvider<String> getBqTxTbl();
    void setBqTxTbl(ValueProvider<String> value);

    @Description("Ethereum blocks data subcription of pubsub")
    @Required
    ValueProvider<String> getEthdataBlkSub();
    void setEthdataBlkSub(ValueProvider<String> value);

    @Description("Ethereum transactions data subcription of pubsub")
    @Required
    ValueProvider<String> getEthdataTxSub();
    void setEthdataTxSub(ValueProvider<String> value);

    @Description("Elasticsearch index used for ethereum blocks data")
    @Required
    String getEthBlkIdx();
    void setEthBlkIdx(String value);

    @Description("Elasticsearch index used for ethereum transactions data")
    @Required
    String getEthTxIdx();
    void setEthTxIdx(String value);

    @Description("BigQuery Schema - ethereum Block")
    @Required
    ValueProvider<String> getEthBqBlk();
    void setEthBqBlk(ValueProvider<String> value);

    @Description("BigQuery Schema - ethereum Transaction")
    @Required
    ValueProvider<String> getEthBqTx();
    void setEthBqTx(ValueProvider<String> value);

    @Description("BigQuery table - ethereum Block")
    @Required
    ValueProvider<String> getEthBqBlkTbl();
    void setEthBqBlkTbl(ValueProvider<String> value);

    @Description("BigQuery table - ethereum Transaction")
    @Required
    ValueProvider<String> getEthBqTxTbl();
    void setEthBqTxTbl(ValueProvider<String> value);
}
