package com.example.customerservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"grpc.server.port=0", "server.ssl.enabled=false"})
class OkHttpCustomerClientTest {

    @Value("${local.server.port}")
    private int port;

    @Test
    void getsCustomerFromHttpServer() throws IOException {
        try (OkHttpCustomerClient client = new OkHttpCustomerClient()) {
            CustomerDto customer = client.getCustomer(
                    "http://localhost:" + port, "http-123");

            assertThat(customer).isEqualTo(new CustomerDto(
                    "http-123", "Customer http-123", "http2@example.com"));
        }
    }

    @Test
    void getsCustomerAsynchronously() throws Exception {
        try (OkHttpAsyncCustomerClient client = new OkHttpAsyncCustomerClient()) {
            CustomerDto customer = client.getCustomer(
                            "http://localhost:" + port, "async-123")
                    .get(5, TimeUnit.SECONDS);

            assertThat(customer).isEqualTo(new CustomerDto(
                    "async-123", "Customer async-123", "http2@example.com"));
        }
    }

    @Test
    void rejectsBlankCustomerId() throws IOException {
        try (OkHttpCustomerClient client = new OkHttpCustomerClient()) {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> client.getCustomer("http://localhost:" + port, " "));
        }
    }
}
