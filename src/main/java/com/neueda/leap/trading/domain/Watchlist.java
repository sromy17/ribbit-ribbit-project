package com.neueda.leap.trading.domain;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "watchlists")
public class Watchlist {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "watchlistid")
    private Integer watchlistId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "userid", nullable = false)
    @JsonIgnore
    private User user;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @ManyToMany
    @JoinTable(
        name = "watchlist_instruments",
        joinColumns = @JoinColumn(name = "watchlistid"),
        inverseJoinColumns = @JoinColumn(name = "instrumentid")
    )
    private Set<Instrument> instruments = new HashSet<>();

    public void addInstrument(Instrument instrument) {
        instruments.add(instrument);
    }

    public void removeInstrument(Instrument instrument) {
        instruments.remove(instrument);
    }

    public Integer getWatchlistId() {
        return watchlistId;
    }

    public void setWatchlistId(Integer watchlistId) {
        this.watchlistId = watchlistId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<Instrument> getInstruments() {
        return instruments;
    }

    public void setInstruments(Set<Instrument> instruments) {
        this.instruments = instruments;
    }
}
