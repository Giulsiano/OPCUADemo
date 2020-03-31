package serverLogic;

import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfigBuilder;
import org.eclipse.milo.opcua.sdk.server.identity.CompositeValidator;
import org.eclipse.milo.opcua.sdk.server.identity.UsernameIdentityValidator;
import org.eclipse.milo.opcua.sdk.server.identity.X509IdentityValidator;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaRuntimeException;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateManager;
import org.eclipse.milo.opcua.stack.core.security.DefaultCertificateValidator;
import org.eclipse.milo.opcua.stack.core.security.DefaultTrustListManager;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.transport.TransportProfile;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.enumerated.MessageSecurityMode;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.structured.BuildInfo;
import org.eclipse.milo.opcua.stack.core.types.structured.RedundantServerDataType;
import org.eclipse.milo.opcua.stack.core.util.CertificateUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedHttpsCertificateBuilder;
import org.eclipse.milo.opcua.stack.server.EndpointConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static com.google.common.collect.Lists.newArrayList;
import static org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig.*;
import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;

public class RedundantServerSet {
    private List<RedundantServer> serverSet;
    private X509Certificate certificate;
    private RedundantServerDataType[] redundantServers;
    private Properties properties;
    private Logger logger = LoggerFactory.getLogger(RedundantServerSet.class.getName());
    private Path securityTempDir =  FileSystems.getDefault().getPath(System.getProperty("java.io.tmpdir"), "security");

    public static final int TCP_SERVER_PORT = 4242;
    public static final int HTTPS_SERVER_PORT = 42424;
    public static final int TCP_REDUNDANCY_PORT = TCP_SERVER_PORT + 1;
    public static final int HTTPS_REDUNDANCY_PORT = HTTPS_SERVER_PORT + 1;
    public static final String REDUNDANCY_URL = "opc.tcp://localhost:" + TCP_REDUNDANCY_PORT + "/milo";

    public RedundantServerSet (int nServer) throws Exception{
        logger.info("Creating Server Set");
        KeyStoreLoader loader = buildKeyStoreLoader("redundant-server.pfx");
        certificate = buildCertificate(loader);
        redundantServers = new RedundantServerDataType[nServer];
        for (int i = 0; i < nServer; i++) {
            redundantServers[i] = new RedundantServerDataType("Server" + i, ubyte(1), ServerState.Suspended);
        }
        serverSet = new LinkedList<>();
        OpcUaServerConfig config = createDefaultConfigBuilder(loader)
                                   .setEndpoints(createEndpointConfigurations(certificate))
                                   .build();

        for (int i = 0; i < nServer; i++) {
            serverSet.add(new RedundantServer(config, "Server" + i)
                                            .setClientEndpointURL(REDUNDANCY_URL)
                                            .setRedundantServerArray(redundantServers)
                                            .setServerState(ServerState.Suspended)
                                            .setRedundancySupport(RedundancySupport.Transparent)
            );
        }
        properties = new Properties();
    }

    private KeyStoreLoader buildKeyStoreLoader (String pfxName) throws Exception{
        if (Files.notExists(Files.createDirectories(securityTempDir))){
            throw new IOException("unable to create security temp dir: " + securityTempDir);
        }
        logger.info("security temp dir: {}", securityTempDir);

        return new KeyStoreLoader().loadServerKeyStore(securityTempDir, pfxName);
    }

    private X509Certificate buildCertificate (KeyStoreLoader loader){
        logger.info("Building certificate");
        return new DefaultCertificateManager(loader.getServerKeyPair(), loader.getServerCertificateChain())
                               .getCertificates()
                               .stream()
                               .findFirst()
                               .orElseThrow(() -> new UaRuntimeException(StatusCodes.Bad_ConfigurationError,
                                                                         "no certificate found"));
    }

    public RedundantServer getCurrentServer (){
        return serverSet.get(0);
    }

    public RedundantServer getCurrentClient () { return (serverSet.size() > 1) ? serverSet.get(1) : null;}

    private OpcUaServerConfigBuilder createDefaultConfigBuilder (KeyStoreLoader loader) throws Exception{
        logger.info("Creating Server Configuration");
        File pkiDir = securityTempDir.resolve("pki").toFile();
        DefaultTrustListManager trustListManager = new DefaultTrustListManager(pkiDir);
        logger.info("pki dir: {}", pkiDir.getAbsolutePath());

        DefaultCertificateValidator certificateValidator = new DefaultCertificateValidator(trustListManager);

        KeyPair httpsKeyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);

        SelfSignedHttpsCertificateBuilder httpsCertificateBuilder = new SelfSignedHttpsCertificateBuilder(httpsKeyPair);
        httpsCertificateBuilder.setCommonName(HostnameUtil.getHostname());
        HostnameUtil.getHostnames("0.0.0.0").forEach(httpsCertificateBuilder::addDnsName);
        X509Certificate httpsCertificate = httpsCertificateBuilder.build();

        UsernameIdentityValidator identityValidator = new UsernameIdentityValidator(
                true,
                authChallenge -> {
                    String username = authChallenge.getUsername();
                    String password = authChallenge.getPassword();

                    boolean userOk = "user".equals(username) && "password1".equals(password);
                    boolean adminOk = "admin".equals(username) && "password2".equals(password);

                    return userOk || adminOk;
                }
        );

        X509IdentityValidator x509IdentityValidator = new X509IdentityValidator(c -> true);

        // The configured application URI must match the one in the certificate(s)
        String applicationUri = CertificateUtil.getSanUri(certificate)
                                               .orElseThrow(() -> new UaRuntimeException(
                                                    StatusCodes.Bad_ConfigurationError,
                                                    "certificate is missing the application URI"));

        return OpcUaServerConfig.builder()
                .setApplicationUri(applicationUri)
                .setApplicationName(LocalizedText.english("Redundant Server"))
                .setBuildInfo(
                        new BuildInfo(
                                "urn:eclipse:milo:redundant-server-set",
                                "eclipse",
                                "redundant server set",
                                OpcUaServer.SDK_VERSION,
                                "", DateTime.now()))
                .setCertificateManager(new DefaultCertificateManager(
                        loader.getServerKeyPair(),
                        loader.getServerCertificateChain()))
                .setTrustListManager(trustListManager)
                .setCertificateValidator(certificateValidator)
                .setHttpsKeyPair(httpsKeyPair)
                .setHttpsCertificate(httpsCertificate)
                .setIdentityValidator(new CompositeValidator(identityValidator, x509IdentityValidator))
                .setProductUri("urn:eclipse:milo:redundant-server-set");
    }

    private Set<EndpointConfiguration> createEndpointConfigurations(X509Certificate certificate) {
        Set<EndpointConfiguration> endpointConfigurations = new LinkedHashSet<>();

        List<String> bindAddresses = newArrayList();
        bindAddresses.add("0.0.0.0");

        Set<String> hostnames = new LinkedHashSet<>();
        hostnames.add(HostnameUtil.getHostname());
        hostnames.addAll(HostnameUtil.getHostnames("0.0.0.0"));
        EndpointConfiguration.Builder builder = EndpointConfiguration.newBuilder();

        for (String bindAddress : bindAddresses) {
            for (String hostname : hostnames) {
                builder.setBindAddress(bindAddress)
                       .setHostname(hostname)
                       .setPath("/milo")
                       .setCertificate(certificate)
                       .addTokenPolicies(
                                USER_TOKEN_POLICY_ANONYMOUS,
                                USER_TOKEN_POLICY_USERNAME,
                                USER_TOKEN_POLICY_X509);

                EndpointConfiguration.Builder noSecurityBuilder = builder.copy()
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(buildTcpEndpoint(noSecurityBuilder, TCP_SERVER_PORT));
                endpointConfigurations.add(buildHttpsEndpoint(noSecurityBuilder, HTTPS_SERVER_PORT));

                // TCP Basic256Sha256 / SignAndEncrypt
                endpointConfigurations.add(buildTcpEndpoint(
                        builder.copy()
                                .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                                .setSecurityMode(MessageSecurityMode.SignAndEncrypt),
                        TCP_SERVER_PORT)
                );

                // HTTPS Basic256Sha256 / Sign (SignAndEncrypt not allowed for HTTPS)
                endpointConfigurations.add(buildHttpsEndpoint(
                                builder.copy()
                                        .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                                        .setSecurityMode(MessageSecurityMode.Sign),
                                HTTPS_SERVER_PORT)
                );

                EndpointConfiguration.Builder discoveryBuilder = builder.copy()
                        .setPath("/milo/discovery")
                        .setSecurityPolicy(SecurityPolicy.None)
                        .setSecurityMode(MessageSecurityMode.None);

                endpointConfigurations.add(buildTcpEndpoint(discoveryBuilder, TCP_SERVER_PORT));
                endpointConfigurations.add(buildHttpsEndpoint(discoveryBuilder, HTTPS_SERVER_PORT));

                EndpointConfiguration.Builder redundantSetBuilder = builder.copy()
                        .setPath("/milo/redundant")
                        .setSecurityPolicy(SecurityPolicy.Basic256Sha256)
                        .setSecurityMode(MessageSecurityMode.Sign);

                endpointConfigurations.add(buildTcpEndpoint(redundantSetBuilder, TCP_REDUNDANCY_PORT));
                endpointConfigurations.add(buildHttpsEndpoint(redundantSetBuilder, HTTPS_REDUNDANCY_PORT));
            }
        }
        return endpointConfigurations;
    }

    private static EndpointConfiguration buildTcpEndpoint(EndpointConfiguration.Builder base, int port) {
        return base.setTransportProfile(TransportProfile.TCP_UASC_UABINARY)
                   .setBindPort(port)
                   .build();
    }

    private static EndpointConfiguration buildHttpsEndpoint(EndpointConfiguration.Builder base, int port) {
        return base.setTransportProfile(TransportProfile.HTTPS_UABINARY)
                   .setBindPort(port)
                   .build();
    }

    private RedundantServer getNextAvailableServer (){
        logger.info("Finding next available server of the set");
        return serverSet.stream()
                .filter((rs) -> rs.getServerState() != ServerState.Failed && rs.getServerState() != ServerState.Running)
                .findFirst()
                .orElse(null);
    }

    private RedundantServer rebuild (RedundantServer server){
        return new RedundantServer(server.getServerConfig(), server.getServerId())
                                .setRedundantServerArray(redundantServers)
                                .setClientEndpointURL(REDUNDANCY_URL);
    }

    public void run () {
        logger.info("Starting transparent redundant server set");
        RedundantServer server = serverSet.get(0).setAsCurrentServer();
        try {
            if (serverSet.size() > 1){
                RedundantServer client = serverSet.get(1).setCurrentRedundantServerId(server.getServerId());
                while (true){
                    try {
                        server.startup().get();
                    }
                    catch (IllegalStateException e){
                        // Milo doesn't allow a stopped server to become running again apparently
                        serverSet.remove(server);
                        server = rebuild(server).setAsCurrentServer().startup().get();
                        serverSet.add(0, server);
                    }
                    // Client startup is blocking while client is running
                    client.startup().get();

                    // Wait the server to actually shutdown
                    Thread.sleep(server.getSecondsUntilShutdown().intValue() * 1000);

                    // At this point another server has to be started since the latter one has failed
                    logger.info("Put {} to the tail of the set", server.getServerId());
                    serverSet.remove(server);
                    serverSet.add(server);
                    server = getNextAvailableServer();
                    if (server == null) break;
                    server.setAsCurrentServer();
                    client = serverSet.get(1).setCurrentRedundantServerId(server.getServerId());
                }
            }
            else server.startup().get();
        }
        catch (InterruptedException | ExecutionException e) {
            logger.error("Error during execution: {}\n{}", e, e.getMessage());
            e.printStackTrace();
        }
        logger.info("Cleaning OPCUA Stack Shared Resources");
        Stack.releaseSharedResources();

        logger.info("Exiting Redundant server set");
        shutdown();
    }

    public void shutdown (){
        serverSet.forEach(server -> {
            try {
                server.shutdown().get();
            }
            catch (Exception ignored) {}
        });
    }

    public Properties getProperties (){
        return properties;
    }
}
