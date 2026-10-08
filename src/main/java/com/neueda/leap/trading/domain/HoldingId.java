package com.neueda.leap.trading.domain;

import java.io.Serializable;
import java.util.Objects;

public class HoldingId implements Serializable {
    private Integer account;
    private Integer instrument;

    public HoldingId() {}

    public HoldingId(Integer account, Integer instrument) {
        this.account = account;
        this.instrument = instrument;
    }

    public Integer getAccount() {
        return account;
    }

    public void setAccount(Integer account) {
        this.account = account;
    }

    public Integer getInstrument() {
        return instrument;
    }

    public void setInstrument(Integer instrument) {
        this.instrument = instrument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HoldingId holdingId = (HoldingId) o;
        return Objects.equals(account, holdingId.account) &&
               Objects.equals(instrument, holdingId.instrument);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, instrument);
    }
}
