package com.example.pdfconverter.service;

import java.io.Serializable;

public class AttemptInfo implements Serializable {
    private int attempts;
    private long freezeTime;

    public AttemptInfo() {}

    public AttemptInfo(int attempts) {
        this.attempts = attempts;
        if (attempts >= RateLimitService.MAX_ATTEMPTS) {
            this.freezeTime = System.currentTimeMillis() + RateLimitService.FREEZE_DURATION.toMillis();
        }
    }

    public void incrementAttempts() {
        this.attempts++;
        if (this.attempts >= RateLimitService.MAX_ATTEMPTS) {
            this.freezeTime = System.currentTimeMillis() + RateLimitService.FREEZE_DURATION.toMillis();
        }
    }

    public boolean isFrozen() {
        if (freezeTime == 0) return false;
        if (System.currentTimeMillis() < freezeTime) return true;
        freezeTime = 0;
        attempts = 0;
        return false;
    }
}
