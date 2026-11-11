package app.xquare.xquareopenapi.domain.apikey

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ApiKeySecretTest {

    @Test
    fun `키는 xq_ 뒤에 무작위 32바이트를 URL에 안전한 base64로 붙인다`() {
        val key = ApiKeySecret.generate()

        assertTrue(key.matches(Regex("xq_[A-Za-z0-9_-]{43}")), key)
    }

    @Test
    fun `만들 때마다 다른 키가 나온다`() {
        assertNotEquals(ApiKeySecret.generate(), ApiKeySecret.generate())
    }

    @Test
    fun `해시는 SHA-256을 소문자 16진수 64자로 돌려준다`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApiKeySecret.hash("abc"))
    }
}
