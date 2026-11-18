package app.xquare.xquareopenapi.domain.apikey

import java.time.LocalDateTime
import java.time.ZoneId

private val SEOUL = ZoneId.of("Asia/Seoul")

// 키 발급·신청 시각은 서버 시간대와 상관없이 한국 시간으로 남긴다.
fun nowInSeoul(): LocalDateTime = LocalDateTime.now(SEOUL)
