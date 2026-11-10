package app.xquare.xquareopenapi.domain.auth.usecase

import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

// IP로 해석되지 않는 remoteAddr를 모으는 키. 값마다 키를 따로 만들면 위조한 값을 바꿔 가며 IP 상한을 피할 수 있으므로
// 해석되지 않는 값은 모두 이 키 하나로 세어 같은 상한을 함께 받게 한다.
internal const val INVALID_IP_KEY = "invalid"

private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

// OTP 발송 IP 상한에 쓰는 키. IPv6는 보통 /64 하나를 한 사용자가 받아 주소를 마음대로 바꿀 수 있으므로 /64 접두사로 묶는다.
// IPv4는 그대로 두고, IPv4-mapped IPv6(::ffff:a.b.c.d)는 IPv4로 바꾼다. 같은 주소의 압축 표기 차이는 InetAddress 파싱으로 흡수한다.
internal fun otpIpKey(ip: String): String {
    if (!ip.contains(':')) return ipv4Key(ip)
    // 대괄호로 감싸면 InetAddress가 IPv6 리터럴로만 해석해 DNS 조회를 하지 않는다. 존 ID(%eth0)는 접두사와 관계없어 뗀다.
    val literal = ip.removePrefix("[").removeSuffix("]").substringBefore('%')
    val address = try {
        InetAddress.getByName("[$literal]")
    } catch (e: UnknownHostException) {
        return INVALID_IP_KEY
    }
    if (address is Inet4Address) return address.hostAddress
    val prefix = address.address.copyOf()
    prefix.fill(0, 8, 16)
    return InetAddress.getByAddress(prefix).hostAddress + "/64"
}

// IPv4는 InetAddress에 넘기면 호스트 이름으로 보고 DNS를 조회할 수 있어 직접 검사한다. 앞자리 0은 지워 같은 주소를 같은 키로 만든다.
private fun ipv4Key(ip: String): String {
    val match = IPV4.matchEntire(ip) ?: return INVALID_IP_KEY
    val octets = match.groupValues.drop(1).map { it.toInt() }
    if (octets.any { it > 255 }) return INVALID_IP_KEY
    return octets.joinToString(".")
}
