package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.trading.analytics.*;
import com.neueda.leap.trading.service.impl.*;
import com.neueda.leap.trading.service.impl.marketdata.*;
import com.neueda.leap.trading.service.contract.MarketQuote;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/** Release regressions use the same disposable, fully migrated PostgreSQL fixture. */
final class AnalyticsReleaseAssertions {
    private static AnalyticsQuery month(String first) {
        var date=LocalDate.parse(first);return new AnalyticsQuery(date,date.withDayOfMonth(date.lengthOfMonth()),"UTC",1,null,null,0,25,"fills","desc");
    }
    @SuppressWarnings("unchecked")
    static void verify(ConfigurableApplicationContext context,JdbcTemplate source,JdbcTemplate db) throws Exception {
        var etl=context.getBean(OltpToOlapEtlService.class);var analytics=context.getBean(AnalyticsService.class);
        String abandoned=UUID.randomUUID().toString();
        db.update("INSERT INTO etl_refresh_runs(run_id,status,source_watermark,started_at) VALUES(?,'RUNNING',CURRENT_TIMESTAMP, CURRENT_TIMESTAMP-INTERVAL '1 hour')",abandoned);
        etl.runFullLoad();assertEquals("ABANDONED",db.queryForObject("SELECT status FROM etl_refresh_runs WHERE run_id=?",String.class,abandoned));
        String good=db.queryForObject("SELECT DISTINCT dataset_run_id FROM fill_facts",String.class);
        // A publisher held by a different connection must exclude a concurrent process.
        try(var connection=db.getDataSource().getConnection();var statement=connection.createStatement()) {
            statement.execute("SELECT pg_advisory_lock(738291)");
            try {assertThrows(IllegalStateException.class,etl::runFullLoad);}
            finally {statement.execute("SELECT pg_advisory_unlock(738291)");}
        }
        assertEquals(good,db.queryForObject("SELECT DISTINCT dataset_run_id FROM fill_facts",String.class));
        // Orphaned/out-of-order lifecycle evidence cannot silently disappear through reporting joins.
        source.update("INSERT INTO order_lifecycle_events(order_id,account_id,event_type,event_sequence,event_version,occurred_at_utc,correlation_id) VALUES(999999,1,'FILL_COMMITTED',1,1,CURRENT_TIMESTAMP,'orphan')");
        assertThrows(IllegalStateException.class,etl::runFullLoad);
        assertEquals(good,db.queryForObject("SELECT DISTINCT dataset_run_id FROM fill_facts",String.class));
        source.update("DELETE FROM order_lifecycle_events WHERE order_id=999999");
        source.update("""
            INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,status,created_at_utc,correlation_id)
            VALUES(30000,1,1,'BUY',12,100,'2026-01-31','CANCELLED','2026-01-31T12:00Z','partial-cancel')
            """);
        source.update("""
            INSERT INTO trades_executed(executeid,requestid,askprice,quantity_executed,date_execution,executed_at_utc,
                fee,client_id,execution_currency,fee_currency,quantity_unit,contract_multiplier,product_convention)
            VALUES(80000,30000,100,4,'2026-01-31','2026-01-31T12:01Z',1,1,'GBP','USD','SHARE',1,'PRICE_PER_UNIT'),
                  (80001,30000,102,6,'2026-02-01','2026-02-01T12:01Z',1,1,'GBP','USD','SHARE',1,'PRICE_PER_UNIT')
            """);
        source.update("""
            INSERT INTO order_lifecycle_events(order_id,account_id,instrument_id,event_type,event_sequence,event_version,occurred_at_utc,correlation_id)
            VALUES(30000,1,1,'ORDER_SUBMITTED',1,1,'2026-01-31T12:00Z','partial-cancel'),
                  (30000,1,1,'ORDER_ACCEPTED',2,1,'2026-01-31T12:00Z','partial-cancel'),
                  (30000,1,1,'ORDER_CANCELLED',3,1,'2026-02-02T12:00Z','partial-cancel')
            """);
        etl.runFullLoad();
        var jan=(Map<String,Object>)analytics.read("overview",month("2026-01-01")).get("data");
        assertEquals(0L,jan.get("conflicting_outcome_orders"),"Partial fill then cancellation is a valid outcome");
        var feb=(Map<String,Object>)analytics.read("overview",month("2026-02-01")).get("data");
        assertEquals(2L,feb.get("fill_count"),"One boundary fill and the February partial fill");
        var partial=db.queryForMap("SELECT COUNT(*) AS fills,SUM(executed_quantity) AS quantity,SUM(execution_price*executed_quantity*contract_multiplier) AS amount FROM fill_facts WHERE requestid=30000");
        assertEquals(2L,partial.get("fills"));assertEquals(10L,partial.get("quantity"));
        assertEquals(0,new BigDecimal("1012").compareTo((BigDecimal)partial.get("amount")));
        assertEquals(List.of(1L,2L,3L),source.queryForList("SELECT event_sequence FROM order_lifecycle_events WHERE order_id=30000 ORDER BY occurred_at_utc,event_sequence",Long.class));
        String pinned=analytics.read("overview",month("2026-01-01")).get("datasetId").toString();etl.runFullLoad();
        var changed=assertThrows(ResponseStatusException.class,()->analytics.readPinned("instruments",month("2026-01-01"),pinned));assertEquals(409,changed.getStatusCode().value());
        // Default configuration must withhold segment rows, including retained reports containing them.
        var withheld=new AnalyticsService(db,context.getBean(tools.jackson.databind.ObjectMapper.class),false);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->withheld.read("client-segments",month("2026-01-01"))).getStatusCode().value());
        var filtered=new AnalyticsQuery(LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-31"),"UTC",null,null,"RETAIL",0,25,"fills","desc");
        assertEquals(403,assertThrows(ResponseStatusException.class,()->withheld.read("overview",filtered)).getStatusCode().value());
        String saved=db.queryForObject("SELECT report_id FROM analytics_reports WHERE version=1",String.class);
        assertEquals(403,assertThrows(ResponseStatusException.class,()->withheld.report(saved)).getStatusCode().value());

        // Two simultaneous deliveries must commit exactly one set of financial effects.
        source.update("INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,status,created_at_utc,correlation_id) VALUES(30001,1,1,'BUY',1,10,CURRENT_DATE,'PENDING',CURRENT_TIMESTAMP,'execution-race')");
        context.getBean(QuoteCache.class).upsert(QuoteSnapshot.from(new MarketQuote("ABC.L",BigDecimal.TEN,null,null,"race-fixture",Instant.now(),Instant.now(),"GBP","ASK")));
        BigDecimal before=source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class);
        var trading=context.getBean(TradingOrderService.class);var pool=Executors.newFixedThreadPool(2);var barrier=new CyclicBarrier(2);
        try {
            List<Future<Boolean>> races=new ArrayList<>();
            for(int i=0;i<2;i++) races.add(pool.submit(()->{barrier.await(10,TimeUnit.SECONDS);try {trading.executeOrder(30001);return true;}catch(IllegalArgumentException conflict){return false;}}));
            int successes=0;for(var result:races) if(result.get(20,TimeUnit.SECONDS)) successes++;
            assertEquals(1,successes);
        }finally {pool.shutdownNow();}
        assertEquals(1,source.queryForObject("SELECT COUNT(*) FROM trades_executed WHERE requestid=30001",Integer.class));
        assertEquals(1,source.queryForObject("SELECT COUNT(*) FROM cash_movements WHERE order_id=30001",Integer.class));
        assertEquals(0,before.subtract(new BigDecimal("11")).compareTo(source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class)));
        assertThrows(IllegalArgumentException.class,()->trading.cancelOrder(30001));
        // Every retry after success stays financially inert and retains lifecycle evidence.
        assertThrows(IllegalArgumentException.class,()->trading.executeOrder(30001));
        etl.runFullLoad();assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM fill_facts WHERE requestid=30001",Long.class));
        var cache=context.getBean(QuoteCache.class);
        cache.upsert(QuoteSnapshot.from(new MarketQuote("ABC.L",BigDecimal.TEN,null,null,"race-fixture",Instant.now(),Instant.now(),"GBP","ASK")));
        cache.upsert(QuoteSnapshot.from(new MarketQuote("ABC.N",BigDecimal.TEN,null,null,"race-fixture",Instant.now(),Instant.now(),"USD","ASK")));
        var submitPool=Executors.newFixedThreadPool(2);var submitBarrier=new CyclicBarrier(2);
        try {
            var first=submitPool.submit(()->{submitBarrier.await(10,TimeUnit.SECONDS);return trading.submitOrder(new com.neueda.leap.trading.api.dto.ProcessOrderRequest(1,null,com.neueda.leap.trading.domain.OrderSide.BUY,1,null,1));});
            var second=submitPool.submit(()->{submitBarrier.await(10,TimeUnit.SECONDS);return trading.submitOrder(new com.neueda.leap.trading.api.dto.ProcessOrderRequest(2,null,com.neueda.leap.trading.domain.OrderSide.BUY,1,null,2));});
            var one=first.get(20,TimeUnit.SECONDS);var two=second.get(20,TimeUnit.SECONDS);
            assertNotEquals(one.orderId(),two.orderId());assertNotEquals(one.tradeId(),two.tradeId());
            assertEquals(com.neueda.leap.trading.domain.OrderStatus.EXECUTED,one.status());assertEquals(com.neueda.leap.trading.domain.OrderStatus.EXECUTED,two.status());
        } finally {submitPool.shutdownNow();}
        etl.runFullLoad();
        var auth=context.getBean(AuthenticationService.class);
        var token=auth.login(new com.neueda.leap.trading.api.dto.LoginRequest("one","fixture-password")).accessToken();
        int port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
        var http=java.net.http.HttpClient.newHttpClient();
        long ordersBefore=source.queryForObject("SELECT COUNT(*) FROM trade_request",Long.class);
        var fractional=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/accounts/1/orders"))
            .header("Authorization","Bearer "+token).header("Content-Type","application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"instrumentId\":1,\"side\":\"BUY\",\"quantity\":1.5}")).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(400,fractional.statusCode(),fractional.body());assertEquals(ordersBefore,source.queryForObject("SELECT COUNT(*) FROM trade_request",Long.class));
        var forbidden=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/etl/run"))
            .header("Authorization","Bearer "+token).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403,forbidden.statusCode(),"Analyst must not trigger extraction against the trading store");
        var pii=http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"+port+"/api/admin/users/1"))
            .header("Authorization","Bearer "+token).GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
        assertEquals(403,pii.statusCode(),"Analyst cannot look up identifiable customer records");
        var staleIssue=new AnalyticsReportRequest(LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-31"),"UTC",null,null,null,"stale-issue",null,null,pinned);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("edge-analyst","",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_REPORTING"))));
        try {assertEquals(409,assertThrows(ResponseStatusException.class,()->analytics.issue(staleIssue)).getStatusCode().value());}
        finally {org.springframework.security.core.context.SecurityContextHolder.clearContext();}
        source.update("INSERT INTO instruments(instrumentid,ticker,instrumentname,quote_currency,quantity_unit,contract_multiplier,product_convention) VALUES(50000,'FAIL','Failure fixture','GBP','SHARE',1,'PRICE_PER_UNIT')");
        cache.upsert(QuoteSnapshot.from(new MarketQuote("FAIL",BigDecimal.TEN,null,null,"failure-fixture",Instant.now(),Instant.now(),"USD","ASK")));
        var accepted=trading.createOrder(new com.neueda.leap.trading.api.dto.CreateOrderRequest(1,"FAIL",com.neueda.leap.trading.domain.OrderSide.BUY,1,null,50000));
        BigDecimal failureBalance=source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class);
        assertThrows(IllegalArgumentException.class,()->trading.executeOrder(accepted.orderId()));
        assertEquals(failureBalance,source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class));
        assertEquals(0,source.queryForObject("SELECT COUNT(*) FROM trades_executed WHERE requestid=?",Integer.class,accepted.orderId()));
        assertTrue(source.queryForObject("SELECT reason FROM order_lifecycle_events WHERE order_id=? AND event_type='EXECUTION_FAILED'",String.class,accepted.orderId()).contains("currency"));
        assertEquals(List.of("ORDER_SUBMITTED","ORDER_ACCEPTED","EXECUTION_ATTEMPTED","EXECUTION_FAILED"),source.queryForList("SELECT event_type FROM order_lifecycle_events WHERE order_id=? ORDER BY event_sequence",String.class,accepted.orderId()));
        etl.runFullLoad();


    }
}
