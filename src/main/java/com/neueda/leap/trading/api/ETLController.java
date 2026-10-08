package com.neueda.leap.trading.api;

import com.neueda.leap.trading.service.impl.OLAPETLService;
import com.neueda.leap.trading.service.impl.OLAPETLResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/etl")
public class ETLController {
    
    @Autowired
    private OLAPETLService olapETLService;
    
    @PostMapping("/trigger-full")
    public ResponseEntity<OLAPETLResult> triggerFullETL() {
        OLAPETLResult result = olapETLService.runFullETL();
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/trigger-trade-facts")
    public ResponseEntity<String> triggerTradeFacts() {
        olapETLService.executeTradeFacts();
        return ResponseEntity.ok("Trade facts loading initiated");
    }
    
    @PostMapping("/trigger-accounts")
    public ResponseEntity<String> triggerAccountsInfo() {
        olapETLService.executeAccountsInfo();
        return ResponseEntity.ok("Accounts info loading initiated");
    }
    
    @PostMapping("/trigger-holdings")
    public ResponseEntity<String> triggerHoldingsCalculation() {
        olapETLService.calculateHoldings();
        return ResponseEntity.ok("Holdings calculation initiated");
    }
    
    @PostMapping("/trigger-employees")
    public ResponseEntity<String> triggerEmployeesInfo() {
        olapETLService.executeEmployeesInfo();
        return ResponseEntity.ok("Employees info loading initiated");
    }
}
