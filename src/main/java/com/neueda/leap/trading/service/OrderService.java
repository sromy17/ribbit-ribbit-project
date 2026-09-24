package com.neueda.leap.trading.service;

import java.util.List;

import com.neueda.leap.trading.api.ProcessOrderRequest;
import com.neueda.leap.trading.api.ProcessOrderResponse;
import com.neueda.leap.trading.domain.Order;

public interface OrderService {
    ProcessOrderResponse submitOrder(ProcessOrderRequest request);

    void cancelOrder(Integer orderId);

    List<Order> getOrders(Integer accountId);
}
