package com.example.pdfconverter;

import io.gatling.javaapi.core.*;
import io.gatling.javaapi.http.*;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

public class ServerOverloadSimulation extends Simulation {

    // Конфигурация сервера
    private static final String API_KEY = "AIzaSyAZKCwi4S4Q4q6-QcMihvBQwbfcBZkHh8o";
    private static final String AUTH_URL = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + API_KEY;
    private static final String CONVERT_URL = "/api/convert";

    // Параметры для теста перегрузки
    private static final int MAX_USERS = 150;
    private static final Duration TEST_DURATION = Duration.ofMinutes(2);
    private static final Duration RAMP_UP_TIME = Duration.ofSeconds(30);

    // HTTP протокол
    private static final HttpProtocolBuilder HTTP_PROTOCOL = http
            .baseUrl("http://localhost:8080")
            .acceptHeader("application/json")
            .contentTypeHeader("multipart/form-data")
            .userAgentHeader("Gatling Overload Test");

    // Шаги сценария
    private static class Steps {
        static ChainBuilder authenticate() {
            return exec(http("Authenticate User")
                    .post(AUTH_URL)
                    .header("Content-Type", "application/json")
                    .body(StringBody("{\"email\":\"vip@vip.com\"," +
                            "\"password\":\"123456\"," +
                            "\"returnSecureToken\":true}"))
                    .check(
                            status().is(200),
                            jsonPath("$.idToken").saveAs("authToken")
                    ))
                    .exec(session -> {
                        // Проверяем что токен сохранен
                        String token = session.getString("authToken");
                        if (token == null || token.isEmpty()) {
                            throw new RuntimeException("Authentication failed - no token received");
                        }
                        return session;
                    });
        }

        static ChainBuilder convertFile() {
            return exec(http("Convert File")
                    .post(CONVERT_URL)
                    .header("Authorization", "Bearer ${authToken}")
                    .header("Content-Type", "multipart/form-data")
                    .bodyPart(StringBodyPart("file", generateTestFileContent(5 * 1024 * 1024))
                            .fileName("test_file.txt"))
                    .bodyPart(StringBodyPart("type", "text/plain"))
                    .check(
                            status().saveAs("httpStatus"),
                            jsonPath("$.message").optional().saveAs("message")
                    ));
        }

        static ChainBuilder checkOverloadResponse() {
            return doIf(session -> {
                Integer status = session.getInt("httpStatus");
                return status != null && (status == 429 || status == 503);
            }).then(
                    exec(session -> {
                        String message = session.getString("message");
                        System.out.println("Server overload response: " +
                                (message != null ? message : "Status " + session.getInt("httpStatus")));
                        return session;
                    })
            );
        }
    }

    // Сценарий для теста перегрузки
    private static final ScenarioBuilder overloadScenario = scenario("Server Overload Test")
            .exec(Steps.authenticate())
            .during(TEST_DURATION).on(
                    exec(Steps.convertFile())
                            .pause(Duration.ofMillis(500))
                            .exec(Steps.checkOverloadResponse())
            );

    // Настройка теста
    {
        setUp(overloadScenario.injectOpen(
                rampUsers(MAX_USERS).during(RAMP_UP_TIME)
        ).protocols(HTTP_PROTOCOL))
                .assertions(
                        global().responseTime().max().lt(5000),
                        global().failedRequests().percent().lt(50.0)
                );
    }

    private static String generateTestFileContent(int size) {
        StringBuilder sb = new StringBuilder(size);
        for (int i = 0; i < size; i++) {
            sb.append((char) (ThreadLocalRandom.current().nextInt(26) + 'a'));
        }
        return sb.toString();
    }
}