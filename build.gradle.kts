plugins {
    java
    id("org.springframework.boot") version "3.4.5"
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
}

extra["springCloudVersion"] = "2024.0.1"
extra["springAiVersion"] = "1.0.0"

dependencies {
    // WebFlux — SSE 스트리밍
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.cloud:spring-cloud-starter-bus-kafka")

    // Spring AI — Anthropic (Claude) + Tool Calling
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    // Monitoring
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")
    implementation("com.github.loki4j:loki-logback-appender:1.5.2")

    // 세션 TTL 만료 (30분 비활성 시 자동 제거)
    implementation("com.google.guava:guava:33.4.8-jre")

    // .env 파일 자동 로딩 (로컬 개발용 API 키 주입)
    implementation("me.paulschwarz:spring-dotenv:4.0.0")

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
