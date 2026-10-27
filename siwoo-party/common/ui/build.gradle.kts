import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// 공통 UI (Android 라이브러리): 테마·3D 버튼·팝업·연출(코인/별/꽃가루)·룰렛·카드·
// 결과 화면(시상대)·플레이어 설정·규칙 카드·일시정지·설정·동시 터치 구역
plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    namespace = "com.siwoo.party.common"
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
    // AppCompat 없이 프레임워크 위젯 + 커스텀 Canvas View 만 사용
    api(project(":common:logic"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}
