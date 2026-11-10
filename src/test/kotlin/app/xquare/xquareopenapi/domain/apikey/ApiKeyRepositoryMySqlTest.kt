package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.global.error.violates
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.dao.DataIntegrityViolationException
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.mysql.MySQLContainer
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

// 동시 신청을 409로 바꿀 때 쓰는 제약 이름이 운영 DB인 MySQL에서도 같게 나오는지 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ApiKeyRepositoryMySqlTest @Autowired constructor(
    private val apiKeyRepository: ApiKeyRepository,
) {

    @Test
    fun `같은 사용자의 키가 겹치면 사용자 제약 위반으로 구분한다`() {
        apiKeyRepository.saveAndFlush(ApiKey(2L, "급식 알리미", "급식 공유", ApiKeySecret.hash("first")))

        val e = assertFailsWith<DataIntegrityViolationException> {
            apiKeyRepository.saveAndFlush(ApiKey(2L, "급식 알리미", "급식 공유", ApiKeySecret.hash("second")))
        }

        assertTrue(e.violates(ApiKey.USER_ID_UNIQUE_CONSTRAINT))
    }

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
