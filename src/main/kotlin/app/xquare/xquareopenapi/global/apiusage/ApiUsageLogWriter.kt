package app.xquare.xquareopenapi.global.apiusage

import app.xquare.xquareopenapi.domain.apiusage.ApiUsageLog
import app.xquare.xquareopenapi.domain.apiusage.usecase.RecordApiUsageUseCase
import org.slf4j.LoggerFactory
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class ApiUsageLogWriter(private val recorder: RecordApiUsageUseCase, queueCapacity: Int) : AutoCloseable {
    private val dropped = AtomicLong()
    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.SECONDS,
        ArrayBlockingQueue(queueCapacity),
        { task -> Thread(task, "api-usage-log-writer").apply { isDaemon = true } },
        // 요청 스레드에서는 DB, 큐 공간, 로그 출력 모두 기다리지 않는다.
        { _, _ -> dropped.incrementAndGet() },
    )

    fun submit(log: ApiUsageLog) {
        executor.execute {
            try {
                recorder.execute(log)
            } catch (_: RuntimeException) {
                logger.warn("API_USAGE_LOG_WRITE_FAILED")
            } finally {
                reportDrops()
            }
        }
    }

    private fun reportDrops() {
        val count = dropped.getAndSet(0)
        if (count > 0) logger.warn("API_USAGE_LOG_QUEUE_FULL dropped={}", count)
    }

    override fun close() {
        val pending = executor.shutdownNow().size
        reportDrops()
        if (pending > 0) logger.warn("API_USAGE_LOG_SHUTDOWN_DROPPED dropped={}", pending)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ApiUsageLogWriter::class.java)
    }
}