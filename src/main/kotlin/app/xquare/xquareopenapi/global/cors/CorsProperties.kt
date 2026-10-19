package app.xquare.xquareopenapi.global.cors

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.cors.CorsConfiguration
import java.time.Duration

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
) {
    // 토큰은 Authorization 헤더로만 주고받으므로 쿠키(credentials)는 허용하지 않는다.
    fun toCorsConfiguration() = CorsConfiguration().apply {
        allowedOrigins = this@CorsProperties.allowedOrigins
        allowedMethods = ALLOWED_METHODS
        allowedHeaders = ALLOWED_HEADERS
        setMaxAge(MAX_AGE)
    }

    companion object {
        private val ALLOWED_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
        private val ALLOWED_HEADERS = listOf("Authorization", "Content-Type")
        private val MAX_AGE = Duration.ofHours(1)
    }
}
