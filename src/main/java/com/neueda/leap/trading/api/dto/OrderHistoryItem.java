package com.neueda.leap.trading.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.Trade;

/** Detached history response; no lazy JPA objects or invented execution totals. */
public record OrderHistoryItem(Integer orderId,String symbol,String side,String status,Integer quantity,
    BigDecimal price,LocalDate dateRequested,Instant createdAtUtc,int executedQuantity,
    BigDecimal executedNotional,BigDecimal fee,BigDecimal cashEffect) {
    public static OrderHistoryItem from(Order order) {
        var trades=order.getTrades();
        int filled=trades.stream().mapToInt(Trade::getExecutedQuantity).sum();
        BigDecimal gross=trades.stream().map(t->t.getExecutionPrice().multiply(BigDecimal.valueOf(t.getExecutedQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal fees=trades.stream().anyMatch(t->t.getFee()==null)?null:trades.stream().map(Trade::getFee).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal cash=fees==null?null:(order.getSide().name().equals("BUY")?gross.add(fees).negate():gross.subtract(fees));
        return new OrderHistoryItem(order.getOrderId(),order.getSymbol(),order.getSide().name(),order.getStatus().name(),order.getQuantity(),order.getPrice(),order.getDateRequested(),order.getCreatedAtUtc(),filled,gross,fees,cash);
    }
}
