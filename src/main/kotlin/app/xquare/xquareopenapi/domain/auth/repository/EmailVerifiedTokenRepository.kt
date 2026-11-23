package app.xquare.xquareopenapi.domain.auth.repository

import java.time.Duration

interface EmailVerifiedTokenRepository {
    fun save(token: String, email: String, ttl: Duration)
    fun findEmailByToken(token: String): String?

    // 읽기와 삭제를 한 번에 해서 이메일을 돌려준다. 같은 토큰을 동시에 소비해도 하나만 이메일을 받고 나머지는 null이다.
    fun consume(token: String): String?

    fun delete(token: String)
}
