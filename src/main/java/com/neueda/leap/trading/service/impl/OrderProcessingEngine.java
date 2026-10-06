package com.neueda.leap.trading.service.impl;

import com.neueda.leap.trading.service.contract.OrderService;
import org.springframework.stereotype.Component;

import com.neueda.leap.trading.api.ProcessOrderRequest;
import com.neueda.leap.trading.api.ProcessOrderResponse;

@Component
public class OrderProcessingEngine {
    private final OrderService orderService;

    public OrderProcessingEngine(OrderService orderService) {
        this.orderService = orderService;
    }

    public ProcessOrderResponse processSingleOrder(ProcessOrderRequest request) {
        return orderService.submitOrder(request); // Spring injects an implementation of submitOrder at runtime
    }
}
