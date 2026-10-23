package com.neueda.leap.trading;

import static org.junit.jupiter.api.Assertions.*;
import com.neueda.leap.trading.analytics.*;
import com.neueda.leap.trading.service.impl.*;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;

/** Independent expected arithmetic: GBP 1012+600; USD 50+3. Never computed by the report SQL. */
final class AnalyticsAssertions {
    @SuppressWarnings("unchecked")
    static void verify(ConfigurableApplicationContext context,JdbcTemplate source,JdbcTemplate reporting) throws Exception {
        source.update("INSERT INTO instruments(instrumentid,ticker,instrumentname) VALUES(3,'EURUSD','Euro Dollar'),(4,'BTC','Bitcoin')");
        source.update("""
            INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,status,created_at_utc,correlation_id)
            VALUES(6,1,1,'BUY',1,10,'2026-02-01','EXECUTED','2026-02-01T00:20Z','boundary'),
                  (7,3,3,'BUY',2,1.5,'2026-01-15','EXECUTED','2026-01-15T12:00Z','fx'),
                  (8,3,4,'SELL',3,200,'2026-01-15','EXECUTED','2026-01-15T12:00Z','crypto')
            """);
        source.update("""
            INSERT INTO trades_executed(executeid,requestid,askprice,quantity_executed,date_execution,executed_at_utc,
                fee,client_id,execution_currency,fee_currency,quantity_unit,contract_multiplier,product_convention)
            VALUES(5,6,10,1,'2026-02-01','2026-02-01T00:30Z',0,1,'GBP','GBP','SHARE',1,'PRICE_PER_UNIT'),
                  (6,7,1.5,2,'2026-01-15','2026-01-15T12:01Z',0.1,2,'USD','USD','FX_BASE_UNIT',1,'PRICE_PER_UNIT'),
                  (7,8,200,3,'2026-01-15','2026-01-15T12:02Z',2,2,'GBP','GBP','COIN',1,'PRICE_PER_UNIT')
            """);
        var etl=context.getBean(OltpToOlapEtlService.class); etl.runFullLoad();
        var analytics=context.getBean(AnalyticsService.class);
        var q=new AnalyticsQuery(LocalDate.parse("2026-01-01"),LocalDate.parse("2026-01-31"),"UTC",null,null,null,0,25,"fills","desc");
        var response=analytics.read("overview",q);var totals=(Map<String,Object>)response.get("data");
        assertEquals(5L,totals.get("fill_count"));assertEquals(4L,totals.get("executed_orders"));
        assertEquals(2L,totals.get("active_clients"),"Deduplicated across accounts");
        assertEquals(4L,totals.get("submitted_orders"));assertEquals(3L,totals.get("accepted_orders"));
        assertEquals(1L,totals.get("rejected_orders"));assertEquals(1L,totals.get("pending_orders"));
        assertEquals(0,new BigDecimal("25").compareTo((BigDecimal)totals.get("rejection_rate_percent")));
        var amounts=(List<Map<String,Object>>)totals.get("grossNotional");
        assertEquals("GBP",amounts.get(0).get("currency")); decimal("1612",amounts.get(0).get("amount"));
        assertEquals("USD",amounts.get(1).get("currency")); decimal("53",amounts.get(1).get("amount"));
        var fees=(List<Map<String,Object>>)totals.get("fees");decimal("4",fees.get(0).get("amount"));decimal("0.1",fees.get(1).get("amount"));
        decimal("-1014",((List<Map<String,Object>>)totals.get("netCash")).get(0).get("amount"));
        var instruments=(Map<String,Object>)analytics.read("instruments",q).get("data");
        assertEquals(4L,instruments.get("total"),"Units stay separate");
        var local=new AnalyticsQuery(q.from(),q.to(),"America/New_York",null,null,null,0,25,"fills","desc");
        assertEquals(6L,((Map<?,?>)analytics.read("overview",local).get("data")).get("fill_count"));
        assertEquals(3L,((Map<?,?>)analytics.read("client-segments",q).get("data")).get("total"));
        var empty=new AnalyticsQuery(LocalDate.parse("2024-01-01"),LocalDate.parse("2024-01-31"),"UTC",null,null,null,0,25,"fills","desc");
        var zero=(Map<?,?>)analytics.read("overview",empty).get("data");assertEquals(0L,zero.get("fill_count"));assertNull(zero.get("rejection_rate_percent"));
        assertThrows(IllegalArgumentException.class,()->new AnalyticsQuery(q.from(),q.to(),"invalid-zone",null,null,null,0,25,"fills","desc"));
        assertThrows(IllegalArgumentException.class,()->new AnalyticsQuery(q.from(),q.to(),"UTC",null,null,null,0,25,"fills; DROP TABLE users","desc"));
        assertThrows(IllegalArgumentException.class,()->new AnalyticsQuery(q.from(),q.to(),"UTC",null,null,null,-1,1000,"fills","desc"));
        var password=context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode("fixture-password");
        source.update("UPDATE users SET userhashedsaltedpassword=?",password);
        source.update("INSERT INTO roles(rolename) VALUES('ANALYST')");
        source.update("INSERT INTO user_roles(userid,roleid) SELECT 1,roleid FROM roles WHERE rolename='ANALYST'");
        var auth=context.getBean(AuthenticationService.class);
        String analyst=auth.login(new com.neueda.leap.trading.api.dto.LoginRequest("one","fixture-password")).accessToken();
        String client=auth.login(new com.neueda.leap.trading.api.dto.LoginRequest("two","fixture-password")).accessToken();
        int port=((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
        var http=java.net.http.HttpClient.newHttpClient();
        String url="http://localhost:"+port+"/api/analytics/overview?from=2026-01-01&to=2026-01-31";
        for(var expected:Map.of("",401,client,403,analyst,200).entrySet()) {
            var builder=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url));
            if(!expected.getKey().isEmpty()) builder.header("Authorization","Bearer "+expected.getKey());
            var result=http.send(builder.GET().build(),java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(expected.getValue(),result.statusCode(),result.body());
        }
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("analyst-one","",List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("PERM_REPORTING"))));
        try {
            var issue=new AnalyticsReportRequest(q.from(),q.to(),"UTC",null,null,null,"issue-one",null,null);
            var issued=analytics.issue(issue);
            assertEquals(issued.get("reportId"),analytics.issue(issue).get("reportId"));
            assertThrows(RuntimeException.class,()->reporting.update("UPDATE analytics_reports SET version=2"));
            source.update("UPDATE trades_executed SET askprice=103 WHERE executeid=2");etl.runFullLoad();
            var amended=analytics.issue(new AnalyticsReportRequest(q.from(),q.to(),"UTC",null,null,null,"amend-one",issued.get("reportId").toString(),"Corrected source execution evidence"));
            assertEquals(2,amended.get("version"));
            assertEquals(issued.get("snapshot"),analytics.report(issued.get("reportId").toString()).get("snapshot"));
            assertNotEquals(((Map<?,?>)issued.get("snapshot")).get("datasetId"),((Map<?,?>)amended.get("snapshot")).get("datasetId"));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
    private static void decimal(String expected,Object actual) { assertEquals(0,new BigDecimal(expected).compareTo((BigDecimal)actual)); }
}
