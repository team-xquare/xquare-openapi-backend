package app.xquare.xquareopenapi.domain.user

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

// 운영 DB인 MySQL이 돌려주는 제약 이름으로도 어떤 값이 겹쳤는지 구분되는지 확인한다. H2는 SignupTest가 확인한다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class UserUniqueConstraintMySqlTest @Autowired constructor(
    private val userRepository: UserRepository,
) {

    @Test
    fun `아이디가 겹치면 USERNAME 제약으로 구분한다`() {
        userRepository.saveAndFlush(user(username = "Jun227", email = "a@dsm.hs.kr"))

        val e = assertFailsWith<DataIntegrityViolationException> {
            userRepository.saveAndFlush(user(username = "Jun227", email = "b@dsm.hs.kr"))
        }

        assertEquals(UserUniqueConstraint.USERNAME, UserUniqueConstraint.violatedBy(e))
    }

    @Test
    fun `이메일이 겹치면 EMAIL 제약으로 구분한다`() {
        userRepository.saveAndFlush(user(username = "first", email = "a@dsm.hs.kr"))

        val e = assertFailsWith<DataIntegrityViolationException> {
            userRepository.saveAndFlush(user(username = "second", email = "a@dsm.hs.kr"))
        }

        assertEquals(UserUniqueConstraint.EMAIL, UserUniqueConstraint.violatedBy(e))
    }

    private fun user(username: String, email: String) = User(username, "encoded", 2315, "장윤호", email)

    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }
}
