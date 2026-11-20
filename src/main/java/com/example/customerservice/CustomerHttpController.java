package com.example.customerservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerHttpController {

    @GetMapping("/{id}")
    public CustomerDto getCustomer(@PathVariable String id) {
        return new CustomerDto(id, "Customer " + id, "http2@example.com");
    }
}
