package serverLogic;

import com.google.common.collect.Sets;
import org.eclipse.milo.opcua.sdk.server.util.HostnameUtil;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateBuilder;
import org.eclipse.milo.opcua.stack.core.util.SelfSignedCertificateGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

class KeyStoreLoader {
    // TODO: add the creation/retrieve of the client certificate

    private static final Pattern IP_ADDR_PATTERN = Pattern.compile(
        "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$");

    private static final String SERVER_ALIAS = "server-ai";
    private static final String CLIENT_ALIAS = "client-server-ai";
    private static final String APPLICATION_URI = "urn:eclipse:milo:redundant-server";
    private static final String PASSWORD = "password";

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private X509Certificate[] serverCertificateChain;
    private X509Certificate serverCertificate;
    private KeyPair serverKeyPair;

    private X509Certificate clientCertificate;
    private KeyPair clientKeyPair;

    private KeyStore loadKeyStore (Path baseDir, Map<String, String> parameters) throws Exception{
        KeyStore keyStore = KeyStore.getInstance(parameters.getOrDefault("instanceType", "PKCS12"));
        File keyStoreFile = baseDir.resolve(parameters.get("pfxName")).toFile();
        char[] password = parameters.getOrDefault("password", PASSWORD).toCharArray();
        if (!keyStoreFile.exists()) {
            KeyPair keyPair = SelfSignedCertificateGenerator.generateRsaKeyPair(2048);
            keyStore.load(null, password);
            X509Certificate certificate = buildCertificate(parameters, keyPair);
            keyStore.setKeyEntry(parameters.getOrDefault("keyStoreAlias", SERVER_ALIAS),
                    keyPair.getPrivate(),
                    password,
                    new X509Certificate[]{certificate}
            );
            keyStore.store(new FileOutputStream(keyStoreFile), password);
        }
        else {
            keyStore.load(new FileInputStream(keyStoreFile), password);
        }
        return keyStore;
    }

    KeyStoreLoader loadClientKeyStore (Path baseDir, String pfxName) throws Exception{
        Map<String, String> parameters = Map.ofEntries(
                Map.entry("password", PASSWORD),
                Map.entry("instanceType", "PKCS12"),
                Map.entry("pfxName", pfxName), // Mandatory
                Map.entry("commonName", "RedundantServerDemo"),
                Map.entry("organization", "Demo Inc"),
                Map.entry("organizationalUnit", "Dev"),
                Map.entry("localityName", "Pisa"),
                Map.entry("stateName", "Italy"),
                Map.entry("countryCode", "IT"),
                Map.entry("applicationUri", "urn:demo:redundant:server2serverClient")
            );
        KeyStore clientKeyStore = loadKeyStore(baseDir, parameters);
        Key clientPrivateKey = clientKeyStore.getKey(CLIENT_ALIAS, PASSWORD.toCharArray());
        if (clientPrivateKey instanceof PrivateKey) {
            clientCertificate = (X509Certificate) clientKeyStore.getCertificate(CLIENT_ALIAS);
            PublicKey serverPublicKey = clientCertificate.getPublicKey();
            clientKeyPair = new KeyPair(serverPublicKey, (PrivateKey) clientPrivateKey);
        }
        return this;
    }

    public KeyStoreLoader loadServerKeyStore (Path baseDir, String pfxName) throws Exception{
        Map<String, String> parameters = Map.ofEntries(
                Map.entry("instanceType", "PKCS12"),
                Map.entry("password", PASSWORD),       // Should be mandatory
                Map.entry("pfxName", pfxName), // Mandatory
                Map.entry("commonName", "RedundantServerDemo"),
                Map.entry("organization", "Demo Inc"),
                Map.entry("organizationalUnit", "Dev"),
                Map.entry("localityName", "Pisa"),
                Map.entry("stateName", "Italy"),
                Map.entry("countryCode", "IT"),
                Map.entry("applicationUri", "urn:demo:redundant:server"),
                Map.entry("keyStoreAlias", SERVER_ALIAS)
        );
        KeyStore keyStore = loadKeyStore(baseDir, parameters);
        Key serverPrivateKey = keyStore.getKey(SERVER_ALIAS, PASSWORD.toCharArray());
        if (serverPrivateKey instanceof PrivateKey) {
            serverCertificate = (X509Certificate) keyStore.getCertificate(SERVER_ALIAS);

            serverCertificateChain = Arrays.stream(keyStore.getCertificateChain(SERVER_ALIAS))
                    .map(X509Certificate.class::cast)
                    .toArray(X509Certificate[]::new);

            PublicKey serverPublicKey = serverCertificate.getPublicKey();
            serverKeyPair = new KeyPair(serverPublicKey, (PrivateKey) serverPrivateKey);
        }
        return this;
    }

    X509Certificate buildCertificate (Map<String, String> parameters, KeyPair keyPair) throws Exception {

        SelfSignedCertificateBuilder builder = new SelfSignedCertificateBuilder(keyPair)
            .setCommonName(parameters.getOrDefault("commonName","Eclipse Milo Redundant Server"))
            .setOrganization(parameters.getOrDefault("organization","Demo Inc"))
            .setOrganizationalUnit(parameters.getOrDefault("organizationalUnit", ""))
            .setLocalityName(parameters.getOrDefault("localityName","Pisa"))
            .setStateName(parameters.getOrDefault("stateName","Italy"))
            .setCountryCode(parameters.getOrDefault("countryCode","IT"))
            .setApplicationUri(parameters.getOrDefault("applicationUri", APPLICATION_URI));

        // Get as many hostnames and IP addresses as we can listed in the certificate.
        Set<String> hostnames = Sets.union(
            Sets.newHashSet(HostnameUtil.getHostname()),
            HostnameUtil.getHostnames("0.0.0.0", false)
        );

        for (String hostname : hostnames)
            if (IP_ADDR_PATTERN.matcher(hostname).matches()) builder.addIpAddress(hostname);
            else builder.addDnsName(hostname);

        return builder.build();
    }

    X509Certificate getServerCertificate() {
        return serverCertificate;
    }

    public X509Certificate[] getServerCertificateChain() {
        return serverCertificateChain;
    }

    KeyPair getServerKeyPair() {
        return serverKeyPair;
    }

    X509Certificate getClientCertificate() {
        return clientCertificate;
    }

    KeyPair getClientKeyPair() {
        return clientKeyPair;
    }
}
