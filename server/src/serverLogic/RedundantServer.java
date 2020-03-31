package serverLogic;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.eclipse.milo.opcua.sdk.core.QualifiedProperty;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.config.OpcUaServerConfig;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.ServerNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.SystemStatusChangeEventNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.objects.TransparentRedundancyNode;
import org.eclipse.milo.opcua.sdk.server.model.nodes.variables.ServerStatusNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UByte;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.enumerated.RedundancySupport;
import org.eclipse.milo.opcua.stack.core.types.enumerated.ServerState;
import org.eclipse.milo.opcua.stack.core.types.structured.RedundantServerDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Security;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.ubyte;

public class RedundantServer{

    static {
        // Required for SecurityPolicy.Aes256_Sha256_RsaPss
        Security.addProvider(new BouncyCastleProvider());
    }

    public final UInteger SHUTDOWN_DELAY_SECONDS = UInteger.valueOf(1);
    public final UInteger SHUTDOWN_DELAY_MILLIS = UInteger.valueOf(SHUTDOWN_DELAY_SECONDS.intValue() * 1000);
    private ExampleNamespace namespace;
    private OpcUaServer server;
    private Server2ServerClient client;
    private String serverId;
    private ScheduledFuture<?> sampleFuture;

    private ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private Lock readLock = readWriteLock.readLock();
    private Lock writeLock = readWriteLock.writeLock();
    private static Logger logger = LoggerFactory.getLogger(RedundantServer.class);

    public RedundantServer (OpcUaServerConfig config, String serverId){
        this.serverId = serverId;
        server = new OpcUaServer(config);
        namespace = new ExampleNamespace(server);
        namespace.startup();
        client = new Server2ServerClient(serverId + "Client");
    }

    public RedundantServer setClientEndpointURL (String endpointURL){
        this.client.setEndpointURL(endpointURL);
        return this;
    }

    public OpcUaServerConfig getServerConfig (){
        return server.getConfig();
    }

    private <E> RedundantServer setProperty (NodeId nodeId, QualifiedProperty<E> property, E value){
        writeLock.lock();
        server.getAddressSpaceManager()
                .getManagedNode(nodeId)
                .get()
                .setProperty(property, value);
        writeLock.unlock();
        return this;
    }

    public <V> V getProperty (NodeId nodeId, QualifiedProperty<V> property){
        readLock.lock();
        V value = (V) server.getAddressSpaceManager()
                .getManagedNode(nodeId)
                .get().getPropertyNode(property)
                .get().getValue().getValue().getValue();
        readLock.unlock();
        return value;
    }

    public RedundantServer setRedundantServerArray (RedundantServerDataType[] array){
        logger.info("Adding {} redundant servers to {}", array.length, this.serverId);
        return setProperty(Identifiers.Server_ServerRedundancy, TransparentRedundancyNode.REDUNDANT_SERVER_ARRAY, array);
    }

    public String getCurrentRedundantServerId (){
        logger.info("Get current redundant server for {}", this.serverId);
        return getProperty(Identifiers.Server_ServerRedundancy, TransparentRedundancyNode.CURRENT_SERVER_ID);
    }

    public RedundantServer setCurrentRedundantServerId (String serverId){
        logger.info("Setting {} as current running server for {}", serverId, this.serverId);
        return setProperty(Identifiers.Server_ServerRedundancy, TransparentRedundancyNode.CURRENT_SERVER_ID, serverId);
    }

    public RedundantServerDataType[] getRedundantServerArray (){
        logger.info("Getting {}'s redundant server array", serverId);
        return getProperty(Identifiers.Server_ServerRedundancy, TransparentRedundancyNode.REDUNDANT_SERVER_ARRAY);
    }

    public RedundantServer setAsCurrentServer (){
        logger.info("Setting {} as Current Redundant Server", this.serverId);
        return setProperty(Identifiers.Server_ServerRedundancy, TransparentRedundancyNode.CURRENT_SERVER_ID, this.serverId);
    }

    public OpcUaServer getServer () {
        return server;
    }

    public ServerState getServerState (){
        return ((ServerStatusNode) server.getAddressSpaceManager()
                .getManagedNode(Identifiers.Server_ServerStatus)
                .get()).getState();
    }

    public RedundantServer setServerState (ServerState newState){
        return setServerState(newState, ubyte(255));
    }

    public RedundantServer setSecondsUntilShutdown (UInteger seconds){
        ((ServerStatusNode) server.getAddressSpaceManager()
                .getManagedNode(Identifiers.Server_ServerStatus)
                .get()).setSecondsTillShutdown(seconds);
        return this;
    }

    public RedundantServer setServerState (ServerState newState, UByte newServiceLevel){
        logger.info("setting {} state/level to {}/{}", serverId, newState.toString(), newServiceLevel);
        // Keep synchronized Server State and Service Level also in the redundant array of this server
        ((ServerStatusNode) server.getAddressSpaceManager()
                .getManagedNode(Identifiers.Server_ServerStatus)
                .get()).setState(newState);

        ((ServerNode) server.getAddressSpaceManager()
                .getManagedNode(Identifiers.Server)
                .get()).setServiceLevel(newServiceLevel);

        RedundantServerDataType[] serverArray = server.getAddressSpaceManager()
                .getManagedNode(Identifiers.Server_ServerRedundancy).get()
                .getProperty(TransparentRedundancyNode.REDUNDANT_SERVER_ARRAY).get();

        // Find the index of this server inside the redundant server array
        int idx = 0;
        while (!serverArray[idx].getServerId().equals(serverId) && idx < serverArray.length) idx++;
        serverArray[idx] = new RedundantServerDataType(this.getServerId(), newServiceLevel, newState);
        setRedundantServerArray(serverArray);

        // Notify subscribers of the changing
        SystemStatusChangeEventNode event = namespace.buildStatusChangeEventNode(newState);
        server.getEventBus().post(event);
        event.delete();
        return this;
    }

    public UInteger getSecondsUntilShutdown (){
        return ((ServerStatusNode) server.getAddressSpaceManager()
                .getManagedNode(Identifiers.Server_ServerStatus)
                .get()).getSecondsTillShutdown();
    }

    private CompletableFuture<RedundantServer> startServer (){
        logger.info("{} is starting as server", this.serverId);
        return server.startup().thenApply((s) -> {
            setServerState(ServerState.Running);
            ScheduledExecutorService executor = server.getScheduledExecutorService();
            int randomFailureTime = ThreadLocalRandom.current().nextInt(5, 10);

            // Simulate the failover of the server after a random period
            executor.schedule(() -> {
                try {
                    logger.error("{} has encountered a fatal error", getServerId());
                    sampleFuture.cancel(false);
                    this.shutdown().get();
                    //this.fail().get(); // Uncomment this to make the redundant server set to stop
                }
                catch (InterruptedException | ExecutionException e) {
                    logger.error("{} can't be shutdown", getServerId());
                    e.printStackTrace();
                }
            }, randomFailureTime, TimeUnit.SECONDS);
            sampleFuture = executor.scheduleAtFixedRate(() -> {
                double nextSampleValue = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
                namespace.setAnalogValue(nextSampleValue);
            }, 0, 567, TimeUnit.MILLISECONDS);
            return RedundantServer.this;
        });
    }

    public CompletableFuture<RedundantServer> startClient (){
        return client.startup().thenApply((client) ->  RedundantServer.this);
    }

    public CompletableFuture<RedundantServer> startup () {
        if (this.serverId.equals(this.getCurrentRedundantServerId())){
            // Run as server
            logger.info("{} is starting as server", this.serverId);
            return startServer();
        }
        else {
            // Run as client
            logger.info("{} is starting as client", this.serverId);
            return startClient();
        }
    }

    private CompletableFuture<RedundantServer> fail (){
        return new CompletableFuture<>().thenApply((s) -> {
                logger.info("Set {} to failed", this.serverId);
                setServerState(ServerState.Failed, ubyte(0));
                return RedundantServer.this;
            });
    }

    public CompletableFuture<RedundantServer> shutdown () {
        if (this.serverId.equals(this.getCurrentRedundantServerId())){
            logger.info("{} will be shut down in {} seconds", serverId, SHUTDOWN_DELAY_SECONDS);
            setSecondsUntilShutdown(SHUTDOWN_DELAY_SECONDS);
            setServerState(ServerState.Shutdown);
            try {
                // Wait for the client to receive the notification so they can start a cleaning disconnection
                Thread.sleep(SHUTDOWN_DELAY_MILLIS.intValue());
            }
            catch (InterruptedException e) {
                logger.error("{}'s thread can't sleep", this.getServerId());
                e.printStackTrace();
            }
            return server.shutdown().thenApply((s) -> {
                    logger.info("{} has been shut down", serverId);
                    sampleFuture.cancel(true);
                    return RedundantServer.this;
            });
        }
        else {
            return client.shutdown().thenApply((c) -> RedundantServer.this);
        }
    }

    public String getServerId (){
        return serverId;
    }

    public Double getAnalogValue(){
        return namespace.getAnalogValue();
    }

    public RedundantServer setRedundancySupport (RedundancySupport support){
        return setProperty(Identifiers.Server_ServerRedundancy, TransparentRedundancyNode.REDUNDANCY_SUPPORT, support);
    }
}
