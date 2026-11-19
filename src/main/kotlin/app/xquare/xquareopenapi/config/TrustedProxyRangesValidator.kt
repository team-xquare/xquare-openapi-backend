package app.xquare.xquareopenapi.config

import org.apache.catalina.util.NetMask
import org.springframework.boot.tomcat.autoconfigure.TomcatServerProperties
import org.springframework.context.annotation.Configuration

// OTP 발송 IP 상한은 remoteAddr로 세고, remoteAddr는 Tomcat RemoteIpValve가 신뢰 프록시 대역을 보고 정한다.
// 이 값이 잘못되면 에러 없이 IP 상한만 깨지므로, 앱이 뜨기 전에 막는다(#31 리뷰).
// - internal-proxies가 빈 값이면 Tomcat이 내부망 프록시를 하나도 믿지 않는다. 그러면 프록시(Ingress) IP 하나가 모든 사용자의 remoteAddr가 되어
//   IP 상한을 전원이 나눠 쓴다. 환경변수를 ""로 주면(Helm의 `value: ""` 등) 기본값이 아니라 빈 값이 들어온다.
// - "/"가 없는 값은 Tomcat이 CIDR이 아니라 정규식으로 읽는다. 단일 IP도 `/32`(IPv6는 `/128`)를 붙여 CIDR로 적어야 한다.
// trusted-proxies는 기본값이 없어서 빈 값을 허용한다(외부 프록시가 없다는 뜻).
@Configuration
class TrustedProxyRangesValidator(tomcat: TomcatServerProperties) {

    init {
        val remoteIp = tomcat.remoteip
        check(!remoteIp.internalProxies.isNullOrBlank()) {
            "$INTERNAL_PROXIES 가 빈 값입니다. 기본 내부망 대역을 쓰려면 환경변수 $INTERNAL_PROXIES_ENV 를 아예 지우고, " +
                "바꾸려면 쉼표로 구분한 CIDR을 넣으세요."
        }
        requireCidrs(INTERNAL_PROXIES, remoteIp.internalProxies)
        remoteIp.trustedProxies?.takeIf { it.isNotBlank() }?.let { requireCidrs(TRUSTED_PROXIES, it) }
    }

    private fun requireCidrs(property: String, value: String) {
        val invalid = value.split(",").map { it.trim() }.filterNot(::isCidr)
        check(invalid.isEmpty()) {
            "$property 에 CIDR이 아닌 값이 있습니다: $invalid. \"/\"가 없는 값은 Tomcat이 정규식으로 읽으므로 단일 IP도 /32(IPv6는 /128)를 붙이세요."
        }
    }

    private fun isCidr(entry: String): Boolean =
        "/" in entry && runCatching { NetMask(entry) }.isSuccess

    companion object {
        private const val INTERNAL_PROXIES = "server.tomcat.remoteip.internal-proxies"
        private const val TRUSTED_PROXIES = "server.tomcat.remoteip.trusted-proxies"
        private const val INTERNAL_PROXIES_ENV = "SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES"
    }
}
