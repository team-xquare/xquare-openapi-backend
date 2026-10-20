package app.xquare.xquareopenapi.global.jwt

import java.time.Duration

// 저장소 TTL이 토큰 만료 시간과 어긋나지 않도록 둘을 함께 넘긴다.
data class RefreshToken(
    val value: String,
    val ttl: Duration,
)
