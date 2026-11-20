package app.xquare.xquareopenapi.domain.apiusage

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor

interface ApiUsageLogRepository : JpaRepository<ApiUsageLog, Long>, JpaSpecificationExecutor<ApiUsageLog>
