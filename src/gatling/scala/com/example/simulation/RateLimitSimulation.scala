package loadtest

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._
import java.util.UUID

class AdvancedRateLimitSimulation extends Simulation {

  // Конфигурация HTTP
  private val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("multipart/form-data")
    .userAgentHeader("Gatling RateLimit Test")

  // Генерация уникальных файлов для теста
  private def generateFileContent(size: Int): String = {
    "x" * size
  }

  // Различные типы файлов
  private val smallFile = generateFileContent(500 * 1024)  // 500KB
  private val mediumFile = generateFileContent(3 * 1024 * 1024) // 3MB
  private val largeFile = generateFileContent(10 * 1024 * 1024) // 10MB

  // Сценарии для разных типов пользователей
  private val freeUserScenario = scenario("Free User Test")
    .exec(session => session.set("userId", UUID.randomUUID().toString))
    .repeat(10) { // 10 попыток (должно хватить для превышения лимита)
      exec(
        http("Free User Request")
          .post("/api/convert")
          .header("Content-Type", "multipart/form-data")
          .bodyPart(StringBodyPart("file", smallFile).fileName("small.txt"))
          .check(
            status.in(200, 429),
            jsonPath("$.error").optional.saveAs("errorMsg")
          )
      ).pause(1.second)
    }

  private val proUserScenario = scenario("Pro User Test")
    .exec(session => session.set("userId", UUID.randomUUID().toString))
    .repeat(15) {
      exec(
        http("Pro User Request")
          .post("/api/convert")
          .header("Content-Type", "multipart/form-data")
          .header("Authorization", "Bearer pro_token")
          .bodyPart(StringBodyPart("file", mediumFile).fileName("medium.txt"))
          .check(
            status.in(200, 429),
            jsonPath("$.error").optional.saveAs("errorMsg")
          )
      ).pause(1.second)
    }

  private val vipUserScenario = scenario("VIP User Test")
    .exec(session => session.set("userId", UUID.randomUUID().toString))
    .repeat(250) {
      exec(
        http("VIP User Request")
          .post("/api/convert")
          .header("Content-Type", "multipart/form-data")
          .header("Authorization", "Bearer vip_token")
          .bodyPart(StringBodyPart("file", largeFile).fileName("large.txt"))
          .check(
            status.in(200, 429),
            jsonPath("$.error").optional.saveAs("errorMsg")
          )
      ).pause(1.second)
    }

  // Сценарий для тестирования глобального ограничения
  private val globalLimitScenario = scenario("Global Limit Test")
    .exec(session => session.set("userId", UUID.randomUUID().toString))
    .exec(
      http("High Load Request")
        .post("/api/convert")
        .header("Content-Type", "multipart/form-data")
        .bodyPart(StringBodyPart("file", smallFile).fileName("test.txt"))
        .check(
          status.in(200, 429),
          jsonPath("$.message").optional.saveAs("message")
        )
    )

  // Настройка тестов
  setUp(
    // Тест лимитов для разных пользователей
    freeUserScenario.inject(atOnceUsers(5)), // 5 free пользователей
    proUserScenario.inject(atOnceUsers(3)),  // 3 pro пользователя
    vipUserScenario.inject(atOnceUsers(1)),  // 1 vip пользователь

    // Тест глобального ограничения (запускаем отдельно)
    // globalLimitScenario.inject(rampUsers(150).during(10.seconds))
  ).protocols(httpProtocol)
    .assertions(
      global.failedRequests.count.is(0) // Проверяем, что нет технических ошибок
    )
}