package com.neueda.leap.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.domain.AccountType;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.Holding;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.domain.OrderSide;
import com.neueda.leap.trading.domain.OrderStatus;
import com.neueda.leap.trading.domain.Trade;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.HoldingRepository;
import com.neueda.leap.trading.repository.jpa.InstrumentRepository;
import com.neueda.leap.trading.repository.jpa.OrderRepository;
import com.neueda.leap.trading.repository.jpa.TradeRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import com.neueda.leap.trading.repository.jpa.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderProcessingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TradingAccountRepository accountRepository;

    @Autowired
    private InstrumentRepository instrumentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private HoldingRepository holdingRepository;

    private Integer accountId;
    private Integer instrumentId;

    @BeforeEach
    void setUp() {
        tradeRepository.deleteAll();
        holdingRepository.deleteAll();
        orderRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        instrumentRepository.deleteAll();

        User user = new User();
        user.setUsername("alice");
        user.setSalt("salt-1");
        user.setUserHashedSaltedPassword("hash-1");
        user = userRepository.save(user);

        TradingAccount account = new TradingAccount();
        account.setUser(user);
        account.setAvailableFunds(new BigDecimal("10000.00"));
        account.setCreationDate(LocalDate.now());
        account.setType(AccountType.CASH);
        account = accountRepository.save(account);

        Instrument instrument = new Instrument();
        instrument.setTicker("AAPL");
        instrument.setInstrumentName("Apple");
        instrument = instrumentRepository.save(instrument);

        this.accountId = account.getAccountId();
        this.instrumentId = instrument.getInstrumentId();
    }

    @Test
    void processSingleTradeRequestOrder() {

        

    }


    @Test
    void processSingleBuyOrderEndToEnd() throws Exception {
        Map<String, Object> createRequest = Map.of(
            "accountId", accountId,
            "instrumentId", instrumentId,
            "side", "BUY",
            "quantity", 1,
            "price", new BigDecimal("1.00")
        );

        MvcResult createResult = mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.accountId").value(accountId))
            .andExpect(jsonPath("$.instrumentId").value(instrumentId))
            .andExpect(jsonPath("$.orderId").isNumber())
            .andReturn();

        Integer createdOrderId = objectMapper.readTree(createResult.getResponse().getContentAsString())
            .get("orderId")
            .asInt();

        mockMvc.perform(post("/api/orders/{orderId}/execute", createdOrderId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("EXECUTED"))
            .andExpect(jsonPath("$.orderId").value(createdOrderId))
            .andExpect(jsonPath("$.executedQuantity").value(1))
            .andExpect(jsonPath("$.executionPrice").value(1.00))
            .andExpect(jsonPath("$.accountSummary.accountId").value(accountId));

        TradingAccount reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getAvailableFunds()).isEqualByComparingTo("9998.00");

        List<Order> accountOrders = orderRepository.findByAccountAccountId(accountId);
        assertThat(accountOrders).hasSize(1);

        Order executedOrder = accountOrders.stream()
            .filter(order -> order.getStatus() == OrderStatus.EXECUTED)
            .findFirst()
            .orElseThrow();
        assertThat(executedOrder.getSide()).isEqualTo(OrderSide.BUY);
        assertThat(executedOrder.getQuantity()).isEqualTo(1);
        assertThat(executedOrder.getPrice()).isEqualByComparingTo("1.00");

        List<Trade> trades = tradeRepository.findAll();
        assertThat(trades).hasSize(1);
        Trade trade = trades.get(0);
        assertThat(trade.getExecutedQuantity()).isEqualTo(1);
        assertThat(trade.getExecutionPrice()).isEqualByComparingTo("1.00");
        assertThat(trade.getOrder().getOrderId()).isEqualTo(executedOrder.getOrderId());

        Holding holding = holdingRepository
            .findByAccountAccountIdAndInstrumentInstrumentId(accountId, instrumentId)
            .orElseThrow();
        assertThat(holding.getQuantity()).isEqualTo(1);
        assertThat(holding.getAverageCost()).isEqualByComparingTo("1.000000");
    }
}
