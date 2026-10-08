package com.neueda.leap.trading.service.impl;

import com.neueda.leap.trading.repository.mybatis.OLAPMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class OLAPETLService {
    
    private static final Logger logger = LoggerFactory.getLogger(OLAPETLService.class);
    
    @Autowired
    private OLAPMapper olapMapper;
    
    @Transactional
    public void executeTradeFacts() {
        try {
            logger.info("Loading trade_facts from OLTP...");
            olapMapper.truncateTradeFacts();
            int rowsInserted = olapMapper.loadTradeFacts();
            logger.info("Loaded {} trade facts", rowsInserted);
        } catch (Exception e) {
            logger.error("Error loading trade_facts", e);
            throw new OLAPETLException("Failed to load trade_facts", e);
        }
    }
    
    @Transactional
    public void executeAccountsInfo() {
        try {
            logger.info("Loading accounts_info from OLTP...");
            olapMapper.truncateAccountsInfo();
            int rowsInserted = olapMapper.loadAccountsInfo();
            logger.info("✓ Loaded {} account records", rowsInserted);
        } catch (Exception e) {
            logger.error("Error loading accounts_info", e);
            throw new OLAPETLException("Failed to load accounts_info", e);
        }
    }
    
    @Transactional
    public void executeEmployeesInfo() {
        try {
            logger.info("Loading employees_info from OLTP...");
            olapMapper.truncateEmployeesInfo();
            int rowsInserted = olapMapper.loadEmployeesInfo();
            logger.info("✓ Loaded {} employee records", rowsInserted);
        } catch (Exception e) {
            logger.error("Error loading employees_info", e);
            throw new OLAPETLException("Failed to load employees_info", e);
        }
    }
    
    @Transactional
    public void calculateHoldings() {
        try {
            logger.info("Calculating holdings from executed trades...");
            olapMapper.truncateHoldingsInfo();
            int rowsInserted = olapMapper.loadHoldingsInfo();
            logger.info("✓ Calculated {} holdings", rowsInserted);
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