package app.xquare.xquareopenapi.domain.auth.repository

interface EmailVerifiedTokenRepository {
    fun findEmailByToken(token: String): String?
    fun delete(token: String)
}
