plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("info.solidsoft.pitest") version "1.19.0"
}

group = "app.xquare"
version = "0.0.1-SNAPSHOT"
description = "xquare-openapi-backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    runtimeOnly("com.mysql:mysql-connector-j")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("com.h2database:h2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

pitest {
    pitestVersion = "1.30.0"
    junit5PluginVersion = "1.2.3"
    // config는 Spring 컨텍스트 시작 시 한 번만 실행돼 캐시된 컨텍스트에서는 뮤턴트가 실행되지 않으므로 제외한다.
    targetClasses = listOf("app.xquare.xquareopenapi.domain.*", "app.xquare.xquareopenapi.global.*")
    threads = 4
    // Kotlin 컴파일러가 넣는 null 체크 호출과 로그 호출을 지우는 뮤턴트는 동작이 바뀌지 않으므로 제외한다.
    // 이 값을 지정하면 PIT 기본값(로깅 라이브러리 제외)이 덮어써지므로 org.slf4j도 직접 넣는다.
    avoidCallsTo = setOf("kotlin.jvm.internal", "org.slf4j")
    outputFormats = listOf("HTML", "XML")
    timestampedReports = false
}

tasks.withType<Test> {
    useJUnitPlatform()
}
