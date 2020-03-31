package clientLogic;

import org.eclipse.milo.examples.client.ClientExample;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.AddressSpace;
import org.eclipse.milo.opcua.sdk.client.api.nodes.VariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.UaServiceFaultException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import serverLogic.RedundantServerSet;

import java.util.concurrent.CompletableFuture;

public class ClientDemo implements ClientExample {

    public static String ANALOGITEM_NAME = "HelloWorld/DataAccess/AnalogValue";
    private String currentServerId = "None";
    private String value = "None";

    private static Logger logger = LoggerFactory.getLogger(ClientDemo.class);
    private Boolean shouldStop = false;

    public String getCurrentServerId (){
        return currentServerId;
    }

    public String getValue (){
        return value;
    }

    public void interrupt (){
        synchronized (shouldStop){
            shouldStop = true;
        }
    }

    private static final long RECONNECTION_DELAY_SECONDS = 1;

    @Override
    public void run (OpcUaClient client, CompletableFuture<OpcUaClient> future) {
        // synchronous connect
        // TODO reconnect if Bad_SessionIdInvalid. OPCUA transparent redundancy give this opportunity
        while (!shouldStop) {
            try {
                client.connect().get();
                AddressSpace addressSpace = client.getAddressSpace();

                // Get a typed reference to the Server object: ServerNode
                VariableNode serverIdNode = addressSpace.createVariableNode(Identifiers.Server_ServerRedundancy_CurrentServerId);
                VariableNode node = client.getAddressSpace().createVariableNode(new NodeId(2, ANALOGITEM_NAME));

                logger.info("Current Server = {}", serverIdNode.readValue().get().getValue().getValue());
                // Read properties of the Server object...

                while (true) {
                    currentServerId = serverIdNode.readValue().get().getValue().getValue().toString();
                    value = node.readValue().get().getValue().getValue().toString();

                    logger.info("Value = {}", value);
                    try {
                        // Simulate reading of the value every n seconds
                        Thread.sleep(1234);
                    }
                    catch (InterruptedException e) {
                        logger.info("Client thread has been interrupted");
                        break;
                    }
                }
            }
            catch (Exception e){
                if (!(e.getCause() instanceof UaServiceFaultException)){
                    logger.error("Exception caught: {}\n\tMessage: {}\n\tCause: {}", e.getClass(), e.getLocalizedMessage(), e.getCause().getMessage());
                    e.printStackTrace();
                    break;
                }

            }
        }
        future.complete(client);
    }


    @Override
    public String getEndpointUrl (){
        return RedundantServerSet.REDUNDANCY_URL;
    }
}