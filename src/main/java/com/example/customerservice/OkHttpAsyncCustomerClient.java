package com.example.customerservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * Demonstrates OkHttp's asynchronous request capability for the customer API.
 *
 * <p>{@link #getCustomer(String, String)} submits a call with OkHttp's {@code enqueue()} API
 * and immediately returns a {@link CompletableFuture}. Unlike {@code Call.execute()}, this
 * does not wait for network I/O on the calling thread. OkHttp runs the exchange on its
 * dispatcher and invokes a callback when either a response arrives or the request fails.
 * Callers can compose the future with {@code thenApply}, {@code thenAccept}, or
 * {@code exceptionally}, or explicitly wait for it when writing a simple command-line demo.</p>
 *
 * <p>The response body is consumed and closed inside the callback, including on unsuccessful
 * HTTP responses, so the connection can be reused. Network, HTTP-status, and response-parsing
 * errors complete the future exceptionally instead of being silently replaced by a default.
 * HTTPS uses the standard JVM trust configuration unless the shared customer API trust-store
 * environment variables are set. Close this client after outstanding calls are finished to
 * release the OkHttp dispatcher and connection pool.</p>
 */
public final class OkHttpAsyncCustomerClient implements AutoCloseable {

    private final OkHttpClient client;
    private final ObjectMapper objectMapper;

    public OkHttpAsyncCustomerClient() throws IOException {
        this(CustomerApiHttpSupport.createHttpClient(), new ObjectMapper());
    }

    OkHttpAsyncCustomerClient(OkHttpClient client, ObjectMapper objectMapper) {
        this.client = client;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<CustomerDto> getCustomer(String baseUrl, String customerId) {
        Request request = CustomerApiHttpSupport.buildCustomerRequest(baseUrl, customerId);
        CompletableFuture<CustomerDto> result = new CompletableFuture<>();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException error) {
                result.completeExceptionally(error);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (response) {
                    result.complete(CustomerApiHttpSupport.parseCustomerResponse(
                            response, objectMapper));
                } catch (IOException | RuntimeException error) {
                    result.completeExceptionally(error);
                }
            }
        });
        return result;
    }

    public static void main(String[] args) throws Exception {
        String baseUrl = args.length > 0 ? args[0] : "https://localhost:8443";
        String customerId = args.length > 1 ? args[1] : "123";

        try (OkHttpAsyncCustomerClient client = new OkHttpAsyncCustomerClient()) {
            CustomerDto customer = client.getCustomer(baseUrl, customerId).get();
            System.out.printf("Customer: id=%s, name=%s, email=%s%n",
                    customer.id(), customer.name(), customer.email());
        }
    }

    @Override
    public void close() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
