package com.neueda.leap.trading.service.impl;

import java.time.LocalDateTime;

public class OLAPETLResult {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMs;
    private String status; // SUCCESS, FAILED, RUNNING
    private String errorMessage;
    
    // Getters and Setters
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    
    @Override
    public String toString() {
        return "OLAPETLResult{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                ", durationMs=" + durationMs +
                ", status='" + status + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}