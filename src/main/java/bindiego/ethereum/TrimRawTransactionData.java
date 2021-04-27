package bindiego.ethereum;

import java.nio.charset.StandardCharsets;

// Import SLF4J packages.
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.DoFn.ProcessContext;
import org.apache.beam.sdk.transforms.DoFn.MultiOutputReceiver;
import org.apache.beam.sdk.transforms.DoFn.OutputReceiver;

import org.apache.beam.sdk.io.gcp.pubsub.PubsubMessage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.core.JsonProcessingException;

public class TrimRawTransactionData extends DoFn<PubsubMessage, String> {
    public TrimRawTransactionData() {}

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

            logger.debug("Extracted ethereum transaction raw message: " + payload);

            JsonNode json = mapper.readTree(payload);
            ObjectNode jsonRoot = (ObjectNode) json;

            jsonRoot.remove("item_id");
            jsonRoot.remove("item_timestamp");

            r.output(mapper.writeValueAsString(json));
        } catch (Exception ex) {
            if (null == payload)
                payload = "Failed to extract pubsub payload";

            logger.error("Failed extract pubsub message", ex);
        }
    }

    private ObjectMapper mapper;

    // Instantiate Logger
    private static final Logger logger = LoggerFactory.getLogger(TrimRawTransactionData.class);
}
