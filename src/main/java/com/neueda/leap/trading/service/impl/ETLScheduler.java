package com.neueda.leap.trading.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@EnableScheduling
public class ETLScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(ETLScheduler.class);
    
    @Autowired
    private OLAPETLService olapETLService;
    
    // Run daily at 2 AM
    @Scheduled(cron = "0 0 2 * * *")
    public void dailyFullETL() {
        logger.info("Daily ETL job triggered");
        olapETLService.runFullETL();
    }
    
    // Run every 6 hours for holdings updates
    @Scheduled(cron = "0 0 */6 * * *")
    public void frequentHoldingsUpdate() {
        logger.info("Frequent holdings update triggered");
        olapETLService.calculateHoldings();
    }
}