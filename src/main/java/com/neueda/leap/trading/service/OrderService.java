package com.neueda.leap.trading.service;

import java.util.List;

import com.neueda.leap.trading.api.CreateOrderRequest;
import com.neueda.leap.trading.api.CreateOrderResponse;
import com.neueda.leap.trading.api.ProcessOrderRequest;
import com.neueda.leap.trading.api.ProcessOrderResponse;
import com.neueda.leap.trading.domain.Order;

public interface OrderService {
    CreateOrderResponse createOrder(CreateOrderRequest request);

    ProcessOrderResponse executeOrder(Integer orderId);

    ProcessOrderResponse submitOrder(ProcessOrderRequest request);

    void cancelOrder(Integer orderId);

    List<Order> getOrders(Integer accountId);
}
