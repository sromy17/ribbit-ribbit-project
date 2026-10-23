package com.neueda.leap.trading;

import com.neueda.leap.Main;
import com.neueda.leap.trading.api.*;
import com.neueda.leap.trading.domain.*;
import com.neueda.leap.trading.service.impl.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Requires a fresh disposable PostgreSQL database; never point at a real database. */
@EnabledIfEnvironmentVariable(named = "ADMIN_PG_URL", matches = ".+")
class AdminPostgresTest {
    private ConfigurableApplicationContext start() {
        return new SpringApplicationBuilder(Main.class).run(
            "--server.port=0", "--spring.config.location=file:src/main/resources/application.yml", "--spring.datasource.url=" + System.getenv("ADMIN_PG_URL"),
            "--spring.datasource.username=postgres", "--spring.datasource.password=admin-test-only",
            "--spring.jpa.hibernate.ddl-auto=update", "--spring.jpa.show-sql=false",
            "--app.datasource.olap.url=" + System.getenv("ADMIN_PG_URL").replace("admin_verification", "admin_reporting"),
            "--app.datasource.olap.username=postgres", "--app.datasource.olap.password=admin-test-only",
            "--market-data.kafka.enabled=false", "--market-data.execution.kafka.enabled=false", "--etl.schedule.cron=-");
    }
    private void actor(int id) {
        Jwt jwt = Jwt.withTokenValue("test").header("alg", "HS256").claim("uid", id).subject("test-" + id).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
            List.of(new SimpleGrantedAuthority("PERM_DISPUTE_WORK"), new SimpleGrantedAuthority("PERM_DISPUTE_READ"),
                new SimpleGrantedAuthority("PERM_DISPUTE_SUPERVISOR"), new SimpleGrantedAuthority("PERM_ACCOUNT_READ"),
                new SimpleGrantedAuthority("PERM_ORDER_INVESTIGATION"))));
    }
    @Test
    void concurrentClaimsReplayRestartAndReportingMeasurements() throws Exception {
        Long caseId; Long finalVersion;
        try (var context = start()) {
            var db = context.getBean("oltpJdbcTemplate", JdbcTemplate.class);
            var reporting = context.getBean("olapJdbcTemplate", JdbcTemplate.class);
            try (var connection = reporting.getDataSource().getConnection()) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource("olap.sql"));
            }
            db.execute("CREATE TABLE IF NOT EXISTS employees(employeeid INT PRIMARY KEY, employeeusername VARCHAR(50), employeeroles VARCHAR(20))");
            db.execute("CREATE TABLE IF NOT EXISTS disputed_orders(disputeid INT PRIMARY KEY, requestid INT, employeeid INT, status VARCHAR(20), category VARCHAR(20), reason VARCHAR(75))");
            db.execute("CREATE TABLE IF NOT EXISTS canceled_order(cancel_id INT PRIMARY KEY, request_id INT, reason VARCHAR(50), cancel_date DATE)");
            db.execute("CREATE TABLE IF NOT EXISTS support_ticket(ticket_id INT PRIMARY KEY, accountid INT, reason VARCHAR(75), created_at TIMESTAMP, status VARCHAR(20), assigned_to INT, closed_at TIMESTAMP)");
            db.update("INSERT INTO users(userid,username,salt,userhashedsaltedpassword) VALUES (910001,'fixture-user','salt','hash')");
            db.update("INSERT INTO accounts(accountid,userid,availablefunds,creationdate,account_type) VALUES (910001,910001,1000,CURRENT_DATE,'TRADING')");
            db.update("INSERT INTO instruments(instrumentid,ticker,instrumentname,status) VALUES (910001,'FIXTURE','Fixture','ACTIVE')");
            db.update("INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,status,created_at_utc,correlation_id) VALUES (910001,910001,910001,'BUY',1,10,CURRENT_DATE,'EXECUTED',CURRENT_TIMESTAMP,'fixture-correlation')");
            db.update("INSERT INTO trades_executed(executeid,requestid,askprice,date_execution,quantity_executed,fee,executed_at_utc) VALUES (910001,910001,10,CURRENT_DATE,1,0,CURRENT_TIMESTAMP)");
            // A large bounded history fixture to exercise actual PostgreSQL pagination.
            db.update("INSERT INTO trade_request(requestid,accountid,instrumentid,side,quantity,bidprice,date_request,status,created_at_utc,correlation_id) SELECT n,910001,910001,'BUY',1,10,CURRENT_DATE,'PENDING',CURRENT_TIMESTAMP,'history-' || n FROM generate_series(920000,929999) n");
            var password = context.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode("fixture-password");
            db.update("UPDATE users SET userhashedsaltedpassword=? WHERE userid=910001", password);
            db.update("INSERT INTO users(userid,username,salt,userhashedsaltedpassword) VALUES (910002,'fixture-admin','salt',?)", password);
            db.update("INSERT INTO roles(rolename) VALUES ('PLATFORM_ADMINISTRATOR')");
            db.update("INSERT INTO user_roles(userid,roleid) SELECT 910002,roleid FROM roles WHERE rolename='PLATFORM_ADMINISTRATOR'");
            var login = context.getBean(AuthenticationService.class);
            String clientToken = login.login(new com.neueda.leap.trading.api.dto.LoginRequest("fixture-user", "fixture-password")).accessToken();
            String adminToken = login.login(new com.neueda.leap.trading.api.dto.LoginRequest("fixture-admin", "fixture-password")).accessToken();
            var client = java.net.http.HttpClient.newHttpClient();
            int port = ((org.springframework.boot.web.server.context.WebServerApplicationContext)context).getWebServer().getPort();
            var uri = java.net.URI.create("http://localhost:" + port + "/api/admin/accounts/910001/history");
            assertEquals(401, client.send(java.net.http.HttpRequest.newBuilder(uri).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(403, client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + clientToken).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + adminToken).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            // The merged customer/staff auth providers share this hashed token schema.
            var rotating = login.login(new com.neueda.leap.trading.api.dto.LoginRequest("fixture-admin", "fixture-password"));
            var rotated = login.refresh(rotating.refreshToken());
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> login.refresh(rotating.refreshToken()));
            assertEquals(401, client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + rotating.accessToken()).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + rotated.accessToken()).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            login.logout(rotated.accessToken());
            assertEquals(401, client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + rotated.accessToken()).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> login.refresh(rotated.refreshToken()));
            var walletUri = java.net.URI.create("http://localhost:" + port + "/accounts/910001/wallet/banks");
            assertEquals(200, client.send(java.net.http.HttpRequest.newBuilder(walletUri).header("Authorization", "Bearer " + clientToken).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(403, client.send(java.net.http.HttpRequest.newBuilder(walletUri).header("Authorization", "Bearer " + adminToken).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode(), "Staff cannot operate another user's banking wallet");
            var cases = context.getBean(DisputeCaseService.class);
            var submission = new DisputeSubmissionRequest("TRADE", "910001", "Investigate fee", "Fee concern", "retry-1", true);
            actor(910001);
            var created = cases.submit(submission); caseId = created.getCaseId();
            assertEquals(caseId, cases.submit(submission).getCaseId(), "Retry must not create another case");
            assertThrows(RuntimeException.class, () -> cases.submit(new DisputeSubmissionRequest("TRADE", "910001", "Changed", "Fee concern", "retry-1", true)));
            actor(910002);
            assertThrows(RuntimeException.class, () -> cases.submit(submission), "Another customer cannot replay somebody else's key");
            var pool = Executors.newFixedThreadPool(2);
            var barrier = new CyclicBarrier(2);
            var outcomes = new ArrayList<Future<Boolean>>();
            final Long raceId = caseId; final Long version = created.getVersion();
            for (int id : List.of(910002, 910003)) outcomes.add(pool.submit(() -> {
                actor(id); barrier.await(10, TimeUnit.SECONDS);
                try { cases.claim(raceId, new DisputeTransitionRequest(version, null, null, null)); return true; }
                catch (RuntimeException conflict) { return false; }
                finally { SecurityContextHolder.clearContext(); }
            }));
            int won = 0; for (var future : outcomes) if (future.get(20, TimeUnit.SECONDS)) won++;
            pool.shutdownNow(); assertEquals(1, won, "Only one concurrent claimant may win");
            var claimed = cases.get(caseId); actor(claimed.getAssignedStaffUserId());
            var accepted = cases.accept(caseId, new DisputeTransitionRequest(claimed.getVersion(), null, null, null));
            var noted = cases.note(caseId, new DisputeNoteRequest("Investigated", accepted.getVersion()));
            var resolved = cases.resolve(caseId, new DisputeTransitionRequest(noted.getVersion(), "No financial error", "NOT_UPHELD", null));
            finalVersion = resolved.getVersion();
            assertThrows(RuntimeException.class, () -> cases.release(raceId, new DisputeTransitionRequest(finalVersion, "reopen", null, null)));
            assertEquals(5, cases.events(caseId).size());
            assertEquals(1L, db.queryForObject("SELECT count(*) FROM dispute_cases", Long.class));
            actor(910001);
            Long secondCase = cases.submit(new DisputeSubmissionRequest("TRADE", "910001", "New allegation", "New reason", "new-2", false)).getCaseId();
            assertNotEquals(caseId, secondCase);
            var reads = context.getBean(AdminInvestigationController.class);
            actor(910002);
            assertEquals(25, ((List<?>)reads.history(910001, "orders", 0, 25).get("items")).size());
            assertThrows(IllegalArgumentException.class, () -> reads.history(910001, "orders", -1, 25));
            var cache = context.getBean(com.neueda.leap.trading.service.impl.marketdata.QuoteCache.class);
            cache.upsert(new com.neueda.leap.trading.service.impl.marketdata.QuoteSnapshot("FIXTURE", new java.math.BigDecimal("10"), System.currentTimeMillis(), "integration-fixture"));
            var trading = context.getBean(TradingOrderService.class);
            trading.executeOrder(920000);
            var balanceAfter = db.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=910001", java.math.BigDecimal.class);
            assertThrows(IllegalArgumentException.class, () -> trading.executeOrder(920000));
            assertEquals(balanceAfter, db.queryForObject("SELECT availablefunds FROM accounts WHERE accountid=910001", java.math.BigDecimal.class));
            assertEquals(1, db.queryForObject("SELECT count(*) FROM cash_movements WHERE order_id=920000", Integer.class));
            assertEquals(1, db.queryForObject("SELECT count(*) FROM holding_movements WHERE order_id=920000", Integer.class));
            var etl = context.getBean(OltpToOlapEtlService.class);
            long refreshStarted = System.nanoTime();
            etl.runFullLoad();
            long refreshMs = (System.nanoTime() - refreshStarted) / 1_000_000;
            etl.runFullLoad();
            assertEquals(10001, reporting.queryForObject("SELECT count(*) FROM order_facts", Integer.class));
            assertEquals(2, reporting.queryForObject("SELECT count(*) FROM fill_facts", Integer.class));
            var reportReads = context.getBean(AdminReadService.class);
            assertEquals(25, ((List<?>)reportReads.orders(null, null, null, LocalDate.now().minusDays(1), LocalDate.now(), 1, 25, "requestid", "asc").get("items")).size());
            // Inject a load failure: the last published tables must survive rollback.
            db.execute("ALTER TABLE support_ticket RENAME TO support_ticket_unavailable");
            assertThrows(RuntimeException.class, etl::runFullLoad);
            db.execute("ALTER TABLE support_ticket_unavailable RENAME TO support_ticket");
            assertEquals(10001, reporting.queryForObject("SELECT count(*) FROM order_facts", Integer.class));
            assertEquals("FAILED", reportReads.reportingStatus().get("status"));
            assertEquals(false, reportReads.reportingStatus().get("stale"));
            long[] latency = new long[40];
            var queryPool = Executors.newSingleThreadExecutor();
            var background = queryPool.submit(() -> {
                long[] times = new long[40];
                for (int i=0;i<40;i++) { long t=System.nanoTime(); reportReads.activity(LocalDate.now().minusDays(1), LocalDate.now(), null); times[i]=(System.nanoTime()-t)/1_000_000; }
                Arrays.sort(times); return times;
            });
            for (int i=0;i<latency.length;i++) { long t=System.nanoTime(); reads.history(910001,"orders",i % 5,25); latency[i]=(System.nanoTime()-t)/1_000_000; }
            long[] reportLatency = background.get(30, TimeUnit.SECONDS); queryPool.shutdownNow();
            Arrays.sort(latency);
            Files.createDirectories(Path.of("target/admin-verification"));
            Files.writeString(Path.of("target/admin-verification/performance.txt"), "Fixture: PostgreSQL; 1 account; 10001 orders; 2 fills; 40 operational reads concurrent with 40 report aggregates.\nHistory p95 ms: " + latency[37] + "\nReport p95 ms: " + reportLatency[37] + "\nRefresh ms: " + refreshMs + "\nFreshness: source watermark within 900 seconds; asserted.\nApproval: NOT APPROVED; local fixture only.\n");
            assertTrue(latency[37] < 2000, "Local bounded history p95 target");
            assertTrue(reportLatency[37] < 5000, "Local report p95 target");
            actor(910002);
            var second = cases.claim(secondCase, new DisputeTransitionRequest(cases.get(secondCase).getVersion(), null, null, null));
            assertThrows(IllegalArgumentException.class, () -> cases.reassign(secondCase, new DisputeTransitionRequest(second.getVersion(), "Invalid target", null, 999999)));
            context.getBean(StaffProvisioningService.class).revoke("fixture-admin", "PLATFORM_ADMINISTRATOR");
            assertEquals(DisputeCaseStatus.OPEN, cases.get(secondCase).getStatus());
            assertNull(cases.get(secondCase).getAssignedStaffUserId());
            assertEquals(DisputeCaseStatus.RESOLVED, cases.get(caseId).getStatus());
            assertEquals(0, db.queryForObject("SELECT count(*) FROM user_roles WHERE userid=910002", Integer.class));
            assertEquals(401, client.send(java.net.http.HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + adminToken).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally { SecurityContextHolder.clearContext(); }
        // A new application and connection pool must read the same durable state.
        try (var restarted = start()) {
            actor(910002);
            var cases = restarted.getBean(DisputeCaseService.class);
            assertEquals(DisputeCaseStatus.RESOLVED, cases.get(caseId).getStatus());
            assertEquals(finalVersion, cases.get(caseId).getVersion());
            assertEquals(5, cases.events(caseId).size());
            var db = restarted.getBean("oltpJdbcTemplate", JdbcTemplate.class);
            assertEquals(1, db.queryForObject("SELECT count(*) FROM trades_executed WHERE requestid=920000", Integer.class));
            assertEquals(1, db.queryForObject("SELECT quantity FROM holdings WHERE accountid=910001", Integer.class));
            assertThrows(IllegalArgumentException.class, () -> restarted.getBean(TradingOrderService.class).executeOrder(920000));
            actor(910001);
            assertEquals(caseId, cases.submit(new DisputeSubmissionRequest("TRADE", "910001", "Investigate fee", "Fee concern", "retry-1", true)).getCaseId());
        } finally { SecurityContextHolder.clearContext(); }
    }
}
