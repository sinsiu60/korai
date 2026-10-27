import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 뱀과 사다리 화면 (Android 라이브러리). 규칙은 :games:snakeladder:logic, 공통 UI 는 :common:ui
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.siwoo.party.games.snakeladder"
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
    implementation(project(":games:snakeladder:logic"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
