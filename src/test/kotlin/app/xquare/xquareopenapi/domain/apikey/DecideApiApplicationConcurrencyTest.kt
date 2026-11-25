package app.xquare.xquareopenapi.domain.apikey

import org.springframework.boot.test.context.SpringBootTest

// 테스트 기본 DB(H2)에서 관리자 동시 처리 시나리오를 돌린다.
@SpringBootTest
class DecideApiApplicationConcurrencyTest : DecideApiApplicationConcurrencyScenarios()
