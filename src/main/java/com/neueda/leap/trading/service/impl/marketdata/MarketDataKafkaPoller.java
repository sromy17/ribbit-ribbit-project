package com.neueda.leap.trading.service.impl.marketdata;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.neueda.leap.trading.repository.jpa.InstrumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MarketDataKafkaPoller {
    private static final Logger log = LoggerFactory.getLogger(MarketDataKafkaPoller.class);

    private final InstrumentRepository instrumentRepository;
    private final FauxnanceQuoteClient fauxnanceQuoteClient;
    private final KafkaQuoteProducer kafkaQuotePublisher;
    private final QuoteCache quoteCache;
    private final boolean kafkaEnabled;
    private final List<String> prioritySymbols;

    public MarketDataKafkaPoller(
        InstrumentRepository instrumentRepository,
        FauxnanceQuoteClient fauxnanceQuoteClient,
        KafkaQuoteProducer kafkaQuotePublisher,
        QuoteCache quoteCache,
        @Value("${market-data.kafka.enabled:false}") boolean kafkaEnabled,
        @Value("${market-data.refresh.priority-symbols:AAPL,MSFT,NVDA,AMZN,GOOGL,META,BRK.B,AVGO,TSLA,JPM,WMT,LLY,V,ORCL,MA,XOM,COST,NFLX,JNJ,HD,PG,BAC,ABBV,CRM,CVX}") String configuredPrioritySymbols
    ) {
        this.instrumentRepository = instrumentRepository;
        this.fauxnanceQuoteClient = fauxnanceQuoteClient;
        this.kafkaQuotePublisher = kafkaQuotePublisher;
        this.quoteCache = quoteCache;
        this.kafkaEnabled = kafkaEnabled;
        this.prioritySymbols = parseSymbols(configuredPrioritySymbols);
    }

    @Scheduled(fixedDelayString = "${market-data.refresh.priority-poll-ms:30000}")
    public void pollAndPublishQuotes() {
        if (!kafkaEnabled) {
            return;
        }

        publishQuotes(prioritySymbols);
    }

    @Scheduled(fixedDelayString = "${market-data.refresh.secondary-poll-ms:300000}")
    public void pollSecondaryQuotes() {
        if (!kafkaEnabled) {
            return;
        }

        Set<String> prioritySet = new HashSet<>(prioritySymbols);
        List<String> secondarySymbols = instrumentRepository.findAll()
            .stream()
            .map(instrument -> instrument != null ? instrument.getTicker() : null)
            .filter(Objects::nonNull)
            .map(ticker -> ticker.trim())
            .filter(s -> !s.isEmpty())
            .map(s -> s.toUpperCase(Locale.ROOT))
            .filter(symbol -> !prioritySet.contains(symbol))
            .distinct()
            .toList();

        publishQuotes(secondarySymbols);
    }

    private void publishQuotes(List<String> symbols) {
        for (String symbol : symbols) {
            try {
                QuoteSnapshot snapshot = QuoteSnapshot.from(fauxnanceQuoteClient.fetchMarketQuote(symbol));
                quoteCache.upsert(snapshot);
                kafkaQuotePublisher.produce(snapshot);
            } catch (RuntimeException ex) {
                log.debug("Skipping quote publish for symbol {} due to polling error", symbol, ex);
            }
        }
    }

    private List<String> parseSymbols(String configuredSymbols) {
        if (configuredSymbols == null || configuredSymbols.isBlank()) {
            return List.of();
        }

        return Arrays.stream(configuredSymbols.split(","))
            .map(symbol -> symbol.trim())
            .filter(symbol -> !symbol.isEmpty())
            .map(symbol -> symbol.toUpperCase(Locale.ROOT))
            .distinct()
            .limit(25)
            .collect(Collectors.toList());
    }
}
