package com.neueda.leap.trading.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.OrderStatus;
import com.neueda.leap.trading.repository.mybatis.AccountOrderSummary;
import com.neueda.leap.trading.service.contract.OrderService;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    void createOrder_returnsCreated() throws Exception {
        ProcessOrderResponse response = new ProcessOrderResponse(
            501,
            701,
            OrderStatus.EXECUTED,
            10,
            new BigDecimal("25.00"),
            new BigDecimal("0.50"),
            new AccountOrderSummary(1, "test-user", new BigDecimal("9974.50"), 0L, 1L, new BigDecimal("250.00"))
        );
        when(orderService.submitOrder(any(ProcessOrderRequest.class))).thenReturn(response);

        CreateAccountOrderRequest request = new CreateAccountOrderRequest(
            "AAPL",
            OrderSide.BUY,
            10,
            new BigDecimal("25.00")
        );

        mockMvc.perform(post("/accounts/1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(501))
            .andExpect(jsonPath("$.tradeId").value(701))
            .andExpect(jsonPath("$.status").value("EXECUTED"))
            .andExpect(jsonPath("$.executionPrice").value(25.00));

        verify(orderService).submitOrder(new ProcessOrderRequest(1, "AAPL", OrderSide.BUY, 10, new BigDecimal("25.00")));
    }

    @Test
    void cancelOrder_returnsNoContentWhenOrderBelongsToAccount() throws Exception {
        Order order = new Order();
        order.setOrderId(101);

        when(orderService.getOrders(1)).thenReturn(List.of(order));

        mockMvc.perform(delete("/accounts/1/orders/101/status"))
            .andExpect(status().isNoContent());

        verify(orderService).cancelOrder(101);
    }

    @Test
    void cancelOrder_returnsNotFoundWhenOrderDoesNotBelongToAccount() throws Exception {
        when(orderService.getOrders(1)).thenReturn(List.of());

        mockMvc.perform(delete("/accounts/1/orders/999/status"))
            .andExpect(status().isNotFound());

        verify(orderService, never()).cancelOrder(999);
    }
}
