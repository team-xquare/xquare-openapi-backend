package app.xquare.xquareopenapi.global.cors

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import org.springframework.http.HttpMethod
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CorsPropertiesTest {

    @Test
    fun `쉼표로 구분한 origin을 목록으로 받는다`() {
        val properties = bind("http://localhost:3000,https://xquare.app")

        assertEquals(listOf("http://localhost:3000", "https://xquare.app"), properties.allowedOrigins)
    }

    @Test
    fun `origin을 비워 두면 어떤 origin도 허용하지 않는다`() {
        val configuration = bind("").toCorsConfiguration()

        assertNull(configuration.checkOrigin("http://localhost:3000"))
    }

    @Test
    fun `허용한 origin만 통과시킨다`() {
        val configuration = CorsProperties(listOf("http://localhost:3000")).toCorsConfiguration()

        assertEquals("http://localhost:3000", configuration.checkOrigin("http://localhost:3000"))
        assertNull(configuration.checkOrigin("http://evil.com"))
    }

    @Test
    fun `REST 메서드와 인증 헤더를 허용하고 preflight 결과를 1시간 캐시한다`() {
        val configuration = CorsProperties(listOf("http://localhost:3000")).toCorsConfiguration()

        assertEquals(listOf("GET", "POST", "PUT", "PATCH", "DELETE"), configuration.allowedMethods)
        assertNull(configuration.checkHttpMethod(HttpMethod.TRACE))
        assertEquals(listOf("Authorization", "Content-Type"), configuration.allowedHeaders)
        assertEquals(3600L, configuration.maxAge)
        assertNull(configuration.allowCredentials)
    }

    private fun bind(allowedOrigins: String): CorsProperties =
        Binder(MapConfigurationPropertySource(mapOf("cors.allowed-origins" to allowedOrigins)))
            .bindOrCreate("cors", CorsProperties::class.java)
}
