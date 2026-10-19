package app.xquare.xquareopenapi.domain.user

import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun existsByUsername(username: String): Boolean
    fun existsByEmail(email: String): Boolean
}
