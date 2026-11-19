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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

// 409로 바꿀 때 쓰는 제약 이름과 잠금 쿼리가 운영 DB인 MySQL에서도 같게 동작하는지 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class ApiKeyRepositoryMySqlTest @Autowired constructor(
    private val apiKeyRepository: ApiKeyRepository,
    private val apiKeyApplicationRepository: ApiKeyApplicationRepository,
) {

    @Test
    fun `같은 사용자의 키가 겹치면 사용자 제약 위반으로 구분한다`() {
        apiKeyRepository.saveAndFlush(ApiKey(2L, ApiKeySecret.hash("first")))

        val e = assertFailsWith<DataIntegrityViolationException> {
            apiKeyRepository.saveAndFlush(ApiKey(2L, ApiKeySecret.hash("second")))
        }

        assertTrue(e.violates(ApiKey.USER_ID_UNIQUE_CONSTRAINT))
    }

    @Test
    fun `같은 키에 같은 분야를 두 번 넣으면 분야 제약 위반으로 구분한다`() {
        val apiKey = apiKeyRepository.saveAndFlush(ApiKey(2L, ApiKeySecret.hash("first")))
        apiKeyApplicationRepository.saveAndFlush(application(apiKey.id!!, ApiKeyApplicationType.FIRST))

        val e = assertFailsWith<DataIntegrityViolationException> {
            apiKeyApplicationRepository.saveAndFlush(application(apiKey.id!!, ApiKeyApplicationType.ADD_FIELD))
        }

        assertTrue(e.violates(ApiKeyApplication.FIELD_UNIQUE_CONSTRAINT))
        assertTrue(!e.violates(ApiKey.USER_ID_UNIQUE_CONSTRAINT))
    }

    @Test
    fun `키 잠금 조회는 있는 키를 돌려주고 없는 id면 null이다`() {
        val apiKey = apiKeyRepository.saveAndFlush(ApiKey(2L, ApiKeySecret.hash("first")))

        assertEquals(apiKey.id, apiKeyRepository.findByIdForUpdate(apiKey.id!!)?.id)
        assertNull(apiKeyRepository.findByIdForUpdate(apiKey.id!! + 1))
    }

    private fun application(apiKeyId: Long, type: ApiKeyApplicationType) =
        ApiKeyApplication(apiKeyId, ApiKeyField.STUDENT, type, "급식 알리미", "급식 공유")

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
