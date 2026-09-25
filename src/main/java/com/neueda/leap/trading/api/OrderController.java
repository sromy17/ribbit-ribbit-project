package com.neueda.leap.trading.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.neueda.leap.trading.service.OrderProcessingEngine;
import com.neueda.leap.trading.service.OrderService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderProcessingEngine orderProcessingEngine;
    private final OrderService orderService;

    public OrderController(OrderProcessingEngine orderProcessingEngine, OrderService orderService) {
        this.orderProcessingEngine = orderProcessingEngine;
        this.orderService = orderService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateOrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @PostMapping("/process")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessOrderResponse processOrder(@Valid @RequestBody ProcessOrderRequest request) {
        return orderProcessingEngine.processSingleOrder(request);
    }

    @PostMapping("/{orderId}/execute")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessOrderResponse executeOrder(@PathVariable Integer orderId) {
        return orderService.executeOrder(orderId);
    }

    @PostMapping("/{orderId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(@PathVariable Integer orderId) {
        orderService.cancelOrder(orderId);
    }

    @GetMapping("/account/{accountId}")
    public Object getOrdersByAccount(@PathVariable Integer accountId) {
        return orderService.getOrders(accountId);
    }
}
