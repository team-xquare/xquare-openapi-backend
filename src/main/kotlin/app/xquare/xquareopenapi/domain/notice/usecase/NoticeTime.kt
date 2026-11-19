package app.xquare.xquareopenapi.domain.notice.usecase

import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

// 작성·수정일시는 Clock 빈의 서울 시간으로 초까지만 남긴다(명세 예시 '2026-09-01T09:00:00').
internal fun now(clock: Clock): LocalDateTime = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS)
