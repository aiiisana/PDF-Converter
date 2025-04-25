package com.example.pdfconverter.model;

import lombok.Getter;

@Getter
public enum SubscriptionType {
    FREE(0), PRO(1000), VIP(5000);

    private final double price;

    SubscriptionType(double price) {
        this.price = price;
    }

    public static SubscriptionType fromString(String name) {
        try {
            return SubscriptionType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid subscription type: " + name);
        }
    }
}

