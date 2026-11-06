package app.xquare.xquareopenapi.domain.auth.usecase

import java.net.Inet4Address
import java.net.InetAddress
import java.net.UnknownHostException

// OTP 발송 IP 상한에 쓰는 키. IPv6는 보통 /64 하나를 한 사용자가 받아 주소를 마음대로 바꿀 수 있으므로 /64 접두사로 묶는다.
// IPv4는 그대로 두고, IPv4-mapped IPv6(::ffff:a.b.c.d)는 IPv4로 바꾼다. 같은 주소의 압축 표기 차이는 InetAddress 파싱으로 흡수한다.
internal fun otpIpKey(ip: String): String {
    if (!ip.contains(':')) return ip
    // 대괄호로 감싸면 InetAddress가 IPv6 리터럴로만 해석해 DNS 조회를 하지 않는다. 존 ID(%eth0)는 접두사와 관계없어 뗀다.
    val literal = ip.removePrefix("[").removeSuffix("]").substringBefore('%')
    val address = try {
        InetAddress.getByName("[$literal]")
    } catch (e: UnknownHostException) {
        return ip
    }
    if (address is Inet4Address) return address.hostAddress
    val prefix = address.address.copyOf()
    prefix.fill(0, 8, 16)
    return InetAddress.getByAddress(prefix).hostAddress + "/64"
}
