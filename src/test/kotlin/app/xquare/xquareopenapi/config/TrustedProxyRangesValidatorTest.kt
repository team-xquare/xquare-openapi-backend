package app.xquare.xquareopenapi.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties
import org.springframework.context.annotation.Configuration
import org.springframework.core.NestedExceptionUtils
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrustedProxyRangesValidatorTest {

    @Test
    fun `기본값과 CIDR 목록은 통과한다`() {
        TrustedProxyRangesValidator(TomcatServerProperties())
        TrustedProxyRangesValidator(properties(internal = "10.0.0.0/8, 192.168.0.0/16,fc00::/7", trusted = "198.51.100.0/24, 203.0.113.9/32"))
        // trusted-proxies는 외부 프록시가 없으면 비워 둔다.
        TrustedProxyRangesValidator(properties(internal = "10.0.0.5/32", trusted = ""))
    }

    @Test
    fun `internal-proxies가 빈 값이면 앱이 뜨지 않는다`() {
        listOf("", "   ").forEach { blank ->
            val e = assertFailsWith<IllegalStateException> { TrustedProxyRangesValidator(properties(internal = blank)) }
            assertContains(e.message!!, "SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES")
        }
    }

    @Test
    fun `CIDR이 아닌 값이 섞여 있으면 어느 값인지 알려 주고 앱이 뜨지 않는다`() {
        // "/"가 없으면 Tomcat이 정규식으로 읽는다. "/"가 있어도 길이가 틀리면 CIDR이 아니다.
        listOf("10.0.0.5", "10.0.0.0/8, 192.168.1.5", "10.0.0.0/33", "10.0.0.0/8,").forEach { value ->
            val e = assertFailsWith<IllegalStateException> { TrustedProxyRangesValidator(properties(internal = value)) }
            assertContains(e.message!!, "server.tomcat.remoteip.internal-proxies")
        }
        val e = assertFailsWith<IllegalStateException> {
            TrustedProxyRangesValidator(properties(internal = "10.0.0.0/8", trusted = "198.51.100.5"))
        }
        assertContains(e.message!!, "server.tomcat.remoteip.trusted-proxies")
        assertContains(e.message!!, "[198.51.100.5]")
    }

    // 환경변수를 빈 문자열로 주면 application.yaml의 기본값이 아니라 ""가 들어온다는 전제까지 실제 설정 파일로 확인한다.
    @Test
    fun `환경변수가 빈 문자열이면 기본값으로 돌아가지 않고 시작이 실패한다`() {
        runner.withPropertyValues("SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES=").run { context ->
            val failure = assertNotNull(context.startupFailure)
            assertContains(NestedExceptionUtils.getMostSpecificCause(failure).message!!, "SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES")
        }
    }

    @Test
    fun `환경변수를 주지 않으면 application yaml의 기본 대역으로 시작한다`() {
        runner.run { context ->
            assertNull(context.startupFailure)
            assertEquals(
                TomcatServerProperties().remoteip.internalProxies,
                context.getBean(TomcatServerProperties::class.java).remoteip.internalProxies,
            )
        }
    }

    private val runner = ApplicationContextRunner()
        .withInitializer(ConfigDataApplicationContextInitializer())
        .withUserConfiguration(PropertiesConfig::class.java, TrustedProxyRangesValidator::class.java)

    private fun properties(internal: String, trusted: String? = null) = TomcatServerProperties().apply {
        remoteip.internalProxies = internal
        remoteip.trustedProxies = trusted
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(TomcatServerProperties::class)
    class PropertiesConfig
}
