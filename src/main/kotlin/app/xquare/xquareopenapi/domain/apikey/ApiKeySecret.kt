package app.xquare.xquareopenapi.domain.apikey

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

// 키 원문은 발급 응답에서 한 번만 보여 주고, DB에는 hash()한 값만 둔다.
// 원문이 무작위 256비트라 솔트 없는 SHA-256으로도 원문을 거꾸로 찾을 수 없다.
object ApiKeySecret {
    private const val PREFIX = "xq_"
    private const val RANDOM_BYTES = 32
    private val random = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(RANDOM_BYTES)
        random.nextBytes(bytes)
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun hash(rawKey: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(rawKey.toByteArray()))
}
