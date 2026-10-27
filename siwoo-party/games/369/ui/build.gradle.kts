import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 369 박수 화면 (Android 라이브러리). 규칙은 :games:369:logic, 공통 UI 는 :common:ui
// 폴더·Gradle 경로는 "369" 이지만 Kotlin 패키지·namespace 는 숫자로 시작할 수 없어 clap369
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.siwoo.party.games.clap369"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = false
    }

    lint {
        abortOnError = false
    }

    testOptions {
        // Robolectric 화면 테스트
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.maxHeapSize = "2g" }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    implementation(project(":common:ui"))
    implementation(project(":common:logic"))
    implementation(project(":games:369:logic"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
