package com.neueda.leap.trading.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "userid")
    private Integer userId;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "salt", nullable = false, length = 25)
    private String salt;

    @Column(name = "userhashedsaltedpassword", nullable = false, length = 64)
    private String userHashedSaltedPassword;

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<TradingAccount> tradingAccounts = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Session> sessions = new ArrayList<>();

    @JsonIgnore
    @ManyToMany(mappedBy = "users")
    private Set<Role> roles = new HashSet<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Watchlist> watchlists = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "user")
    private List<Notification> notifications = new ArrayList<>();

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public String getUserHashedSaltedPassword() {
        return userHashedSaltedPassword;
    }

    public void setUserHashedSaltedPassword(String userHashedSaltedPassword) {
        this.userHashedSaltedPassword = userHashedSaltedPassword;
    }

    public List<TradingAccount> getTradingAccounts() {
        return tradingAccounts;
    }

    public void setTradingAccounts(List<TradingAccount> tradingAccounts) {
        this.tradingAccounts = tradingAccounts;
    }

    public List<Session> getSessions() {
        return sessions;
    }

    public void setSessions(List<Session> sessions) {
        this.sessions = sessions;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public List<Watchlist> getWatchlists() {
        return watchlists;
    }

    public void setWatchlists(List<Watchlist> watchlists) {
        this.watchlists = watchlists;
    }

    public List<Notification> getNotifications() {
        return notifications;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

    public void updateProfile(String username) {
        this.username = username;
    }
}
