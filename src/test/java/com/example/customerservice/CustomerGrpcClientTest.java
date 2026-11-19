package com.example.customerservice;

import com.example.customerservice.grpc.Customer;
import com.example.customerservice.grpc.CustomerBatchResponse;
import com.example.customerservice.grpc.CustomerServiceGrpc;
import com.example.customerservice.grpc.GetCustomerRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CustomerGrpcClientTest {

    @Test
    void getCustomerViaGrpcClient() {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();

        try {
            Customer response = CustomerServiceGrpc.newBlockingStub(channel).getCustomer(
                    GetCustomerRequest.newBuilder().setId("test-123").build());

            assertThat(response.getId()).isEqualTo("test-123");
            assertThat(response.getName()).isEqualTo("Customer test-123");
            assertThat(response.getEmail()).isEqualTo("customer@example.com");
        } finally {
            channel.shutdownNow();
        }
    }

    @Test
    void collectCustomersReturnsAfterLimit() throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", 9090)
                .usePlaintext()
                .build();
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<CustomerBatchResponse> result = new AtomicReference<>();

        try {
            StreamObserver<CustomerBatchResponse> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(CustomerBatchResponse response) {
                    result.set(response);
                }

                @Override
                public void onError(Throwable error) {
                    complete.countDown();
                }

                @Override
                public void onCompleted() {
                    complete.countDown();
                }
            };

            StreamObserver<GetCustomerRequest> requests =
                    CustomerServiceGrpc.newStub(channel).collectCustomers(responseObserver);
            requests.onNext(GetCustomerRequest.newBuilder().setId("client-1").build());
            requests.onNext(GetCustomerRequest.newBuilder().setId("client-2").build());
            requests.onNext(GetCustomerRequest.newBuilder().setId("client-3").build());
            requests.onCompleted();

            assertThat(complete.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get()).isNotNull();
            assertThat(result.get().getReceived()).isEqualTo(3);
            assertThat(result.get().getLimit()).isEqualTo(3);
            assertThat(result.get().getCustomersCount()).isEqualTo(3);
        } finally {
            channel.shutdownNow();
        }
    }
}
