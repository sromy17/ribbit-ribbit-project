package com.neueda.leap.trading.domain;

/**
 * Enum representing the trading status of an instrument.
 * Used to enforce BR-05: Trading rules validation before order acceptance.
 */
public enum InstrumentStatus {
    /** Instrument is available for trading */
    ACTIVE,
    /** Instrument is suspended - orders cannot be placed */
    SUSPENDED,
    /** Instrument is delisted - no longer available */
    DELISTED,
    /** Instrument is in a halt state (e.g., due to regulatory action) */
    HALTED;

    /**
     * Check if the instrument is currently tradable.
     * @return true if the instrument can accept new orders
     */
    public boolean isTradable() {
        return this == ACTIVE;
    }
}
