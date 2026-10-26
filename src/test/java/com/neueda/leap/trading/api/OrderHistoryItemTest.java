package com.neueda.leap.trading.api;

import com.neueda.leap.trading.api.dto.OrderHistoryItem;
import com.neueda.leap.trading.domain.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderHistoryItemTest {
    private Order order(OrderSide side) {
        var order = new Order();
        order.setOrderId(1);
        order.setSide(side);
        order.setStatus(OrderStatus.EXECUTED);
        order.setQuantity(20); // Requested quantity must not inflate actual fills.
        order.setPrice(new BigDecimal("99"));
        return order;
    }
    private Trade fill(int quantity, String price, String fee) {
        var trade = new Trade();
        trade.setExecutedQuantity(quantity);
        trade.setExecutionPrice(new BigDecimal(price));
        trade.setFee(fee == null ? null : new BigDecimal(fee));
        return trade;
    }
    @Test void partialFillsUseExecutionPricesAndActualQuantities() {
        var order = order(OrderSide.BUY);
        order.getTrades().add(fill(4,"100","1"));
        order.getTrades().add(fill(6,"102","1"));
        var history = OrderHistoryItem.from(order);
        assertEquals(10,history.executedQuantity());
        assertEquals(new BigDecimal("1012"),history.executedNotional());
        assertEquals(new BigDecimal("-1014"),history.cashEffect());
        assertNull(history.createdAtUtc()); // No invented historical timestamp.
    }
    @Test void unknownHistoricalFeeDoesNotBecomeZeroOrInventNetCash() {
        var order = order(OrderSide.SELL);
        order.getTrades().add(fill(2,"100",null));
        var history = OrderHistoryItem.from(order);
        assertEquals(new BigDecimal("200"),history.executedNotional());
        assertNull(history.fee());
        assertNull(history.cashEffect());
    }
    @Test void saleCashSubtractsFees() {
        var order = order(OrderSide.SELL);
        order.getTrades().add(fill(2,"100","1"));
        assertEquals(new BigDecimal("199"),OrderHistoryItem.from(order).cashEffect());
    }
}
