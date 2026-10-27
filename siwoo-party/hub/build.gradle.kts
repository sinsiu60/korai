import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.siwoo.party"
    compileSdk = 34

    defaultConfig {
        // 시우 파티 (파티 게임 모음집). 시우 마블 단독 앱(com.siwoo.marble)과 따로 설치된다
        applicationId = "com.siwoo.party"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3"
        // 네이티브(.so) 라이브러리가 하나도 없으므로 ABI 필터가 필요 없다.
        // → armeabi-v7a(32비트)를 포함한 모든 기기에 그대로 설치된다.
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 릴리스 APK도 디버그 키로 서명 (개인 배포용, 별도 키 관리 불필요)
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    buildFeatures {
        buildConfig = false
    }

    packaging {
        resources {
            excludes += setOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "DebugProbesKt.bin")
        }
    }

    lint {
        abortOnError = false
    }

    testOptions {
        // Robolectric 흐름 테스트 (허브 → 게임 → 결과 → 허브 점수 누적)
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
    // AppCompat 없이 프레임워크 위젯 + 커스텀 Canvas View.
    // games/<게임>/logic·ui 모듈은 자동으로 의존성에 들어간다 → 게임 추가 시 GameRegistry 에만 등록
    implementation(project(":common:logic"))
    implementation(project(":common:ui"))
    rootProject.file("games").listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }.forEach { g ->
        if (File(g, "ui/build.gradle.kts").exists()) implementation(project(":games:${g.name}:ui"))
        if (File(g, "logic/build.gradle.kts").exists()) implementation(project(":games:${g.name}:logic"))
    }

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
}

// 빌드된 APK 안에 lib/(네이티브 라이브러리)가 없는지 검사한다.
// 사용: ./gradlew :hub:checkNoNativeLibs
tasks.register("checkNoNativeLibs") {
    dependsOn("assembleDebug", "assembleRelease")
    doLast {
        val apks = fileTree(layout.buildDirectory.dir("outputs/apk")) { include("**/*.apk") }.files
        if (apks.isEmpty()) throw GradleException("APK를 찾지 못했습니다.")
        apks.sortedBy { it.name }.forEach { apk ->
            val libs = ZipFile(apk).use { zip ->
                zip.entries().asSequence().map { it.name }.filter { it.startsWith("lib/") }.toList()
            }
            if (libs.isNotEmpty()) {
                throw GradleException("${apk.name}에 네이티브 라이브러리가 있습니다: $libs")
            }
            println("OK  ${apk.name}  (${apk.length() / 1024} KB)  lib/ 폴더 없음")
        }
    }
}
