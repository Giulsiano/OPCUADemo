package clientLogic;

import org.eclipse.milo.examples.client.ClientExample;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.config.OpcUaClientConfig;
import org.eclipse.milo.opcua.stack.client.DiscoveryClient;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

public class ClientDemoRunner implements Runnable{
    private static final Logger logger = LoggerFactory.getLogger(ClientDemoRunner.class.getName());

    private ClientExample clientExample;
    private CompletableFuture<OpcUaClient> futureDisconnect = new CompletableFuture<>();

    public ClientDemoRunner (ClientExample clientExample) {
        this.clientExample = clientExample;
        this.futureDisconnect.whenCompleteAsync((c, ex) -> {
            if (ex != null) {
                logger.error("Error running example: {}", ex.getMessage(), ex);
            }
            try {
                c.disconnect().get();
                Stack.releaseSharedResources();
            } catch (InterruptedException | ExecutionException e) {
                logger.error("Error disconnecting: {}.\n{}", e.getMessage(), e);
            }
            System.exit(0);
        });
    }

    public ClientExample getClientExample (){
        return clientExample;
    }

    private OpcUaClient createClient() throws Exception {
        Path securityTempDir = Paths.get(System.getProperty("java.io.tmpdir"), "security");
        Files.createDirectories(securityTempDir);
        if (!Files.exists(securityTempDir)) {
            throw new IOException("unable to create security dir: " + securityTempDir);
        }
        LoggerFactory.getLogger(getClass())
                .info("security temp dir: {}", securityTempDir.toAbsolutePath());

        KeyStoreLoader loader = new KeyStoreLoader().load(securityTempDir);

        SecurityPolicy securityPolicy = clientExample.getSecurityPolicy();

        List<EndpointDescription> endpoints;

        try {
            endpoints = DiscoveryClient.getEndpoints(clientExample.getEndpointUrl()).get();
        }
        catch (Throwable ex) {
            // try the explicit discovery endpoint as well
            String discoveryUrl = clientExample.getEndpointUrl();

            if (!discoveryUrl.endsWith("/")) {
                discoveryUrl += "/";
            }
            discoveryUrl += "discovery";

            logger.info("Trying explicit discovery URL: {}", discoveryUrl);
            endpoints = DiscoveryClient.getEndpoints(discoveryUrl).get();
        }

        EndpointDescription endpoint = endpoints.stream()
                .filter(e -> e.getSecurityPolicyUri().equals(securityPolicy.getUri()))
                .filter(clientExample.endpointFilter())
                .findFirst()
                .orElseThrow(() -> new UaRuntimeException(StatusCode.BAD.getValue(), "no desired endpoints returned"));

        logger.info("Using endpoint: {} [{}/{}]",
                endpoint.getEndpointUrl(), securityPolicy, endpoint.getSecurityMode());

        OpcUaClientConfig config = OpcUaClientConfig.builder()
                .setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
                .setApplicationUri("urn:eclipse:milo:examples:client")
                .setCertificate(loader.getClientCertificate())
                .setKeyPair(loader.getClientKeyPair())
                .setEndpoint(endpoint)
                .setIdentityProvider(clientExample.getIdentityProvider())
                .setRequestTimeout(uint(5000))
                .build();

        return OpcUaClient.create(config);
    }

    @Override
    public void run (){
        try {
            OpcUaClient client = createClient();
            try {
                clientExample.run(client, futureDisconnect);
            } catch (Throwable t) {
                logger.error("Error running client example: {}", t.getMessage(), t);
                futureDisconnect.completeExceptionally(t);
            }
        }
        catch (Throwable t) {
            logger.error("Error creating client: {}", t.getMessage(), t);
            futureDisconnect.completeExceptionally(t);
        }
    }

    public void stop (){
        try {
            futureDisconnect.get();
        }
        catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
