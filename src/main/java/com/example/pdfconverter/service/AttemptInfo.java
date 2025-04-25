package com.example.pdfconverter.service;

import lombok.Getter;
import lombok.Setter;

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
        if (this.attempts >= RateLimitService.MAX_ATTEMPTS && freezeUntil == null) {
            this.freezeUntil = Instant.now().plus(RateLimitService.FREEZE_DURATION);
        }
    }

    public boolean isFrozen() {
        return freezeUntil != null && Instant.now().isBefore(freezeUntil);
    }

    public Instant getFreezeUntil() {
        return freezeUntil;
    }
}