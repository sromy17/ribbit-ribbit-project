package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.trading.analytics.*;
import com.neueda.leap.trading.service.impl.*;
import com.neueda.leap.trading.service.impl.marketdata.*;
import com.neueda.leap.trading.service.contract.MarketQuote;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import tools.jackson.databind.ObjectMapper;

/** Measured local mixed workload, not a production capacity certification. */
final class PersonaLoadAssertions {
    @SuppressWarnings("unchecked")
    static void verify(ConfigurableApplicationContext context,JdbcTemplate source,JdbcTemplate reporting) throws Exception {
        var analytics=context.getBean(AnalyticsService.class);var etl=context.getBean(OltpToOlapEtlService.class);
        var cache=context.getBean(QuoteCache.class);var json=context.getBean(ObjectMapper.class);
        var auth=context.getBean(AuthenticationService.class);
        source.update("INSERT INTO users(userid,username,salt,user_hashed_salted_password) SELECT 3,'david','salt',user_hashed_salted_password FROM users WHERE userid=1");
        source.update("INSERT INTO roles(rolename) VALUES('OPERATIONS')");
        source.update("INSERT INTO user_roles(userid,roleid) SELECT 3,roleid FROM roles WHERE rolename='OPERATIONS'");
        String joanna=auth.login(new com.neueda.leap.trading.api.dto.LoginRequest("one","fixture-password")).accessToken();
        String david=auth.login(new com.neueda.leap.trading.api.dto.LoginRequest("david","fixture-password")).accessToken();
        String priya=joanna; // Fixture client one also holds ANALYST; production roles remain server-controlled.
        int port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
        String base="http://localhost:"+port;var http=HttpClient.newHttpClient();
        LocalDate day=LocalDate.now(ZoneOffset.UTC);
        var query=new AnalyticsQuery(day.withDayOfMonth(1),day.withDayOfMonth(day.lengthOfMonth()),"UTC",1,null,null,0,25,"fills","desc");
        // Add history to exercise indexed aggregates while new orders are committed.
        source.update("INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,status,created_at_utc,correlation_id) SELECT n,1,1,'BUY',1,10,CURRENT_DATE,'PENDING',CURRENT_TIMESTAMP,'load-'||n FROM generate_series(10000,19999) n");
        etl.runFullLoad();
        var executor=Executors.newFixedThreadPool(3);var start=new CountDownLatch(1);var stop=new java.util.concurrent.atomic.AtomicBoolean();
        var errors=new java.util.concurrent.ConcurrentLinkedQueue<Throwable>();
        var lockSamples=new java.util.concurrent.atomic.AtomicInteger();var lockWaitSamples=new java.util.concurrent.atomic.AtomicInteger();
        var reports=executor.submit(()-> {try{start.await();for(int i=0;i<20;i++) analytics.read("overview",query);}catch(Throwable e){errors.add(e);} });
        var refresh=executor.submit(()-> {try{start.await();for(int i=0;i<3;i++) etl.runFullLoad();}catch(Throwable e){errors.add(e);} });
        var locks=executor.submit(()->{try{start.await();while(!stop.get()) {
            lockSamples.incrementAndGet();
            lockWaitSamples.addAndGet(source.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock'",Integer.class));
            Thread.sleep(10);
        }}catch(Throwable e){errors.add(e);} });
        var latency=new long[20];var ids=new ArrayList<Integer>();long begin=System.nanoTime();start.countDown();
        try {
            for(int i=0;i<20;i++) {
                cache.upsert(QuoteSnapshot.from(new MarketQuote("ABC.L",BigDecimal.TEN,null,null,"load-fixture",Instant.now(),Instant.now(),"GBP","ASK")));
                long t=System.nanoTime();var result=send(http,base+"/accounts/1/orders",joanna,"{\"instrumentId\":1,\"side\":\"BUY\",\"quantity\":1}");
                latency[i]=System.nanoTime()-t;assertEquals(201,result.statusCode(),result.body());
                ids.add(((Number)json.readValue(result.body(),Map.class).get("orderId")).intValue());
            }
            reports.get(60,TimeUnit.SECONDS);refresh.get(60,TimeUnit.SECONDS);
        } finally {stop.set(true);locks.get(10,TimeUnit.SECONDS);executor.shutdownNow();}
        assertTrue(errors.isEmpty(),errors.toString());
        long elapsed=System.nanoTime()-begin;Arrays.sort(latency);
        int last=ids.getLast();
        var evidence=get(http,base+"/api/admin/investigations/"+last,david);assertEquals(200,evidence.statusCode(),evidence.body());
        var investigation=json.readValue(evidence.body(),Map.class);
        var types=((List<Map<String,Object>>)investigation.get("events")).stream().map(e->e.get("event_type")).toList();
        assertTrue(types.containsAll(List.of("ORDER_SUBMITTED","ORDER_ACCEPTED","EXECUTION_ATTEMPTED","PRICING_DECISION","FILL_COMMITTED")),types.toString());
        assertEquals(1,((List<?>)investigation.get("fills")).size());
        etl.runFullLoad();
        String path="/api/analytics/overview?from="+query.from()+"&to="+query.to()+"&instrumentId=1";
        var result=get(http,base+path,priya);assertEquals(200,result.statusCode(),result.body());
        var overview=(Map<?,?>)json.readValue(result.body(),Map.class).get("data");assertEquals(20,((Number)overview.get("fill_count")).intValue());
        assertEquals(20,((Number)overview.get("executed_orders")).intValue());assertEquals(1,((Number)overview.get("active_clients")).intValue());
        // Reporting schema failure must not change trading availability.
        reporting.execute("ALTER TABLE fill_facts RENAME TO fill_facts_unavailable");
        try {
            assertEquals(503,get(http,base+path,priya).statusCode());
            cache.upsert(QuoteSnapshot.from(new MarketQuote("ABC.L",BigDecimal.TEN,null,null,"load-fixture",Instant.now(),Instant.now(),"GBP","ASK")));
            assertEquals(201,send(http,base+"/accounts/1/orders",joanna,"{\"instrumentId\":1,\"side\":\"BUY\",\"quantity\":1}").statusCode());
        }finally {reporting.execute("ALTER TABLE fill_facts_unavailable RENAME TO fill_facts");}
        var before=source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class);
        assertThrows(IllegalArgumentException.class,()->context.getBean(TradingOrderService.class).executeOrder(last));
        assertEquals(before,source.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=1",BigDecimal.class));
        assertEquals(1,source.queryForObject("SELECT COUNT(*) FROM trades_executed WHERE requestid=?",Integer.class,last));
        etl.runFullLoad();
        Instant cutoff=reporting.queryForObject("SELECT source_watermark FROM etl_refresh_runs WHERE status='SUCCEEDED' ORDER BY published_at DESC LIMIT 1",java.sql.Timestamp.class).toInstant();
        long lag=Duration.between(cutoff,Instant.now()).toMillis();
        Long fillToPublication=reporting.queryForObject("""
            SELECT ROUND(EXTRACT(EPOCH FROM (r.published_at-f.executed_at_utc))*1000)::bigint
            FROM fill_facts f CROSS JOIN
              (SELECT published_at FROM etl_refresh_runs WHERE status='SUCCEEDED' ORDER BY published_at DESC LIMIT 1) r
            WHERE f.requestid=(SELECT MAX(requestid) FROM fill_facts)
            """,Long.class);
        Files.createDirectories(Path.of("target/analytics-verification"));
        Files.writeString(Path.of("target/analytics-verification/mixed-load.txt"),
            "LOCAL FIXTURE ONLY; stakeholder limits NOT APPROVED\n10000 historical orders; 20 sequential HTTP submissions/executions concurrent with 20 analytics service queries and 3 refreshes.\n"+
            "Order end-to-end p50 ms: "+latency[9]/1_000_000.0+"\nOrder p95 ms: "+latency[18]/1_000_000.0+
            "\nMixed workload throughput orders/sec: "+20/(elapsed/1_000_000_000.0)+"\nOLTP lock-wait samples: "+lockWaitSamples+" of "+lockSamples+
            "\nSource snapshot age after publication ms: "+lag+"\nNewest fill to final publication lag ms: "+fillToPublication+"\nIsolation: analytics failure returned 503 while an order returned 201. Replay left balances and fill count unchanged.\n");
    }
    private static HttpResponse<String> get(HttpClient c,String url,String token)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    private static HttpResponse<String> send(HttpClient c,String url,String token,String body)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(url)).header("Authorization","Bearer "+token).header("Content-Type","application/json").timeout(Duration.ofSeconds(20)).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
}
