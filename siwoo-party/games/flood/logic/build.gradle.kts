import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 색 번지기 점령 규칙 (격자 만들기·흡수·순위) (순수 Kotlin, Android 의존성 없음)
// Android 의존성이 전혀 없으므로 PC(JVM)에서 바로 단위 테스트가 돈다.
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
    implementation(project(":common:logic"))
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    testLogging {
        events("passed", "failed", "skipped")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
