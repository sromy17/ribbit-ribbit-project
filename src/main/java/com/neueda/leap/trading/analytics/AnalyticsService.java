package com.neueda.leap.trading.analytics;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** All reads and report storage use ONLY the reporting datasource. No OLTP fallback. */
@Service
public class AnalyticsService {
    public static final String METRIC_VERSION = "reporting-metric-contract-v2";
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate sql;
    private final ObjectMapper json;
    public AnalyticsService(@Qualifier("olapJdbcTemplate") JdbcTemplate db, ObjectMapper json) {
        this.db=db; this.sql=new NamedParameterJdbcTemplate(db); this.json=json;
    }
    private void bound() {
        db.execute("SET LOCAL statement_timeout = '5s'");
        db.execute("SET LOCAL lock_timeout = '3s'");
        // Repeatable-read plus MVCC-safe ETL replacement pins the response without blocking publication.
    }
    @Transactional(transactionManager="olapTransactionManager", readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=15)
    public Map<String,Object> read(String kind, AnalyticsQuery q) {
        bound();
        var meta=metadata(q);
        Object data = switch(kind) {
            case "overview" -> overview(q);
            case "activity", "instruments", "client-segments" -> groups(q,kind,false);
            default -> throw new IllegalArgumentException("Unknown analytics view");
        };
        if(kind.equals("overview")) outcomeCoverage(meta,cast(data));
        meta.put("data",data);
        return meta;
    }
    @Transactional(transactionManager="olapTransactionManager", readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=15)
    public Map<String,Object> status() {
        bound();
        var rows=db.queryForList("SELECT run_id, source_watermark, published_at FROM etl_refresh_runs WHERE status='SUCCEEDED' ORDER BY published_at DESC,run_id DESC LIMIT 1");
        var latest=db.queryForList("SELECT status, started_at FROM etl_refresh_runs ORDER BY started_at DESC,run_id DESC LIMIT 1");
        var result=new LinkedHashMap<String,Object>();
        result.put("available",!rows.isEmpty()); result.put("metricVersion",METRIC_VERSION);
        result.put("latestRefreshStatus",latest.isEmpty()?"UNAVAILABLE":latest.get(0).get("status"));
        if (!rows.isEmpty()) result.putAll(publication(rows.get(0)));
        return result;
    }
    private Map<String,Object> publication(Map<String,Object> row) {
        var result=new LinkedHashMap<String,Object>();
        result.put("datasetId",row.get("run_id"));
        result.put("sourceCutoff",instant(row.get("source_watermark")));
        result.put("publishedAt",instant(row.get("published_at")));
        Instant cutoff=((Timestamp)row.get("source_watermark")).toInstant();
        long lag=Math.max(0,java.time.Duration.between(cutoff,Instant.now()).getSeconds());
        result.put("ageSeconds",lag); result.put("stale",lag>900); result.put("staleThresholdSeconds",900);
        return result;
    }
    private String instant(Object value) { return value instanceof Timestamp t?t.toInstant().toString():String.valueOf(value); }
    private Map<String,Object> metadata(AnalyticsQuery q) {
        var rows=db.queryForList("SELECT run_id,source_watermark,published_at FROM etl_refresh_runs WHERE status='SUCCEEDED' ORDER BY published_at DESC,run_id DESC LIMIT 1");
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"No published analytics dataset");
        var m=new LinkedHashMap<>(publication(rows.get(0)));
        m.put("metricVersion",METRIC_VERSION); m.put("definitionStatus","PROPOSED");
        m.put("period",Map.of("from",q.from().toString(),"to",q.to().toString(),"timezone",q.timezone(),"startInclusive",q.start().toString(),"endExclusive",q.end().toString()));
        m.put("filters",filters(q));
        var coverage=one(q,"""
            SELECT (SELECT COUNT(*) FROM fills WHERE executed_at_utc IS NULL) AS unassigned_fill_time,
              (SELECT COUNT(*) FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end AND client_id IS NULL) AS unattributed_fills,
              (SELECT COUNT(*) FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end AND NOT valued) AS unvalued_fills,
              (SELECT COUNT(*) FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end AND quantity_unit IS NULL) AS unknown_quantity_unit,
              (SELECT COUNT(*) FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end AND fee_currency IS NULL) AS unknown_fee_currency,
              (SELECT COUNT(*) FROM orders o WHERE NOT EXISTS (SELECT 1 FROM lifecycle_event_facts e WHERE e.requestid=o.requestid AND e.event_type='ORDER_SUBMITTED')) AS missing_submission_evidence,
              (SELECT COUNT(*) FROM cash WHERE occurred_at_utc >= :start AND occurred_at_utc < :end AND (currency IS NULL OR currency_source IS NULL)) AS unverified_cash_currency,
              (SELECT COUNT(*) FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end AND segment_code='UNKNOWN') AS unknown_segment_fills
            """);
        m.put("sourceCoverage",coverage);
        var refresh=db.queryForList("SELECT status FROM etl_refresh_runs ORDER BY started_at DESC,run_id DESC LIMIT 1");
        String refreshStatus=refresh.isEmpty()?"UNAVAILABLE":refresh.get(0).get("status").toString();
        m.put("latestRefreshStatus",refreshStatus);
        var warnings=new ArrayList<String>();
        if(refreshStatus.equals("FAILED")) warnings.add("Latest refresh failed; this is the last successfully published dataset.");
        warnings.add("Metric definitions and segment source/values await stakeholder approval.");
        warnings.add("Source cutoff identifies the committed extraction snapshot; it does not certify upstream completeness.");
        warnings.add("Instrument attributes are current metadata; fill currencies, units and conventions are execution snapshots.");
        if(Boolean.TRUE.equals(m.get("stale"))) warnings.add("Dataset is stale against the provisional 900-second threshold.");
        if(q.end().isAfter(((Timestamp)rows.get(0).get("source_watermark")).toInstant())) warnings.add("The selected period extends beyond the dataset cutoff.");
        boolean incomplete=q.end().isAfter(((Timestamp)rows.get(0).get("source_watermark")).toInstant()) || coverage.values().stream().anyMatch(v->v instanceof Number n && n.longValue()>0);
        if(incomplete) warnings.add("Evidence is incomplete. Known subtotals exclude unvalued amounts; unknown-time counts cover the selected dataset, not an inferred period.");
        m.put("completeness",incomplete?"INCOMPLETE":"KNOWN_EVIDENCE");
        m.put("warnings",warnings); return m;
    }
    private void outcomeCoverage(Map<String,Object> metadata,Map<String,Object> totals) {
        var coverage=cast(metadata.get("sourceCoverage"));
        coverage.put("unknown_outcome_orders",totals.get("unknown_outcome_orders"));
        coverage.put("conflicting_outcome_orders",totals.get("conflicting_outcome_orders"));
        if(((Number)totals.get("unknown_outcome_orders")).longValue()>0 || ((Number)totals.get("conflicting_outcome_orders")).longValue()>0) {
            metadata.put("completeness","INCOMPLETE");
            @SuppressWarnings("unchecked") var warnings=(List<String>)metadata.get("warnings");
            warnings.add("Submission-cohort outcomes contain unknown or conflicting evidence; rejection-rate completeness is limited.");
        }
    }
    private Map<String,Object> filters(AnalyticsQuery q) {
        var f=new LinkedHashMap<String,Object>(); f.put("instrumentId",q.instrumentId());f.put("side",q.side());f.put("segment",q.segment()); return f;
    }
    private Map<String,Object> params(AnalyticsQuery q) {
        var p=new HashMap<String,Object>(); p.put("start",Timestamp.from(q.start()));p.put("end",Timestamp.from(q.end()));
        p.put("zone",q.timezone());p.put("instrument",q.instrumentId());p.put("side",q.side());p.put("segment",q.segment());
        p.put("limit",q.size()); p.put("offset",(long)q.page()*q.size()); return p;
    }
    private String segment(String client,String time) {
        return "COALESCE((SELECT s.segment_code FROM client_segment_history s WHERE s.client_id="+client+
            " AND s.effective_from_utc <= "+time+" AND (s.effective_to_utc IS NULL OR "+time+" < s.effective_to_utc)), 'UNKNOWN')";
    }
    private String base(AnalyticsQuery q) {
        String filter=(q.instrumentId()==null?"":" AND o.instrumentid=:instrument")+(q.side()==null?"":" AND o.side=:side");
        String seg=q.segment()==null?"":" WHERE segment_code=:segment";
        return "WITH cutoff AS (SELECT source_watermark FROM etl_refresh_runs WHERE status='SUCCEEDED' ORDER BY published_at DESC,run_id DESC LIMIT 1), orders AS (SELECT o.* FROM order_facts o WHERE 1=1"+filter+"), "+
            "raw_fills AS (SELECT f.*, o.side, i.ticker, "+segment("f.client_id","f.executed_at_utc")+" AS segment_code, "+
            "(f.execution_currency IS NOT NULL AND f.contract_multiplier > 0 AND f.product_convention='PRICE_PER_UNIT') IS TRUE AS valued "+
            "FROM fill_facts f JOIN orders o ON o.requestid=f.requestid LEFT JOIN instruments_info i ON i.instrumentid=f.instrumentid WHERE f.executed_at_utc IS NULL OR f.executed_at_utc <= (SELECT source_watermark FROM cutoff)), "+
            "fills AS (SELECT * FROM raw_fills"+seg+"), "+
            "raw_events AS (SELECT e.*, o.userid, o.order_status, "+segment("o.userid","e.occurred_at_utc")+" AS segment_code FROM lifecycle_event_facts e JOIN orders o ON o.requestid=e.requestid WHERE e.occurred_at_utc <= (SELECT source_watermark FROM cutoff)), "+
            "events AS (SELECT * FROM raw_events"+seg+"), "+
            "raw_cash AS (SELECT c.*, "+segment("o.userid","c.occurred_at_utc")+" AS segment_code FROM cash_movement_facts c JOIN orders o ON o.requestid=c.requestid WHERE c.occurred_at_utc <= (SELECT source_watermark FROM cutoff)), "+
            "cash AS (SELECT * FROM raw_cash"+seg+") ";
    }
    private Map<String,Object> one(AnalyticsQuery q,String query) { return sql.queryForMap(base(q)+query,params(q)); }
    private List<Map<String,Object>> list(AnalyticsQuery q,String query) { return sql.queryForList(base(q)+query,params(q)); }
    private Map<String,Object> overview(AnalyticsQuery q) {
        var r=one(q,"""
            SELECT COUNT(*) AS fill_count, COUNT(DISTINCT requestid) AS executed_orders,
                COUNT(DISTINCT client_id) AS active_clients,
                COUNT(*) FILTER (WHERE side='BUY') AS buy_fills,
                COUNT(*) FILTER (WHERE side='SELL') AS sell_fills
            FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end
            """);
        r.putAll(one(q,"""
            SELECT COUNT(DISTINCT requestid) FILTER (WHERE event_type='ORDER_SUBMITTED') AS submitted_orders,
                COUNT(DISTINCT requestid) FILTER (WHERE event_type='ORDER_ACCEPTED') AS accepted_orders
            FROM events WHERE occurred_at_utc >= :start AND occurred_at_utc < :end
            """));
        var cohort=one(q,"""
            , cohort AS (SELECT DISTINCT requestid,order_status FROM events WHERE event_type='ORDER_SUBMITTED'
                AND occurred_at_utc >= :start AND occurred_at_utc < :end),
            outcomes AS (SELECT c.*, EXISTS (SELECT 1 FROM lifecycle_event_facts r WHERE r.requestid=c.requestid
                    AND r.event_type='VALIDATION_REJECTED' AND r.occurred_at_utc <= (SELECT source_watermark FROM cutoff)) AS rejected,
                EXISTS (SELECT 1 FROM fill_facts f WHERE f.requestid=c.requestid AND (f.executed_at_utc IS NULL OR f.executed_at_utc <= (SELECT source_watermark FROM cutoff))) AS filled,
                EXISTS (SELECT 1 FROM lifecycle_event_facts r WHERE r.requestid=c.requestid AND r.event_type='ORDER_CANCELLED'
                    AND r.occurred_at_utc <= (SELECT source_watermark FROM cutoff)) AS cancelled,
                EXISTS (SELECT 1 FROM lifecycle_event_facts r WHERE r.requestid=c.requestid AND r.event_type='ORDER_ACCEPTED'
                    AND r.occurred_at_utc <= (SELECT source_watermark FROM cutoff)) AS accepted
                FROM cohort c)
            SELECT COUNT(*) AS cohort_orders, COUNT(*) FILTER(WHERE rejected) AS rejected_orders,
                COUNT(*) FILTER(WHERE NOT rejected AND NOT filled AND NOT cancelled AND accepted AND order_status='PENDING') AS pending_orders,
                COUNT(*) FILTER(WHERE NOT rejected AND NOT filled AND NOT cancelled AND NOT (accepted AND order_status='PENDING')) AS unknown_outcome_orders,
                COUNT(*) FILTER(WHERE (rejected AND (filled OR cancelled)) OR (filled AND cancelled)) AS conflicting_outcome_orders,
                100.0 * COUNT(*) FILTER(WHERE rejected) / NULLIF(COUNT(*),0) AS rejection_rate_percent FROM outcomes
            """);
        r.putAll(cohort);
        r.put("grossNotional",list(q,"SELECT execution_currency AS currency,SUM(execution_price*executed_quantity*contract_multiplier) AS amount,COUNT(*) AS valued_fills FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end AND valued GROUP BY execution_currency ORDER BY currency"));
        r.put("fees",list(q,"SELECT fee_currency AS currency,SUM(fee) AS amount FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end AND fee_currency IS NOT NULL GROUP BY fee_currency ORDER BY currency"));
        r.put("netCash",list(q,"SELECT currency,SUM(amount) AS amount FROM cash WHERE occurred_at_utc >= :start AND occurred_at_utc < :end AND currency IS NOT NULL AND currency_source IS NOT NULL GROUP BY currency ORDER BY currency"));
        return r;
    }
    private Map<String,Object> groups(AnalyticsQuery q,String kind,boolean all) {
        String keys=switch(kind) {
            case "activity" -> "(executed_at_utc AT TIME ZONE :zone)::date AS period";
            case "instruments" -> "instrumentid,ticker,quantity_unit";
            case "client-segments" -> "segment_code";
            default -> throw new IllegalArgumentException("Unknown grouping");
        };
        String by=switch(kind) {case "activity"->"1";case "instruments"->"instrumentid,ticker,quantity_unit";default->"segment_code";};
        // Monetary measures are separate per currency; never add native units across instruments.
        String grouped="SELECT "+keys+",COUNT(*) AS fill_count,COUNT(DISTINCT requestid) AS executed_orders,COUNT(DISTINCT client_id) AS active_clients,"+
            "COUNT(*) FILTER(WHERE side='BUY') AS buy_fills,COUNT(*) FILTER(WHERE side='SELL') AS sell_fills"+
            (kind.equals("instruments")?",SUM(executed_quantity) AS executed_quantity":"")+
            " FROM fills WHERE executed_at_utc >= :start AND executed_at_utc < :end GROUP BY "+by;
        String key=kind.equals("activity")?"period":kind.equals("instruments")?"instrumentid":"segment_code";
        String sort=q.sort().equals("fills")?"fill_count":key;
        String order=" ORDER BY "+sort+" "+q.direction()+", "+key+" ASC"+(kind.equals("instruments")?",quantity_unit ASC NULLS LAST":"");
        long total=sql.queryForObject(base(q)+"SELECT COUNT(*) FROM ("+grouped+") groups",params(q),Long.class);
        if(all && total>1000) throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,"Report exceeds 1000 groups; narrow filters");
        var items=list(q,grouped+order+(all?" LIMIT 1000":" LIMIT :limit OFFSET :offset"));
        var result=new LinkedHashMap<String,Object>(); result.put("items",items);result.put("total",total);
        result.put("page",all?0:q.page());result.put("size",all?1000:q.size());
        result.put("rankingMeasure","fill_count");return result;
    }
    @Transactional(transactionManager="olapTransactionManager", isolation=Isolation.REPEATABLE_READ, timeout=30)
    public Map<String,Object> issue(AnalyticsReportRequest request) {
        var q=request.query();
        if(!q.from().equals(q.from().withDayOfMonth(1)) || !q.to().equals(q.from().withDayOfMonth(q.from().lengthOfMonth())))
            throw new IllegalArgumentException("Issued reports require one complete calendar month");
        bound();
        String actor=SecurityContextHolder.getContext().getAuthentication().getName();
        String signature=json.writeValueAsString(Arrays.asList(q.from().toString(),q.to().toString(),q.timezone(),q.instrumentId(),q.side(),q.segment(),request.amendsReportId(),request.amendmentReason()));
        var existing=db.queryForList("SELECT report_id,snapshot,request_signature FROM analytics_reports WHERE issued_by=? AND idempotency_key=?",actor,request.idempotencyKey());
        if(!existing.isEmpty()) return replay(existing.get(0),signature);
        int version=1;
        if(request.amendsReportId()!=null) {
            var previous=report(request.amendsReportId());
            if(request.amendmentReason()==null || request.amendmentReason().isBlank()) throw new IllegalArgumentException("An amendment requires a reason");
            var old=cast(previous.get("snapshot"));
            var period=cast(old.get("period"));
            if(!q.from().toString().equals(period.get("from")) || !q.to().toString().equals(period.get("to")) || !q.timezone().equals(period.get("timezone")) || !filters(q).equals(old.get("filters")))
                throw new IllegalArgumentException("Amendments must retain the original period, timezone and filters");
            version=((Number)previous.get("version")).intValue()+1;
        }
        var snapshot=metadata(q);
        var totals=overview(q);
        outcomeCoverage(snapshot,totals);
        snapshot.put("overview",totals);
        for(String kind:List.of("activity","instruments","client-segments")) snapshot.put(kind,groups(q,kind,true));
        String id=UUID.randomUUID().toString();
        db.update("INSERT INTO analytics_reports(report_id,issued_by,idempotency_key,request_signature,version,amends_report_id,amendment_reason,snapshot) VALUES(?,?,?,?,?,?,?,?::jsonb)",
            id,actor,request.idempotencyKey(),signature,version,request.amendsReportId(),request.amendmentReason(),json.writeValueAsString(snapshot));
        return report(id);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> cast(Object value) { return (Map<String,Object>) value; }
    private Map<String,Object> replay(Map<String,Object> row,String signature) {
        if(!signature.equals(row.get("request_signature"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Idempotency key already used with different report parameters");
        // Return the full saved report, never recalculate it against a newer dataset.
        return report(row.get("report_id").toString());
    }
    @Transactional(transactionManager="olapTransactionManager", readOnly=true, isolation=Isolation.REPEATABLE_READ, timeout=15)
    public Map<String,Object> report(String id) {
        db.execute("SET LOCAL statement_timeout = '5s'");
        db.execute("SET LOCAL lock_timeout = '3s'");
        try { UUID.fromString(id); } catch(RuntimeException e) { throw new IllegalArgumentException("Invalid report ID"); }
        var rows=db.queryForList("SELECT report_id,version,amends_report_id,amendment_reason,issued_at,snapshot FROM analytics_reports WHERE report_id=?",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Report not found");
        var row=rows.get(0); var r=new LinkedHashMap<String,Object>();
        r.put("reportId",row.get("report_id"));r.put("version",row.get("version"));r.put("amendsReportId",row.get("amends_report_id"));
        r.put("amendmentReason",row.get("amendment_reason"));r.put("issuedAt",instant(row.get("issued_at")));
        r.put("snapshot",json.readerFor(Map.class).with(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readValue(row.get("snapshot").toString())); return r;
    }
}
