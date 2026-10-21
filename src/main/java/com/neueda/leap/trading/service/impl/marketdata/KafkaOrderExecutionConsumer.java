package com.neueda.leap.trading.service.impl.marketdata;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.service.impl.TradingOrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderExecutionConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaOrderExecutionConsumer.class);

    private final ObjectMapper objectMapper;
    private final TradingOrderService tradingOrderService;
    private final ConcurrentMap<Integer, Object> orderLocks = new ConcurrentHashMap<>();

    public KafkaOrderExecutionConsumer(ObjectMapper objectMapper, TradingOrderService tradingOrderService) {
        this.objectMapper = objectMapper;
        this.tradingOrderService = tradingOrderService;
    }

    @KafkaListener(
        topics = "${market-data.execution.kafka.topic:orders.execute}",
        groupId = "${market-data.execution.kafka.group-id:trading-order-execution}",
        autoStartup = "${market-data.execution.kafka.enabled:false}"
    )
    public void onMessage(String payload) {
        try {
            OrderExecutionCommand command = objectMapper.readValue(payload, OrderExecutionCommand.class);
            if (command.orderId() == null) {
                log.warn("Ignoring order execution command without an order ID");
                return;
            }

            Object lock = orderLocks.computeIfAbsent(command.orderId(), ignored -> new Object());
            synchronized (lock) {
                try {
                    tradingOrderService.executeOrder(command.orderId());
                } catch (IllegalArgumentException ex) {
                    log.warn("Order execution command rejected for order {}: {}", command.orderId(), ex.getMessage());
                }
            }
            orderLocks.remove(command.orderId(), lock);
        } catch (JsonProcessingException ex) {
            log.error("Could not parse order execution command; sending message to container error handling", ex);
            throw new IllegalArgumentException("Invalid order execution command", ex);
        }
    }
}
