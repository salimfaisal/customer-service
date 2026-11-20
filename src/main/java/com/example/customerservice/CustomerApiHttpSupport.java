package com.example.customerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.List;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Shared OkHttp setup and HTTP handling used by the synchronous and asynchronous examples.
 *
 * <p>Each call to {@link #createHttpClient()} builds an OkHttp client with its own dispatcher
 * and connection pool. The dispatcher schedules asynchronous calls, runs them on its executor,
 * and limits how many are in flight at once; additional calls wait until capacity is available.
 * The connection pool is also supplied by default. It retains eligible connections for later
 * requests to the same origin (scheme, host, and port), rather than binding a connection to one
 * complete URI. HTTP/1.1 connections can be reused after a response is closed, while HTTP/2 can
 * multiplex concurrent requests over a compatible connection.</p>
 *
 * <p>These examples create a client per standalone demonstration. A long-running application
 * should normally share one client across calls to benefit from its dispatcher and connection
 * pool, then close it only after its outstanding calls have finished.</p>
 */
final class CustomerApiHttpSupport {

    // Prefer HTTP/2, but permit HTTP/1.1 if the HTTPS server cannot negotiate HTTP/2.
    private static final List<Protocol> HTTP2_PROTOCOLS =
            List.of(Protocol.HTTP_2, Protocol.HTTP_1_1);

    private CustomerApiHttpSupport() {
    }

    static OkHttpClient createHttpClient() throws IOException {
        // build() creates OkHttp's default dispatcher and connection pool; no custom pool is needed here.
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .protocols(HTTP2_PROTOCOLS);
        String trustStorePath = System.getenv("TLS_TRUSTSTORE_PATH");
        if (trustStorePath == null || trustStorePath.isBlank()) {
            // Without a custom trust store, HTTPS uses the JVM's standard trusted certificates.
            return builder.build();
        }

        String password = System.getenv("TLS_TRUSTSTORE_PASSWORD");
        if (password == null) {
            throw new IllegalStateException(
                    "TLS_TRUSTSTORE_PASSWORD is required when TLS_TRUSTSTORE_PATH is set");
        }

        char[] passwordChars = password.toCharArray();
        try {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(Path.of(trustStorePath))) {
                trustStore.load(input, passwordChars);
            }

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            X509TrustManager trustManager = Arrays.stream(trustManagerFactory.getTrustManagers())
                    .filter(X509TrustManager.class::isInstance)
                    .map(X509TrustManager.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new GeneralSecurityException(
                            "No X.509 trust manager is available"));

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return builder.sslSocketFactory(sslContext.getSocketFactory(), trustManager).build();
        } catch (GeneralSecurityException error) {
            throw new IOException("Could not configure TLS trust store", error);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    static Request buildCustomerRequest(String baseUrl, String customerId) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("Customer id is required");
        }

        // Add segments separately so special characters in an ID are encoded as path data.
        HttpUrl url = HttpUrl.get(baseUrl)
                .newBuilder()
                .addPathSegment("api")
                .addPathSegment("customers")
                .addPathSegment(customerId)
                .build();
        return new Request.Builder().url(url).get().build();
    }

    static CustomerDto parseCustomerResponse(Response response, ObjectMapper objectMapper)
            throws IOException {
        // HTTPS requests in this example intentionally fail if negotiation falls back to HTTP/1.1.
        if (response.request().url().isHttps() && response.protocol() != Protocol.HTTP_2) {
            throw new IOException("Expected HTTP/2 but negotiated " + response.protocol());
        }
        // The calling client owns the Response and must close it after this method returns.
        ResponseBody body = response.body();
        if (!response.isSuccessful()) {
            String responseText = body == null ? "" : body.string();
            throw new IOException("Customer request failed with HTTP "
                    + response.code() + ": " + responseText);
        }
        if (body == null) {
            throw new IOException("Customer response body is empty");
        }
        return objectMapper.readValue(body.string(), CustomerDto.class);
    }
}
