package com.example.customerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

/**
 * Demonstrates OkHttp's synchronous request capability for the customer API.
 *
 * <p>{@link #getCustomer(String, String)} uses {@code Call.execute()}, which performs the
 * network exchange on the calling thread and returns only after the response is available.
 * This makes the method convenient for simple command-line programs and sequential workflows,
 * but callers should avoid using it on a UI thread or a server request thread when the HTTP
 * exchange could take a noticeable amount of time.</p>
 *
 * <p>The client configuration is shared with the other customer API examples: it negotiates
 * HTTP/2 when the server supports it, verifies HTTPS certificates using the normal JVM trust
 * store by default, and can use a PKCS#12 trust store configured with
 * {@code TLS_TRUSTSTORE_PATH} and {@code TLS_TRUSTSTORE_PASSWORD}. Responses are always closed
 * after parsing so their bodies and underlying connections can be safely reused. Close this
 * client when its work is complete to release its dispatcher and connection pool.</p>
 */
public final class OkHttpCustomerClient implements AutoCloseable {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public OkHttpCustomerClient() throws IOException {
        this(CustomerApiHttpSupport.createHttpClient(), new ObjectMapper());
    }

    OkHttpCustomerClient(OkHttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public CustomerDto getCustomer(String baseUrl, String customerId) throws IOException {
        Request request = CustomerApiHttpSupport.buildCustomerRequest(baseUrl, customerId);
        try (Response response = client.newCall(request).execute()) {
            return CustomerApiHttpSupport.parseCustomerResponse(response, objectMapper);
        }
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    public static void main(String[] args) throws IOException {
        String baseUrl = args.length > 0 ? args[0] : "https://localhost:8443";
        String customerId = args.length > 1 ? args[1] : "123";

        try (OkHttpCustomerClient client = new OkHttpCustomerClient()) {
            CustomerDto customer = client.getCustomer(baseUrl, customerId);
            System.out.println("HTTP protocol: HTTP/2");
            System.out.printf("Customer: id=%s, name=%s, email=%s%n",
                    customer.id(), customer.name(), customer.email());
        }
    }

}
