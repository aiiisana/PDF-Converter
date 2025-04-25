package com.example.pdfconverter.controller;

import com.example.pdfconverter.model.SubscriptionType;
import com.example.pdfconverter.model.User;
import com.example.pdfconverter.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get current user profile")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved profile"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized"
            ));
        }

        User user = (User) authentication.getPrincipal();

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "subscription", user.getSubscription()
        ));
    }

    @Operation(summary = "Change subscription type for authenticated user")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Subscription changed"),
            @ApiResponse(responseCode = "202", description = "Not enough money, but request accepted"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PostMapping("/subscription")
    public ResponseEntity<?> changeSubscription(
            Authentication authentication,
            @RequestParam String type,
            @RequestParam double money
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "error", "Unauthorized"
            ));
        }

        User user = (User) authentication.getPrincipal();
        boolean changed = userService.changeSubscription(user.getId(), type, money);

        if (changed) {
            return ResponseEntity.ok(Map.of(
                    "message", "Subscription updated",
                    "newSubscription", type
            ));
        } else {
            double required = SubscriptionType.fromString(type).getPrice();
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                            "message", "Not enough money",
                            "requiredAmount", required,
                            "yourAmount", money
                    ));
        }
    }
}
