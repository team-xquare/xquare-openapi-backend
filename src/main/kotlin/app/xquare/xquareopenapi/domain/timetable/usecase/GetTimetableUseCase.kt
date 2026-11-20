package app.xquare.xquareopenapi.domain.timetable.usecase

import app.xquare.xquareopenapi.domain.meal.neis.NeisProperties
import app.xquare.xquareopenapi.domain.timetable.neis.NeisTimetable
import app.xquare.xquareopenapi.domain.timetable.neis.NeisTimetableClient
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableResponse
import app.xquare.xquareopenapi.domain.timetable.presentation.dto.TimetableSearchRequest
import app.xquare.xquareopenapi.global.error.ErrorCode
import app.xquare.xquareopenapi.global.error.XquareException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

// 시간표는 DB에 두지 않고 요청 때 나이스에서 가져와 10분 동안 메모리에 둔다.
// 저장하면 테이블·동기화 범위·삭제 규칙이 생기는데, 조회 전용이고 관리자가 고칠 일도 없어 그만한 이득이 없다.
// 캐시는 같은 주를 여러 학생이 연달아 볼 때 나이스를 매번 부르지 않으려는 것이다. 서버 인스턴스마다 따로 둔다.
@Service
class GetTimetableUseCase(
    private val neisTimetableClient: NeisTimetableClient,
    private val neisProperties: NeisProperties,
    private val clock: Clock,
) {
    private val cache = ConcurrentHashMap<CacheKey, CachedTimetable>()

    // date가 속한 주(월~일)의 시간표를 준다. date를 생략하면 오늘(서울 시간)이다.
    fun execute(request: TimetableSearchRequest): TimetableResponse {
        val monday = (request.date ?: LocalDate.now(clock)).with(DayOfWeek.MONDAY)
        val key = CacheKey(request.grade!!, request.classNum!!, monday)
        val now = clock.instant()
        val timetables = cache[key]?.takeIf { now.isBefore(it.expiresAt) }?.timetables ?: fetch(key).also {
            // 날짜를 바꿔 가며 부르면 키가 계속 늘어나므로, 많이 쌓이면 통째로 비운다.
            if (cache.size >= MAX_CACHE_SIZE) cache.clear()
            cache[key] = CachedTimetable(it, now.plus(CACHE_TTL))
        }
        return TimetableResponse.of(key.grade, key.classNum, monday, monday.plusDays(6), timetables)
    }

    // 실패는 캐시하지 않아 다음 요청이 다시 나이스를 부른다.
    private fun fetch(key: CacheKey): List<NeisTimetable> {
        // 키 없이 부르면 나이스가 샘플 5건만 줘서 시간표가 맞지 않는다.
        if (neisProperties.apiKey.isBlank()) {
            log.warn("NEIS_API_KEY가 없어 나이스 시간표를 가져오지 않습니다.")
            throw XquareException(ErrorCode.TIMETABLE_UNAVAILABLE)
        }
        return try {
            neisTimetableClient.fetch(key.grade, key.classNum, key.monday, key.monday.plusDays(6))
        } catch (e: RuntimeException) {
            log.warn("나이스 시간표를 가져오지 못했습니다. {}", key, e)
            throw XquareException(ErrorCode.TIMETABLE_UNAVAILABLE)
        }
    }

    private data class CacheKey(val grade: Int, val classNum: Int, val monday: LocalDate)

    private class CachedTimetable(val timetables: List<NeisTimetable>, val expiresAt: Instant)

    companion object {
        private val log = LoggerFactory.getLogger(GetTimetableUseCase::class.java)
        private val CACHE_TTL = Duration.ofMinutes(10)
        private const val MAX_CACHE_SIZE = 1000
    }
}
