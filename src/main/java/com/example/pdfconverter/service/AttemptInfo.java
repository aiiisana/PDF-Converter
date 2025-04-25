package com.example.pdfconverter.service;

import java.time.Instant;

public class AttemptInfo {
    private int attempts;
    private Instant freezeUntil;

    public AttemptInfo(int attempts) {
        this.attempts = attempts;
        checkAndSetFreeze();
    }

    public void incrementAttempts() {
        this.attempts++;
        checkAndSetFreeze();
    }

    private void checkAndSetFreeze() {
        if (this.attempts >= RateLimitService.MAX_ATTEMPTS) {
            this.freezeUntil = Instant.now().plus(RateLimitService.FREEZE_DURATION);
        }
    }

    public boolean isFrozen() {
        if (freezeUntil == null) return false;
        return Instant.now().isBefore(freezeUntil);
    }

    public Instant getFreezeUntil() {
        return freezeUntil;
    }
}