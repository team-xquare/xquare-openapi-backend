package app.xquare.xquareopenapi.domain.auth

import app.xquare.xquareopenapi.domain.auth.usecase.otpIpKey
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class OtpIpKeyTest {
    @Test
    fun `IPv4는 그대로 쓴다`() {
        assertEquals("203.0.113.7", otpIpKey("203.0.113.7"))
        assertEquals("127.0.0.1", otpIpKey("127.0.0.1"))
    }

    @Test
    fun `IPv6는 같은 64비트 접두사면 같은 키다`() {
        val key = otpIpKey("2001:db8:1:2:3:4:5:6")
        assertEquals("2001:db8:1:2:0:0:0:0/64", key)
        assertEquals(key, otpIpKey("2001:db8:1:2:ffff:ffff:ffff:ffff"))
        assertEquals(key, otpIpKey("2001:db8:1:2::1"))
        assertNotEquals(key, otpIpKey("2001:db8:1:3::1"))
        assertNotEquals(key, otpIpKey("2002:db8:1:2:3:4:5:6"))
    }

    @Test
    fun `압축 표기 대문자 앞자리 0 대괄호 존 ID가 달라도 같은 키다`() {
        val key = "2001:db8:0:0:0:0:0:0/64"
        for (ip in listOf("2001:db8::1", "2001:DB8::abcd", "2001:0db8:0000:0000:0000:0000:0000:0001", "[2001:db8::1]", "2001:db8::1%eth0", "2001:db8::1%2")) {
            assertEquals(key, otpIpKey(ip), ip)
        }
        assertEquals("0:0:0:0:0:0:0:0/64", otpIpKey("0:0:0:0:0:0:0:1"))
        assertEquals("fe80:0:0:0:0:0:0:0/64", otpIpKey("fe80::1%eth0"))
    }

    @Test
    fun `IPv4-mapped IPv6는 IPv4로 바꾼다`() {
        assertEquals("203.0.113.7", otpIpKey("::ffff:203.0.113.7"))
        assertEquals("203.0.113.7", otpIpKey("0:0:0:0:0:ffff:cb00:7107"))
        assertEquals("203.0.113.7", otpIpKey("[::FFFF:203.0.113.7]"))
    }

    @Test
    fun `IP로 해석되지 않는 값은 모두 invalid 키 하나로 모으고 DNS 조회를 하지 않는다`() {
        for (ip in listOf("localhost:8080", "zz::1", "2001:db8::g", "unknown", "", "localhost", "example.com",
            "1.2.3", "1.2.3.4.5", "256.0.0.1", "1.2.3.-4", "1.2.3.4 ", "[1.2.3.4]", "1.2.3.0004", "random-${System.nanoTime()}")) {
            assertEquals("invalid", otpIpKey(ip), ip)
        }
    }

    @Test
    fun `IPv4 앞자리 0은 지워 같은 키로 만든다`() {
        assertEquals("10.0.0.1", otpIpKey("010.000.000.001"))
        assertEquals("0.0.0.0", otpIpKey("0.0.0.0"))
        assertEquals("255.255.255.255", otpIpKey("255.255.255.255"))
    }
}
