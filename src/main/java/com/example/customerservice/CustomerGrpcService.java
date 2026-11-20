package com.example.customerservice;

import com.example.customerservice.grpc.Customer;
import com.example.customerservice.grpc.CustomerBatchResponse;
import com.example.customerservice.grpc.CustomerServiceGrpc;
import com.example.customerservice.grpc.GetCustomerRequest;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService
public class CustomerGrpcService extends CustomerServiceGrpc.CustomerServiceImplBase {

    private static final int CUSTOMER_LIMIT = 3;

    @Override
    public void getCustomer(GetCustomerRequest request,
                            StreamObserver<Customer> responseObserver) {
        if (request.getId().isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Customer id is required")
                    .asRuntimeException());
            return;
        }

        Customer customer = Customer.newBuilder()
                .setId(request.getId())
                .setName("Customer " + request.getId())
                .setEmail("customer@example.com")
                .build();

        responseObserver.onNext(customer);
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<GetCustomerRequest> collectCustomers(
            StreamObserver<CustomerBatchResponse> responseObserver) {
        java.util.List<Customer> customers = new java.util.ArrayList<>(CUSTOMER_LIMIT);

        return new StreamObserver<>() {
            @Override
            public void onNext(GetCustomerRequest request) {
                if (request.getId().isBlank()) {
                    responseObserver.onError(Status.INVALID_ARGUMENT
                            .withDescription("Customer id is required")
                            .asRuntimeException());
                    return;
                }

                if (customers.size() < CUSTOMER_LIMIT) {
                    customers.add(Customer.newBuilder()
                            .setId(request.getId())
                            .setName("Customer " + request.getId())
                            .setEmail("grpc2@example.com")
                            .build());
                }

                if (customers.size() == CUSTOMER_LIMIT) {
                    responseObserver.onNext(buildResponse(customers));
                    responseObserver.onCompleted();
                }
            }

            @Override
            public void onError(Throwable error) {
                // The client has already failed; no server response is possible.
            }

            @Override
            public void onCompleted() {
                if (customers.size() < CUSTOMER_LIMIT) {
                    responseObserver.onNext(buildResponse(customers));
                    responseObserver.onCompleted();
                }
            }

            private CustomerBatchResponse buildResponse(java.util.List<Customer> collected) {
                return CustomerBatchResponse.newBuilder()
                        .addAllCustomers(collected)
                        .setReceived(collected.size())
                        .setLimit(CUSTOMER_LIMIT)
                        .build();
            }
        };
    }
}
