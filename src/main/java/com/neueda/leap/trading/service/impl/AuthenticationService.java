package com.neueda.leap.trading.service.impl;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import com.neueda.leap.trading.api.dto.LoginRequest;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.UserRepository;
import com.neueda.leap.trading.security.TokenHash;
import org.springframework.beans.factory.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Staff authentication shares the hashed token store with customer authentication. */
@Service
public class AuthenticationService {
    private final UserRepository users; private final JdbcTemplate db; private final FinancialIdAllocator ids;
    private final PasswordEncoder passwords; private final JwtEncoder encoder; private final long lifetime;
    public AuthenticationService(UserRepository users,@Qualifier("oltpJdbcTemplate") JdbcTemplate db,
        FinancialIdAllocator ids,PasswordEncoder passwords,JwtEncoder encoder,
        @Value("${security.jwt.session-lifetime-seconds:3600}") long lifetime) {
        this.users=users;this.db=db;this.ids=ids;this.passwords=passwords;this.encoder=encoder;this.lifetime=lifetime;
    }
    @Transactional public LoginResult login(LoginRequest request) {
        User user=users.findByUsername(request.username()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid username or password"));
        if(!passwords.matches(request.password(),user.getUserHashedSaltedPassword())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid username or password");
        return issue(user);
    }
    private LoginResult issue(User user) {
        Instant now=Instant.now(),expires=now.plusSeconds(lifetime);
        var claims=JwtClaimsSet.builder().subject(user.getUsername()).id(UUID.randomUUID().toString()).claim("uid",user.getUserId())
            .claim("roles",user.getRoles().stream().map(role->role.getRoleName()).toList()).issuedAt(now).expiresAt(expires).build();
        String token=encoder.encode(JwtEncoderParameters.from(org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
        String refresh=UUID.randomUUID().toString()+UUID.randomUUID(); int refreshId=ids.next("REFRESH_TOKEN");
        db.update("INSERT INTO refresh_tokens(refreshtokenid,userid,refreshtokenhash,dateissued,dateexpires,isrevoked) VALUES(?,?,?,?,?,false)",refreshId,user.getUserId(),TokenHash.sha256(refresh),Timestamp.from(now),Timestamp.from(now.plusSeconds(7*86400)));
        db.update("INSERT INTO access_tokens(accesstokenid,refreshtokenid,accesstokenhash,dateissued,dateexpires,isrevoked) VALUES(?,?,?,?,?,false)",ids.next("ACCESS_TOKEN"),refreshId,TokenHash.sha256(token),Timestamp.from(now),Timestamp.from(expires));
        return new LoginResult(token,expires,user.getUserId(),user.getUsername(),user.getFirstName(),user.getLastName(),refresh,lifetime);
    }
    @Transactional public LoginResult refresh(String raw) {
        if(raw==null || raw.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Refresh token is required");
        var rows=db.queryForList("SELECT refreshtokenid,userid FROM refresh_tokens WHERE refreshtokenhash=? AND isrevoked=false AND dateexpires>? FOR UPDATE",TokenHash.sha256(raw),Timestamp.from(Instant.now()));
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Refresh token is invalid");
        int refreshId=((Number)rows.get(0).get("refreshtokenid")).intValue();
        db.update("UPDATE refresh_tokens SET isrevoked=true WHERE refreshtokenid=?",refreshId);
        db.update("UPDATE access_tokens SET isrevoked=true WHERE refreshtokenid=?",refreshId);
        return issue(users.findById(((Number)rows.get(0).get("userid")).intValue()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"User is unavailable")));
    }
    @Transactional public void logout(String raw) {
        var rows=db.queryForList("SELECT refreshtokenid FROM access_tokens WHERE accesstokenhash=?",TokenHash.sha256(raw));
        for(var row:rows) {
            db.update("UPDATE access_tokens SET isrevoked=true WHERE refreshtokenid=?",row.get("refreshtokenid"));
            db.update("UPDATE refresh_tokens SET isrevoked=true WHERE refreshtokenid=?",row.get("refreshtokenid"));
        }
    }
    @Transactional public void revokeUserSessions(Integer id) {
        db.update("UPDATE access_tokens SET isrevoked=true WHERE refreshtokenid IN (SELECT refreshtokenid FROM refresh_tokens WHERE userid=?)",id);
        db.update("UPDATE refresh_tokens SET isrevoked=true WHERE userid=?",id);
    }
    public record LoginResult(String accessToken,Instant expiresAt,Integer userId,String username,String firstName,String lastName,String refreshToken,long expiresInSeconds) { }
}
