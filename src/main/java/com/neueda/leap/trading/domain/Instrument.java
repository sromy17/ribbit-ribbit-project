package com.neueda.leap.trading.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "instruments")
public class Instrument {
    @Id
    @Column(name = "instrumentid")
    private Integer instrumentId;

    @Column(name = "ticker", nullable = false, length = 25)
    private String ticker;

    @Column(name = "instrumentname", nullable = false, length = 25)
    private String instrumentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private InstrumentStatus status = InstrumentStatus.ACTIVE;

    @Column(name = "asset_class", length = 30)
    private String assetClass;
    @Column(name = "market", length = 40)
    private String market;
    @Column(name = "venue", length = 80)
    private String venue;
    @Column(name = "quote_currency", length = 3)
    private String quoteCurrency;
    @Column(name = "settlement_currency", length = 3)
    private String settlementCurrency;
    @Column(name = "quantity_unit", length = 40)
    private String quantityUnit;
    @Column(name = "price_precision")
    private Integer pricePrecision;
    @Column(name = "quantity_precision")
    private Integer quantityPrecision;
    @Column(name = "contract_multiplier", precision = 30, scale = 10)
    private BigDecimal contractMultiplier;
    @Column(name = "product_convention", length = 80)
    private String productConvention;
    @Column(name = "metadata_source", length = 200)
    private String metadataSource;
    @Column(name = "market_data_symbol", length = 80)
    private String marketDataSymbol;

    public String getMarketDataSymbol() { return marketDataSymbol; }
    public void setMarketDataSymbol(String value) { marketDataSymbol = value; }

    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String value) { assetClass = value; }
    public String getMarket() { return market; }
    public void setMarket(String value) { market = value; }
    public String getVenue() { return venue; }
    public void setVenue(String value) { venue = value; }
    public String getQuoteCurrency() { return quoteCurrency; }
    public void setQuoteCurrency(String value) { quoteCurrency = value; }
    public String getSettlementCurrency() { return settlementCurrency; }
    public void setSettlementCurrency(String value) { settlementCurrency = value; }
    public String getQuantityUnit() { return quantityUnit; }
    public void setQuantityUnit(String value) { quantityUnit = value; }
    public Integer getPricePrecision() { return pricePrecision; }
    public void setPricePrecision(Integer value) { pricePrecision = value; }
    public Integer getQuantityPrecision() { return quantityPrecision; }
    public void setQuantityPrecision(Integer value) { quantityPrecision = value; }
    public BigDecimal getContractMultiplier() { return contractMultiplier; }
    public void setContractMultiplier(BigDecimal value) { contractMultiplier = value; }
    public String getProductConvention() { return productConvention; }
    public void setProductConvention(String value) { productConvention = value; }
    public String getMetadataSource() { return metadataSource; }
    public void setMetadataSource(String value) { metadataSource = value; }

    @JsonIgnore
    @OneToMany(mappedBy = "instrument")
    private List<Order> orders = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "instrument")
    private List<Holding> holdings = new ArrayList<>();

    @JsonIgnore
    @ManyToMany(mappedBy = "instruments")
    private Set<Watchlist> watchlists = new HashSet<>();

    public Integer getInstrumentId() {
        return instrumentId;
    }

    public void setInstrumentId(Integer instrumentId) {
        this.instrumentId = instrumentId;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getInstrumentName() {
        return instrumentName;
    }

    public void setInstrumentName(String instrumentName) {
        this.instrumentName = instrumentName;
    }

    public List<Order> getOrders() {
        return orders;
    }

    public void setOrders(List<Order> orders) {
        this.orders = orders;
    }

    @Transient
    @JsonProperty("trades")
    public List<Trade> getTrades() {
        return orders.stream()
            .flatMap(order -> order.getTrades().stream())
            .collect(Collectors.toList());
    }

    public List<Holding> getHoldings() {
        return holdings;
    }

    public void setHoldings(List<Holding> holdings) {
        this.holdings = holdings;
    }

    public Set<Watchlist> getWatchlists() {
        return watchlists;
    }

    public void setWatchlists(Set<Watchlist> watchlists) {
        this.watchlists = watchlists;
    }

    public InstrumentStatus getStatus() {
        return status;
    }

    public void setStatus(InstrumentStatus status) {
        this.status = status;
    }

    /**
     * Check if this instrument is currently available for trading.
     * BR-05: Validates that instrument is tradable before accepting orders.
     * @return true if the instrument can accept new orders
     */
    public boolean isTradable() {
        // Existing database rows may predate the nullable status column; treat those as active.
        return status == null || status.isTradable();
    }
}
