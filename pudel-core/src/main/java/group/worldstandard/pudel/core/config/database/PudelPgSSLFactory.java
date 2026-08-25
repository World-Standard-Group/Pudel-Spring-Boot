/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package group.worldstandard.pudel.core.config.database;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Self-contained PostgreSQL SSL socket factory, wired via the {@code sslfactory=}
 * JDBC URL parameter. It builds the TLS {@link SSLContext} from PEM material on
 * disk instead of relying on PgJDBC's built-in {@code PEMKeyManager}.
 *
 * <p>Why this exists: PgJDBC's default PEM loader only understands <em>RSA</em>
 * client keys and additionally enforces a strict 0600 file-permission check.
 * By loading the CA / client cert / client key ourselves we support any key
 * algorithm the JVM understands (RSA, Ed25519, EC, ...) and remove the
 * permission constraint that previously forced a copy-to-0600 hack in the
 * container entrypoint.</p>
 *
 * <p>Activation: the JDBC URL sets {@code sslfactory=} to this class. The three
 * PEM paths (CA / client cert / client key) are supplied as JVM system
 * properties, written by the container entrypoint:
 * <pre>
 *   -Dpudel.ssl.ca=/app/keys/rootCA.crt
 *   -Dpudel.ssl.cert=/app/keys/pudel.crt
 *   -Dpudel.ssl.key=/app/keys/pudel.key
 * </pre>
 * The client key may be PEM (PKCS#8 or PKCS#1) or raw PKCS#8 DER. PgJDBC
 * instantiates this class via its no-arg constructor and uses it as the
 * {@link SSLSocketFactory} for the connection.</p>
 */
public class PudelPgSSLFactory extends SSLSocketFactory {

    /** System property keys written by the entrypoint. */
    private static final String SYS_CA = "pudel.ssl.ca";
    private static final String SYS_CERT = "pudel.ssl.cert";
    private static final String SYS_KEY = "pudel.ssl.key";

    private final SSLSocketFactory delegate;

    /**
     * No-arg constructor required by PgJDBC's reflective instantiation of the
     * {@code sslfactory=} class. Reads the PEM paths from system properties and
     * builds the TLS context immediately.
     */
    public PudelPgSSLFactory() {
        this.delegate = buildDelegate();
    }

    private static SSLSocketFactory buildDelegate() {
        try {
            final String ca = System.getProperty(SYS_CA);
            final String cert = System.getProperty(SYS_CERT);
            final String key = System.getProperty(SYS_KEY);
            if (ca == null || cert == null || key == null) {
                throw new IllegalStateException("PudelPgSSLFactory requires system properties "
                        + SYS_CA + ", " + SYS_CERT + ", " + SYS_KEY);
            }
            return buildSslContext(ca.trim(), cert.trim(), key.trim()).getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Pudel SSL context: " + e.getMessage(), e);
        }
    }

    private static SSLContext buildSslContext(String caPath, String clientCertPath, String clientKeyPath)
            throws Exception {
        // Trust: CA that signs the server certificate.
        final X509Certificate caCert = (X509Certificate) readCertificate(caPath);
        final KeyStore trustStore = KeyStore.getInstance("PKCS12");
        trustStore.load(null, null);
        trustStore.setCertificateEntry("pudel-ca", caCert);
        final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trustStore);
        final TrustManager[] trustManagers = tmf.getTrustManagers();

        // Key: client cert + private key for mTLS.
        final X509Certificate clientCert = (X509Certificate) readCertificate(clientCertPath);
        final PrivateKey clientKey = readPrivateKey(clientKeyPath);
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setCertificateEntry("pudel-client", clientCert);
        keyStore.setKeyEntry("pudel-client-key", clientKey, new char[0], new Certificate[]{clientCert});
        final KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keyStore, new char[0]);
        final KeyManager[] keyManagers = kmf.getKeyManagers();

        final SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(keyManagers, trustManagers, new SecureRandom());
        return ctx;
    }

    private static Certificate readCertificate(String path) throws Exception {
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            final byte[] der = stripPem(in.readAllBytes());
            return CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
        }
    }

    private static PrivateKey readPrivateKey(String path) throws Exception {
        final byte[] raw = Files.readAllBytes(Paths.get(path));
        final byte[] der = stripPem(raw);
        final PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);

        // Ordered roughly by most common usage to optimize the loop
        String[] algorithms = {
                "RSA", "EC", "EdDSA", "RSASSA-PSS", "XDH", "DiffieHellman", "DSA", "HSS/LMS", "ML-DSA", "ML-KEM"
        };

        for (String alg : algorithms) {
            try {
                return KeyFactory.getInstance(alg).generatePrivate(spec);
            } catch (Exception ignored) {
                // try the next algorithm
            }
        }

        throw new IllegalArgumentException("Unsupported private key algorithm in " + path
                + " (tried RSA, EC, EdDSA, RSASSA-PSS, XDH, DiffieHellman, DSA, HSS/LMS, ML-DSA, ML-KEM)");
    }

    /** Strip optional PEM armour, returning the raw DER bytes. */
    private static byte[] stripPem(byte[] data) {
        final String text = new String(data, StandardCharsets.US_ASCII);
        if (!text.contains("-----BEGIN")) {
            return data; // already DER
        }

        final String b64 = text.replaceAll("-----.+-----", "").replaceAll("\\s+", "");
        return Base64.getDecoder().decode(b64);
    }

    // ---- SSLSocketFactory delegation ----

    @Override
    public String[] getDefaultCipherSuites() {
        return delegate.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return delegate.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        return delegate.createSocket(s, host, port, autoClose);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
        return delegate.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return delegate.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port,
            InetAddress localAddress, int localPort) throws IOException {
        return delegate.createSocket(address, port, localAddress, localPort);
    }
}
