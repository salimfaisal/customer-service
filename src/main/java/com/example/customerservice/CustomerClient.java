package com.example.customerservice;

import com.example.customerservice.grpc.Customer;
import com.example.customerservice.grpc.CustomerBatchResponse;
import com.example.customerservice.grpc.CustomerServiceGrpc;
import com.example.customerservice.grpc.GetCustomerRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class CustomerClient {

    public static void main(String[] args) throws InterruptedException {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 9090;
        String customerId = args.length > 2 ? args[2] : "123";

        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            CustomerServiceGrpc.CustomerServiceBlockingStub client =
                    CustomerServiceGrpc.newBlockingStub(channel);

            Customer response = client.getCustomer(
                    GetCustomerRequest.newBuilder().setId(customerId).build());

            System.out.printf("Customer: id=%s, name=%s, email=%s%n",
                    response.getId(), response.getName(), response.getEmail());

            CountDownLatch streamingComplete = new CountDownLatch(1);
            StreamObserver<CustomerBatchResponse> batchObserver = new StreamObserver<>() {
                @Override
                public void onNext(CustomerBatchResponse batch) {
                    System.out.printf("Batch response: received=%d, limit=%d%n",
                            batch.getReceived(), batch.getLimit());
                    batch.getCustomersList().forEach(customer ->
                            System.out.printf("  Customer: id=%s, name=%s, email=%s%n",
                                    customer.getId(), customer.getName(), customer.getEmail()));
                }

                @Override
                public void onError(Throwable error) {
                    System.err.println("Streaming request failed: " + error.getMessage());
                    streamingComplete.countDown();
                }

                @Override
                public void onCompleted() {
                    streamingComplete.countDown();
                }
            };

            StreamObserver<GetCustomerRequest> batchRequest =
                    CustomerServiceGrpc.newStub(channel).collectCustomers(batchObserver);
            for (String id : new String[]{customerId, customerId + "-2", customerId + "-3"}) {
                batchRequest.onNext(GetCustomerRequest.newBuilder().setId(id).build());
            }
            batchRequest.onCompleted();
            streamingComplete.await(5, TimeUnit.SECONDS);
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
