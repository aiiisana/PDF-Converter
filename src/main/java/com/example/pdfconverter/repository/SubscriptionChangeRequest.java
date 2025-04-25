package com.example.pdfconverter.repository;

import lombok.Data;

@Data
public class SubscriptionChangeRequest {
    private String subscription; // "PRO", "VIP"
    private double amount;       // Переданная сумма денег
}