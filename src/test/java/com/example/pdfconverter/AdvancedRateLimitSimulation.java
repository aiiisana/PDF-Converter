package com.example.pdfconverter;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class AdvancedRateLimitSimulation extends Simulation {

    // Конфигурация пользователей
    private static final String[] FREE_USERS = {
            "user1@free.com:123456:free",
            "user2@free.com:123456:free",
            "user3@free.com:123456:free"
    };

    private static final String[] PRO_USERS = {
            "pro@pro.com:123456:pro",
            "pro2@pro.com:123456:pro"
    };

    private static final String[] VIP_USERS = {
            "vip@vip.com:123456:vip"
    };

    // Конфигурация сервера
    private static final String API_KEY = "AIzaSyAZKCwi4S4Q4q6-QcMihvBQwbfcBZkHh8o";
    private static final String AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY;
    private static final String CONVERT_URL = "/api/convert";

    // Трекеры для проверки лимитов
    private static final AtomicInteger freeUserRequests = new AtomicInteger(0);
    private static final AtomicInteger proUserRequests = new AtomicInteger(0);
    private static final AtomicInteger vipUserRequests = new AtomicInteger(0);

    // HTTP протокол
    private static final HttpProtocolBuilder HTTP_PROTOCOL = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .contentTypeHeader("multipart/form-data")
            .userAgentHeader("Gatling Performance Test");

    // Шаги сценария
    private static class Steps {
        // Аутентификация пользователя
        static ChainBuilder authenticate(String email, String password) {
            return exec(http("Authenticate " + email)
                    .post(AUTH_URL)
                    .header("Content-Type", "application/json")
                    .body(StringBody("{\"email\":\"" + email +
                            "\",\"password\":\"" + password +
                            "\",\"returnSecureToken\":true}"))
                    .check(
                            status().is(200),
                            jsonPath("$.idToken").saveAs("authToken"),
                            jsonPath("$.localId").saveAs("userId"),
                            jsonPath("$.email").saveAs("userEmail")
                    ));
        }

        // Отправка файла на конвертацию
        static ChainBuilder convertFile(String fileContent, String fileName, String userType) {
            return exec(session -> {
                // Логируем количество запросов
                switch(userType) {
                    case "free": freeUserRequests.incrementAndGet(); break;
                    case "pro": proUserRequests.incrementAndGet(); break;
                    case "vip": vipUserRequests.incrementAndGet(); break;
                }
                return session;
            })
                    .exec(http("Convert File " + fileName)
                            .post(CONVERT_URL)
                            .header("Authorization", "Bearer ${authToken}")
                            .header("Content-Type", "multipart/form-data")
                            .bodyPart(StringBodyPart("file", fileContent).fileName(fileName))
                            .bodyPart(StringBodyPart("type", "text/plain"))
                            .check(
                                    status().in(200, 429),
                                    jsonPath("$.error").optional().saveAs("errorMsg"),
                                    jsonPath("$.message").optional().saveAs("message")
                            ));
        }

        // Проверка ответа на rate limiting
        static ChainBuilder validateRateLimit(String userType) {
            return doIf(session -> {
                Integer status = session.getInt("status");
                return status != null && status == 429;
            }).then(
                    exec(session -> {
                        String message = session.getString("message");
                        String error = session.getString("errorMsg");
                        System.out.println("Rate limit exceeded for " + userType +
                                " user. Message: " + (message != null ? message : error));
                        return session;
                    })
            );
        }
    }

    // Сценарии для разных типов пользователей
    private static class Scenarios {
        static ScenarioBuilder freeUserScenario = scenario("Free User Flow")
                .exec(session -> {
                    String[] credentials = FREE_USERS[ThreadLocalRandom.current().nextInt(FREE_USERS.length)].split(":");
                    return session
                            .set("email", credentials[0])
                            .set("password", credentials[1])
                            .set("userType", credentials[2]);
                })
                .exec(Steps.authenticate("${email}", "${password}"))
                .repeat(10).on(
                        exec(Steps.convertFile(
                                generateTestFileContent(500 * 1024), // 500KB
                                "free_user_file.txt", "free"))
                                .pause(Duration.ofSeconds(1))
                                .exec(Steps.validateRateLimit("free"))
                );

        static ScenarioBuilder proUserScenario = scenario("Pro User Flow")
                .exec(session -> {
                    String[] credentials = PRO_USERS[ThreadLocalRandom.current().nextInt(PRO_USERS.length)].split(":");
                    return session
                            .set("email", credentials[0])
                            .set("password", credentials[1])
                            .set("userType", credentials[2]);
                })
                .exec(Steps.authenticate("${email}", "${password}"))
                .repeat(15).on(
                        exec(Steps.convertFile(
                                generateTestFileContent(3 * 1024 * 1024), // 3MB
                                "pro_user_file.txt", "pro"))
                                .pause(Duration.ofSeconds(1))
                                .exec(Steps.validateRateLimit("pro"))
                );

        static ScenarioBuilder vipUserScenario = scenario("VIP User Flow")
                .exec(session -> {
                    String[] credentials = VIP_USERS[ThreadLocalRandom.current().nextInt(VIP_USERS.length)].split(":");
                    return session
                            .set("email", credentials[0])
                            .set("password", credentials[1])
                            .set("userType", credentials[2]);
                })
                .exec(Steps.authenticate("${email}", "${password}"))
                .repeat(25).on(
                        exec(Steps.convertFile(
                                generateTestFileContent(10 * 1024 * 1024), // 10MB
                                "vip_user_file.txt", "vip"))
                                .pause(Duration.ofSeconds(1))
                                .exec(Steps.validateRateLimit("vip"))
                );
    }

    // Настройка теста
    {
        setUp(
                Scenarios.freeUserScenario.injectOpen(
                        rampUsers(5).during(10)), // 5 free пользователей за 10 секунд

                Scenarios.proUserScenario.injectOpen(
                        rampUsers(3).during(10)), // 3 pro пользователя за 10 секунд

                Scenarios.vipUserScenario.injectOpen(
                        atOnceUsers(1)) // 1 vip пользователь сразу
        )
                .protocols(HTTP_PROTOCOL)
                .assertions(
                        global().failedRequests().percent().lt(5.0), // Менее 5% ошибок
                        details("Convert File free_user_file.txt").responseTime().max().lt(2000),
                        details("Convert File vip_user_file.txt").responseTime().max().lt(3000)
                );
    }

    private static String generateTestFileContent(int size) {
        // Генерация тестового содержимого файла
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) (ThreadLocalRandom.current().nextInt(26) + 'a'));
        }
        return sb.toString();
    }
}