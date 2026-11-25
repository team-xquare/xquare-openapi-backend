package app.xquare.xquareopenapi.domain.apikey

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

private val SEOUL = ZoneId.of("Asia/Seoul")

// 저장한 한국 시간을 응답에 내보낼 때 +09:00 오프셋을 붙인다. 문서의 RFC 3339 date-time 형식에 맞추려는 것이다.
// 저장 칼럼은 오프셋 없는 한국 시간(LocalDateTime) 그대로 둔다.
fun LocalDateTime.withSeoulOffset(): OffsetDateTime = atZone(SEOUL).toOffsetDateTime()
