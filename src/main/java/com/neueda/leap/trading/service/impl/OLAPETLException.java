package com.neueda.leap.trading.service.impl;

public class OLAPETLException extends RuntimeException {
    
    public OLAPETLException(String message) {
        super(message);
    }
    
    public OLAPETLException(String message, Throwable cause) {
        super(message, cause);
    }
}