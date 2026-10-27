import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 순수 Kotlin 공통 로직: 세션·점수 규칙·벌칙 문구·터치 구역 계산·JSON.
// Android 의존성이 없어 PC(JVM)에서 바로 단위 테스트가 돈다.
plugins {
    kotlin("jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
