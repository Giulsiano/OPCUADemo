package serverLogic;

import org.eclipse.milo.examples.client.ClientExample;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaMonitoredItem;
import org.eclipse.milo.opcua.sdk.client.api.subscriptions.UaSubscription;
import org.eclipse.milo.opcua.sdk.client.model.nodes.objects.ServerNode;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.AttributeId;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MonitoringMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class Server2ServerClient implements ClientExample {

    // TODO: This is the client logic code for the server to retrieve infos about the current running server
    private Logger logger = LoggerFactory.getLogger(Server2ServerClient.class);
    private String endpointURL = "";
    private String clientId;
    private final AtomicLong clientHandles = new AtomicLong(1L);
    private CompletableFuture<OpcUaClient> futureDisconnect;

    public Server2ServerClient (String clientId){
        this.clientId = clientId;
    }

    @Override
    public String getEndpointUrl (){
        return endpointURL;
    }

    public void setEndpointURL (String url){
        endpointURL = url;
    }

    private OpcUaClient createClient() throws Exception {
        Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security");
        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new Exception("unable to create security dir: " + securityTempDir);
        }
        LoggerFactory.getLogger(getClass())
                .info("security temp dir: {}", securityTempDir.toAbsolutePath());

        KeyStoreLoader loader = new KeyStoreLoader().loadClientKeyStore(securityTempDir, "server2server-client.pfx");

        SecurityPolicy securityPolicy = getSecurityPolicy();

        List<EndpointDescription> endpoints;

        try {
            endpoints = DiscoveryClient.getEndpoints(getEndpointUrl()).get();
        }
        catch (Throwable ex) {
            // try the explicit discovery endpoint as well
            String discoveryUrl = getEndpointUrl();

            if (!discoveryUrl.endsWith("/")) {
                discoveryUrl += "/";
            }
            discoveryUrl += "discovery";

            logger.info("Trying explicit discovery URL: {}", discoveryUrl);
            endpoints = DiscoveryClient.getEndpoints(discoveryUrl).get();
        }

        EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getUri()))
                .filter(endpointFilter())
                .findFirst()
                .orElseThrow(() -> new Exception("no desired endpoints returned"));

        logger.info("Using endpoint: {} [{}/{}]",
                endpoint.getEndpointUrl(), securityPolicy, endpoint.getSecurityMode());

        OpcUaClientConfig config = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("eclipse milo opc-ua client demo"))
                .setApplicationUri("urn:eclipse:milo:clientDemo")
                .setCertificate(loader.getClientCertificate())
                .setKeyPair(loader.getClientKeyPair())
                .setEndpoint(endpoint)
                .setIdentityProvider(getIdentityProvider())
                .setRequestTimeout(uint(5000))
                .build();

        return OpcUaClient.create(config);
    }

    @Override
    public void run (OpcUaClient client, CompletableFuture<OpcUaClient> future) throws Exception{
        // Connect to the server and ask for its state to return
        logger.info("Connecting {} to {}", clientId, getEndpointUrl());
        client.connect().get();
        ServerState oldState = ((ServerNode) client.getAddressSpace().getObjectNode(Identifiers.Server).get()).getServerStatusNode().get().getState().get();

        // create a subscription and a monitored item
        UaSubscription subscription = client.getSubscriptionManager()
            .createSubscription(1000.0).get();

        ReadValueId readValueId = new ReadValueId(
            Identifiers.Server,
            AttributeId.EventNotifier.uid(),
            null,
            QualifiedName.NULL_VALUE
        );

        // client handle must be unique per item
        UInteger clientHandle = uint(clientHandles.getAndIncrement());

        EventFilter eventFilter = new EventFilter(new SimpleAttributeOperand[]{
                                        new SimpleAttributeOperand(
                                                Identifiers.SystemStatusChangeEventType,
                                                new QualifiedName[]{new QualifiedName(0, "SystemState")},
                                                AttributeId.Value.uid(),
                                                null
                                        )
                                },
                                new ContentFilter(null)
        );

        MonitoringParameters parameters = new MonitoringParameters(
            clientHandle,
            0.0,
            ExtensionObject.encode(client.getSerializationContext(), eventFilter),
            uint(10),
            true
        );

        MonitoredItemCreateRequest request = new MonitoredItemCreateRequest(
            readValueId,
            MonitoringMode.Reporting,
            parameters
        );

        List<UaMonitoredItem> items = subscription
            .createMonitoredItems(TimestampsToReturn.Both, Collections.singletonList(request)).get();

        items.get(0).setEventConsumer((item, vs) -> {
            if (vs.length > 1) {
                logger.error("{} received too much variants: {}", clientId, vs.length);
                future.complete(client);
            }
            else {
                ServerState newState = (ServerState.from((Integer) vs[0].getValue()));
                if (newState == ServerState.Suspended || newState == ServerState.Shutdown){
                    logger.warn("{}: Server changes its status from {} to {}.", clientId, oldState, newState);
                    logger.info("Shutting down {}", clientId);
                    future.complete(client);
                }
            }
        });
    }

    public CompletableFuture<Server2ServerClient> startup(){
        try {
            OpcUaClient client = createClient();
            futureDisconnect = new CompletableFuture<>();
            futureDisconnect.whenCompleteAsync((c, ex) -> {
                if (ex != null) logger.error("{}: Error running example: {}\n{}", clientId, ex, ex.getMessage());

                try {
                    logger.info("Disconnecting {}", clientId);
                    c.disconnect().get();
                }
                catch (InterruptedException | ExecutionException e) {
                    logger.error("Error disconnecting {}: {}.\n{}", clientId, e.getMessage(), e);
                }
            });
            try {
                run(client, futureDisconnect);
                //future.get(1000, TimeUnit.SECONDS); //not required since there is a future.complete on run()
            } catch (Throwable t) {
                logger.error("Error running client example: {}", t.getMessage(), t);
                futureDisconnect.completeExceptionally(t);
            }
        }
        catch (Throwable t) {
            logger.error("Error creating {}: {}\n{}", clientId, t, t.getMessage());
            futureDisconnect.completeExceptionally(t);
        }
        return futureDisconnect.thenApply((c) -> Server2ServerClient.this);
    }

    public CompletableFuture<Server2ServerClient> shutdown (){
        return futureDisconnect.thenApply((c) -> Server2ServerClient.this);
    }
}
