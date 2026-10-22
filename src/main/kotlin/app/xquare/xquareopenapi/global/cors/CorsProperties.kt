package app.xquare.xquareopenapi.global.cors

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.cors.CorsConfiguration
import java.time.Duration

@ConfigurationProperties(prefix = "cors")
data class CorsProperties(
    val allowedOrigins: List<String> = emptyList(),
) {
    // 토큰은 Authorization 헤더로만 주고받으므로 쿠키(credentials)는 허용하지 않는다.
    // 생성 API는 Location 헤더로 새 리소스 URI를 돌려준다. 노출하지 않으면 브라우저 JS가 읽지 못한다.
    fun toCorsConfiguration(): CorsConfiguration {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins
        configuration.allowedMethods = ALLOWED_METHODS
        configuration.allowedHeaders = ALLOWED_HEADERS
        configuration.exposedHeaders = EXPOSED_HEADERS
        configuration.setMaxAge(MAX_AGE)
        return configuration
    }

    companion object {
        private val ALLOWED_METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE")
        private val ALLOWED_HEADERS = listOf("Authorization", "Content-Type")
        private val EXPOSED_HEADERS = listOf("Location")
        private val MAX_AGE = Duration.ofHours(1)
    }
}
