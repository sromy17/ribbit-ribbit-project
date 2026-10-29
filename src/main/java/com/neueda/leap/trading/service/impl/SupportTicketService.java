package com.neueda.leap.trading.service.impl;

import com.neueda.leap.trading.security.AccountAccessService;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SupportTicketService {
    private final JdbcTemplate db;
    private final AccountAccessService access;
    private static final Set<String> CATEGORIES = Set.of("PLATFORM_QUESTION", "TECHNICAL_ISSUE", "ACCOUNT_ASSISTANCE");
    private static final String COLUMNS = "ticket_id,accountid,reason,category,message,status,created_at,closed_at,resolution,version";
    private static final RowMapper<Ticket> MAPPER = (rs, row) -> new Ticket(rs.getInt("ticket_id"),rs.getInt("accountid"),
        rs.getString("reason"),rs.getString("category"),rs.getString("message"),rs.getString("status"),
        rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("closed_at")==null?null:rs.getTimestamp("closed_at").toInstant(),
        rs.getString("resolution"),rs.getInt("version"));

    public SupportTicketService(@Qualifier("oltpJdbcTemplate") JdbcTemplate db, AccountAccessService access) {
        this.db = db;
        this.access = access;
    }

    @Transactional
    public Ticket submit(int accountId, Submission request) {
        access.requireAccountOwner(accountId);
        if (!CATEGORIES.contains(request.category())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose a valid support topic");
        String subject = request.subject().trim(), message = request.message().trim();
        db.update("INSERT INTO support_ticket(accountid,reason,category,message,request_key,status) VALUES (?,?,?,?,?,'OPEN') ON CONFLICT (request_key) DO NOTHING",
            accountId,subject,request.category(),message,request.requestId());
        Ticket ticket = db.queryForObject("SELECT " + COLUMNS + " FROM support_ticket WHERE request_key=?",MAPPER,request.requestId());
        if (ticket == null || ticket.accountId()!=accountId || !subject.equals(ticket.subject()) || !message.equals(ticket.message()) || !request.category().equals(ticket.category()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Submission reference already used; refresh your requests before retrying");
        return ticket;
    }

    public Page customerTickets(int accountId, int page) {
        access.requireAccountOwner(accountId);
        return page("WHERE accountid=?",List.of(accountId),page);
    }

    public Page staffTickets(String status, int page) {
        if (status == null || status.isBlank() || "ALL".equals(status)) return page("",List.of(),page);
        if (!Set.of("OPEN","IN_PROGRESS","RESOLVED").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid ticket status");
        return page("WHERE status=?",List.of(status),page);
    }

    private Page page(String where, List<Object> parameters, int page) {
        if (page < 0 || page > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page");
        var args = new java.util.ArrayList<>(parameters);
        Long total = db.queryForObject("SELECT count(*) FROM support_ticket " + where,Long.class,args.toArray());
        args.add(20); args.add(page*20);
        return new Page(db.query("SELECT " + COLUMNS + " FROM support_ticket " + where + " ORDER BY created_at DESC,ticket_id DESC LIMIT ? OFFSET ?",MAPPER,args.toArray()),total==null?0:total,page,20);
    }

    @Transactional
    public Ticket update(int ticketId, Transition request) {
        if (!Set.of("IN_PROGRESS","RESOLVED").contains(request.status())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose In progress or Resolved");
        String resolution = request.resolution()==null?"":request.resolution().trim();
        if ("RESOLVED".equals(request.status()) && resolution.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"A response is required to resolve a request");
        int updated = db.update("UPDATE support_ticket SET status=?,resolution=?,closed_at=CASE WHEN ?='RESOLVED' THEN CURRENT_TIMESTAMP ELSE NULL END,version=version+1 WHERE ticket_id=? AND version=? AND status<>'RESOLVED'",
            request.status(),resolution,request.status(),ticketId,request.expectedVersion());
        if (updated!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"This ticket changed or is already resolved; refresh the queue");
        return db.queryForObject("SELECT " + COLUMNS + " FROM support_ticket WHERE ticket_id=?",MAPPER,ticketId);
    }

    public record Submission(@NotBlank @Size(max=30) String category,@NotBlank @Size(max=75) String subject,
                             @NotBlank @Size(max=2000) String message,@NotNull UUID requestId) {}
    public record Transition(@NotBlank String status,@Size(max=2000) String resolution,@Min(0) int expectedVersion) {}
    public record Ticket(int ticketId,int accountId,String subject,String category,String message,String status,
                         Instant createdAtUtc,Instant closedAtUtc,String resolution,int version) {}
    public record Page(List<Ticket> items,long total,int page,int size) {}
}
