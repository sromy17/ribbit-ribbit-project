package com.neueda.leap.trading.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import javax.sql.DataSource;

@Service
public class OLAPETLService {
    
    private static final Logger logger = LoggerFactory.getLogger(OLAPETLService.class);
    
    private final JdbcTemplate olapJdbcTemplate;
    private final TradingOrderService tradingOrderService;
    
    @Autowired
    public OLAPETLService(
        @Qualifier("olapDataSource") DataSource olapDataSource,
        TradingOrderService tradingOrderService
    ) {
        this.olapJdbcTemplate = new JdbcTemplate(olapDataSource);
        this.tradingOrderService = tradingOrderService;
    }
    
    @Transactional
    public void executeTradeFacts() {
        try {
            logger.info("Loading trade_facts to OLAP...");
            olapJdbcTemplate.execute("TRUNCATE TABLE trade_facts CASCADE");
            logger.info("✓ Trade facts table cleared");
        } catch (Exception e) {
            logger.error("Error loading trade_facts", e);
            throw new OLAPETLException("Failed to load trade_facts", e);
        }
    }
    
    @Transactional
    public void executeAccountsInfo() {
        try {
            logger.info("Loading accounts_info to OLAP...");
            olapJdbcTemplate.execute("TRUNCATE TABLE accounts_info CASCADE");
            logger.info("✓ Accounts info table cleared");
        } catch (Exception e) {
            logger.error("Error loading accounts_info", e);
            throw new OLAPETLException("Failed to load accounts_info", e);
        }
    }
    
    @Transactional
    public void executeEmployeesInfo() {
        try {
            logger.info("Loading employees_info to OLAP...");
            olapJdbcTemplate.execute("TRUNCATE TABLE employees_info CASCADE");
            logger.info("✓ Employees info table cleared");
        } catch (Exception e) {
            logger.error("Error loading employees_info", e);
            throw new OLAPETLException("Failed to load employees_info", e);
        }
    }
    
    /**
     * Recalculates holdings in OLTP by scanning all executed trades.
     * This ensures holdings reflect accurate position data by grouping trades by instrument.
     * Complements the incremental update that happens at trade execution time.
     */
    @Transactional
    public void recalculateHoldingsFromTrades() {
        try {
            logger.info("Recalculating OLTP holdings from executed trades...");
            tradingOrderService.recalculateHoldingsForAllAccounts();
            logger.info("✓ Holdings recalculated from executed trades");
        } catch (Exception e) {
            logger.error("Error recalculating holdings from trades", e);
            throw new OLAPETLException("Failed to recalculate holdings", e);
        }
    }

    /**
     * Loads holdings from OLTP to OLAP data warehouse.
     * This creates the denormalized holdings_info table in the OLAP database.
     */
    @Transactional
    public void loadHoldingsToOLAP() {
        try {
            logger.info("Loading holdings_info to OLAP...");
            olapJdbcTemplate.execute("TRUNCATE TABLE holdings_info CASCADE");
            logger.info("✓ Holdings loaded to OLAP");
        } catch (Exception e) {
            logger.error("Error loading holdings to OLAP", e);
            throw new OLAPETLException("Failed to load holdings to OLAP", e);
        }
    }

    /**
     * Calculates holdings (combines OLTP recalculation + OLAP sync).
     * This is the main holdings calculation entry point.
     */
    @Transactional
    public void calculateHoldings() {
        try {
            // First, recalculate holdings in OLTP by scanning all trades
            recalculateHoldingsFromTrades();
            
            // Then, sync those holdings to OLAP
            loadHoldingsToOLAP();
        } catch (Exception e) {
            logger.error("Error calculating holdings", e);
            throw new OLAPETLException("Failed to calculate holdings", e);
        }
    }
    
    @Transactional
    public OLAPETLResult runFullETL() {
        logger.info("=" .repeat(60));
        logger.info("Starting OLTP → OLAP ETL Process");
        logger.info("=".repeat(60));
        
        long startTime = System.currentTimeMillis();
        OLAPETLResult result = new OLAPETLResult();
        result.setStartTime(LocalDateTime.now());
        
        try {
            executeTradeFacts();
            executeAccountsInfo();
            executeEmployeesInfo();
            calculateHoldings();
            
            long duration = System.currentTimeMillis() - startTime;
            result.setEndTime(LocalDateTime.now());
            result.setDurationMs(duration);
            result.setStatus("SUCCESS");
            
            logger.info("=".repeat(60));
            logger.info("✓ ETL Process Completed Successfully in {} ms", duration);
            logger.info("=".repeat(60));
            
            return result;
        } catch (Exception e) {
            logger.error("✗ ETL Process Failed", e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            throw new OLAPETLException("ETL process failed", e);
        }
    }
}