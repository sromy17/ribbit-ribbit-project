package com.neueda.leap.trading.service.contract;

import java.util.List;

import com.neueda.leap.trading.api.dto.CreateOrderRequest;
import com.neueda.leap.trading.api.dto.CreateOrderResponse;
import com.neueda.leap.trading.api.dto.ProcessOrderRequest;
import com.neueda.leap.trading.api.dto.ProcessOrderResponse;
import com.neueda.leap.trading.domain.Order;

public interface OrderService {
    CreateOrderResponse createOrder(CreateOrderRequest request);

    ProcessOrderResponse executeOrder(Integer orderId);

    ProcessOrderResponse submitOrder(ProcessOrderRequest request);

    void cancelOrder(Integer orderId);

    List<Order> getOrders(Integer accountId);
}
