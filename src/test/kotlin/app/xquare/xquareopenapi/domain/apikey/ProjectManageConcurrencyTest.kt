package app.xquare.xquareopenapi.domain.apikey

import app.xquare.xquareopenapi.domain.auth.FakeAuthRepositories
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

// 테스트 기본 DB(H2)에서 재발급·삭제 동시성 시나리오를 돌린다.
@SpringBootTest
@Import(FakeAuthRepositories::class)
class ProjectManageConcurrencyTest : ProjectManageConcurrencyScenarios()
