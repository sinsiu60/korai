import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.siwoo.marble"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.siwoo.marble"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    // AndroidX/AppCompat 없이 프레임워크 위젯 + 커스텀 Canvas View만 사용 (APK 크기·메모리 절약)
    implementation(project(":core"))
}

// 빌드된 APK 안에 lib/(네이티브 라이브러리)가 없는지 검사한다.
// 사용: ./gradlew :app:checkNoNativeLibs
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
