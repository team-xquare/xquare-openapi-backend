package app.xquare.xquareopenapi.domain.user

import org.hibernate.exception.ConstraintViolationException
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertNull

// 제약 이름은 MySQL(UserUniqueConstraintMySqlTest)과 H2에서 실제로 받은 값이다.
class UserUniqueConstraintTest {

    @Test
    fun `MySQL이 돌려주는 제약 이름으로 겹친 값을 구분한다`() {
        assertEquals(UserUniqueConstraint.USERNAME, violatedBy("users.uk_users_username"))
        assertEquals(UserUniqueConstraint.EMAIL, violatedBy("users.uk_users_email"))
    }

    @Test
    fun `H2가 돌려주는 제약 이름으로 겹친 값을 구분한다`() {
        assertEquals(UserUniqueConstraint.USERNAME, violatedBy("PUBLIC.UK_USERS_USERNAME INDEX PUBLIC.UK_USERS_USERNAME_INDEX_4"))
        assertEquals(UserUniqueConstraint.EMAIL, violatedBy("PUBLIC.UK_USERS_EMAIL INDEX PUBLIC.UK_USERS_EMAIL_INDEX_4"))
    }

    @Test
    fun `users의 unique 제약이 아니면 구분하지 않는다`() {
        assertNull(violatedBy("PRIMARY"))
        assertNull(violatedBy(null))
        assertNull(UserUniqueConstraint.violatedBy(DataIntegrityViolationException("not null", IllegalStateException())))
    }

    private fun violatedBy(constraintName: String?) =
        UserUniqueConstraint.violatedBy(
            DataIntegrityViolationException(
                "duplicate",
                ConstraintViolationException("duplicate", SQLException(), constraintName),
            ),
        )
}
