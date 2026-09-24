package com.neueda.leap.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.domain.AccountType;
import com.neueda.leap.trading.domain.Instrument;
import com.neueda.leap.trading.domain.TradingAccount;
import com.neueda.leap.trading.domain.User;
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

    private Integer accountId;
    private Integer instrumentId;

    @BeforeEach
    void setUp() {
        tradeRepository.deleteAll();
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
    void processSingleBuyOrderEndToEnd() throws Exception {
        Map<String, Object> request = Map.of(
            "accountId", accountId,
            "instrumentId", instrumentId,
            "side", "BUY",
            "quantity", 10,
            "price", new BigDecimal("100.00")
        );

        mockMvc.perform(post("/api/orders/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("EXECUTED"))
            .andExpect(jsonPath("$.executedQuantity").value(10))
            .andExpect(jsonPath("$.accountSummary.accountId").value(accountId));

        TradingAccount reloaded = accountRepository.findById(accountId).orElseThrow();
        assertThat(reloaded.getAvailableFunds()).isEqualByComparingTo("8999.00");
        assertThat(orderRepository.findByAccountAccountId(accountId)).hasSize(1);
        assertThat(tradeRepository.findAll()).hasSize(1);
    }
}
