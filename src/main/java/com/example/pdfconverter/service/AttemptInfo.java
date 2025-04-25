package com.example.pdfconverter.service;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

public class AttemptInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private int attempts;
    private Instant freezeUntil;

    public AttemptInfo() {}

    public AttemptInfo(int attempts) {
        this.attempts = attempts;
        if (attempts >= RateLimitService.MAX_ATTEMPTS) {
            this.freezeUntil = Instant.now().plus(RateLimitService.FREEZE_DURATION);
        }
    }

    public void incrementAttempts() {
        this.attempts++;
        if (this.attempts >= RateLimitService.MAX_ATTEMPTS) {
            this.freezeUntil = Instant.now().plus(RateLimitService.FREEZE_DURATION);
        }
    }

    public boolean isFrozen() {
        if (freezeUntil == null) return false;
        return Instant.now().isBefore(freezeUntil);
    }

    // Getters and setters
    public int getAttempts() { return attempts; }
    public Instant getFreezeUntil() { return freezeUntil; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public void setFreezeUntil(Instant freezeUntil) { this.freezeUntil = freezeUntil; }
}