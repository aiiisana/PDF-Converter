package com.example.pdfconverter.controller;


import com.example.pdfconverter.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Not authenticated");
        }

        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "uid", user.getId(),
                "email", user.getUsername(),
                "subscription", user.getSubscription()
        ));
    }
}
