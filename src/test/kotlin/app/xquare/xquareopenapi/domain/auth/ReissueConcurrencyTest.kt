package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.user.User
import app.xquare.xquareopenapi.domain.user.UserRepository
import com.jayway.jsonpath.JsonPath
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.annotation.DirtiesContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// 가짜 저장소를 쓰지 않고 실제 HTTP 서버와 Redis를 거쳐 동시에 토큰을 교체한다.
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.datasource.url=jdbc:h2:mem:reissue-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1"],
)
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReissueConcurrencyTest @Autowired constructor(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val redisTemplate: StringRedisTemplate,
) {
    @LocalServerPort
    private var port: Int = 0

    private var userId: Long = 0

    @BeforeEach
    fun setUp() {
        userId = userRepository.save(
            User("concurrent", passwordEncoder.encode(PASSWORD)!!, 2315, "장윤호", "concurrent@dsm.hs.kr"),
        ).id!!
    }

    @AfterEach
    fun tearDown() {
        redisTemplate.delete("refresh_token:$userId")
        userRepository.deleteAll()
    }

    @RepeatedTest(3)
    fun `같은 토큰으로 동시에 재발급하면 하나만 성공하고 새 토큰으로 다시 재발급할 수 있다`() {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build().use { client ->
            val login = post(client, "/auth/login", """{"username":"concurrent","password":"$PASSWORD"}""")
            assertEquals(200, login.statusCode(), login.body())
            val oldToken = JsonPath.read<String>(login.body(), "$.refreshToken")

            val responses = reissueConcurrently(client, oldToken)
            val successes = responses.filter { it.statusCode() == 200 }
            val failures = responses.filter { it.statusCode() == 401 }
            val statuses = responses.map { it.statusCode() }.toString()
            assertEquals(1, successes.size, statuses)
            assertEquals(CONCURRENT_REQUESTS - 1, failures.size, statuses)
            failures.forEach { response ->
                assertEquals("INVALID_REFRESH_TOKEN", JsonPath.read<String>(response.body(), "$.code"))
            }

            val newToken = JsonPath.read<String>(successes.single().body(), "$.refreshToken")
            assertNotEquals(oldToken, newToken)
            assertEquals(newToken, redisTemplate.opsForValue().get("refresh_token:$userId"))
            assertTrue(redisTemplate.getExpire("refresh_token:$userId") > 0)

            // 실패한 요청이 성공한 요청의 새 토큰을 덮어쓰지 않았는지 API로도 확인한다.
            val next = reissue(client, newToken)
            assertEquals(200, next.statusCode(), next.body())
            val nextToken = JsonPath.read<String>(next.body(), "$.refreshToken")
            assertNotEquals(newToken, nextToken)
            assertEquals(nextToken, redisTemplate.opsForValue().get("refresh_token:$userId"))

            val replay = reissue(client, oldToken)
            assertEquals(401, replay.statusCode(), replay.body())
            assertEquals("INVALID_REFRESH_TOKEN", JsonPath.read<String>(replay.body(), "$.code"))
            assertEquals(nextToken, redisTemplate.opsForValue().get("refresh_token:$userId"))
        }
    }

    private fun reissueConcurrently(client: HttpClient, token: String): List<HttpResponse<String>> {
        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        val ready = CountDownLatch(CONCURRENT_REQUESTS)
        val start = CountDownLatch(1)
        try {
            val requests = List(CONCURRENT_REQUESTS) {
                executor.submit<HttpResponse<String>> {
                    ready.countDown()
                    assertTrue(start.await(10, TimeUnit.SECONDS), "동시 요청 시작 신호를 받지 못했습니다.")
                    reissue(client, token)
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "모든 요청이 준비되지 않았습니다.")
            start.countDown()
            return requests.map { it.get(20, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "요청 스레드가 종료되지 않았습니다.")
        }
    }

    private fun reissue(client: HttpClient, token: String) =
        post(client, "/auth/reissue", """{"refreshToken":"$token"}""")

    private fun post(client: HttpClient, path: String, body: String): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    companion object {
        private const val PASSWORD = "Password1!"
        private const val CONCURRENT_REQUESTS = 8

        @Container
        @ServiceConnection(name = "redis")
        @JvmStatic
        val redis: GenericContainer<*> = GenericContainer("redis:7.4-alpine").withExposedPorts(6379)
    }
}
