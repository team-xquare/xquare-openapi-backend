package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

// 테스트 기본 DB(H2)에서 동시 신청 시나리오를 돌린다.
@SpringBootTest
@Import(FakeAuthRepositories::class)
class ProjectApplyConcurrencyTest : ProjectApplyConcurrencyScenarios()
