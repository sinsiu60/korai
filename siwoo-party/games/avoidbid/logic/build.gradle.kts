import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 피하기 경매 규칙 (순수 Kotlin, Android 의존성 없음)
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
