package com.neueda.leap.trading.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.service.contract.OrderService;

import jakarta.validation.Valid;

@RestController
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/accounts/{accountId}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public ProcessOrderResponse createOrder(
        @PathVariable("accountId") Integer accountId,
        @Valid @RequestBody CreateAccountOrderRequest request
    ) {
        return orderService.submitOrder(new ProcessOrderRequest(
            accountId,
            request.ticker(),
            request.side(),
            request.quantity(),
            request.price()
        ));
    }

    @DeleteMapping("/accounts/{accountId}/orders/{orderId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(
        @PathVariable("accountId") Integer accountId,
        @PathVariable("orderId") Integer orderId
    ) {
        boolean accountOwnsOrder = orderService.getOrders(accountId).stream()
            .anyMatch(order -> orderId.equals(order.getOrderId()));

        if (!accountOwnsOrder) {
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Order not found for account: " + accountId
            );
        }

        orderService.cancelOrder(orderId);
    }

    @GetMapping("/accounts/{accountId}/orders")
    public List<Order> getOrdersByAccount(@PathVariable("accountId") Integer accountId) {
        return orderService.getOrders(accountId);
    }
}
