plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "allmart"
version = "0.0.1-SNAPSHOT"
description = "chat-service"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

extra["springCloudVersion"] = "2025.1.0"
extra["springAiVersion"] = "2.0.0-M2"

dependencies {
    // WebFlux — SSE 스트리밍
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-bus-kafka")

    // Spring AI — Anthropic (Claude) + Tool Calling
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    // Spring AI — MCP 서버 (WebFlux SSE 트랜스포트)
    // Claude.ai Desktop 등 MCP 클라이언트가 allmart 쇼핑 툴에 직접 연결
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")

    // Monitoring
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")

    // 세션 TTL 만료 (30분 비활성 시 자동 제거)
    implementation("com.google.guava:guava:33.4.8-jre")

    // .env 로딩은 DotEnvPostProcessor (allmart.chatservice.config) 가 담당 — spring-dotenv 제거 (Spring Boot 4.x 미호환)

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}
