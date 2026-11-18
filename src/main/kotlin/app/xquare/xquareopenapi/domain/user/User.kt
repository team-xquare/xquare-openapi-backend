package app.xquare.xquareopenapi.domain.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
// 제약 이름은 UserUniqueConstraint가 중복 원인을 구분하는 데 쓰므로 직접 정한다.
@Table(
    name = "users",
    uniqueConstraints = [
        UniqueConstraint(name = User.USERNAME_UNIQUE_CONSTRAINT, columnNames = ["username"]),
        UniqueConstraint(name = User.EMAIL_UNIQUE_CONSTRAINT, columnNames = ["email"]),
    ],
)
class User(
    @Column(nullable = false, length = 15)
    val username: String,

    password: String,

    @Column(nullable = false)
    val studentNumber: Int,

    @Column(nullable = false, length = 10)
    val name: String,

    @Column(nullable = false)
    val email: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: Role = Role.USER,
) {
    // 비밀번호 재설정에서만 바꾼다. 인코딩된 해시만 받는다.
    @Column(nullable = false, length = 60)
    var password: String = password
        protected set

    fun changePassword(encodedPassword: String) {
        password = encodedPassword
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    companion object {
        const val USERNAME_UNIQUE_CONSTRAINT = "uk_users_username"
        const val EMAIL_UNIQUE_CONSTRAINT = "uk_users_email"
    }
}
