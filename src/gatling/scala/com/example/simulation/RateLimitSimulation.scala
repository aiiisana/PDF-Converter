package com.example.simulation

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class RateLimitSimulation extends Simulation {

  // Базовый URL вашего сервиса
  val baseUrl = "http://localhost:8080"

  // HTTP протокол
  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // Сценарий для free пользователей
  val freeUserScenario = scenario("Free User Rate Limit Test")
    .exec(
      http("PDF Conversion Request - Free")
        .post("/api/convert")
        .header("Authorization", "Bearer free_user_token")
        .body(StringBody("""{"fileSize": 1048576, "fileHash": "test123"}"""))
        .check(status.in(200, 429))
        .pause(1.second)

  // Сценарий для pro пользователей
  val proUserScenario = scenario("Pro User Rate Limit Test")
    .exec(
      http("PDF Conversion Request - Pro")
        .post("/api/convert")
        .header("Authorization", "Bearer pro_user_token")
        .body(StringBody("""{"fileSize": 1048576, "fileHash": "test456"}"""))
        .check(status.in(200, 429)))
    .pause(1.second)

  // Сценарий для vip пользователей
  val vipUserScenario = scenario("VIP User Rate Limit Test")
    .exec(
      http("PDF Conversion Request - VIP")
        .post("/api/convert")
        .header("Authorization", "Bearer vip_user_token")
        .body(StringBody("""{"fileSize": 1048576, "fileHash": "test789"}"""))
        .check(status.in(200, 429)))
    .pause(1.second)

  // Настройка теста
  setUp(
    // Тестируем free пользователей (1 запрос в день)
    freeUserScenario.inject(
      atOnceUsers(2) // Должен провалиться, так как лимит 1 запрос
    ),

    // Тестируем pro пользователей (15 запросов в день)
    proUserScenario.inject(
      constantUsersPerSec(1).during(20.seconds) // 20 запросов за 20 секунд
    ),

    // Тестируем vip пользователей (50 запросов в день)
    vipUserScenario.inject(
      rampUsersPerSec(1).to(10).during(1.minute) // Нарастающая нагрузка
    )
  ).protocols(httpProtocol)
}