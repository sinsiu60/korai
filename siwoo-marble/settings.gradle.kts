pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SiwooMarble"

// :core  = 순수 Kotlin(JVM) 규칙 엔진. Android SDK 없이도 테스트 가능.
// :app   = 안드로이드 UI. Android SDK 필요.
// SDK 없는 환경에서 엔진 테스트만 돌릴 때: ./gradlew -PcoreOnly :core:test
include(":core")
if (!providers.gradleProperty("coreOnly").isPresent) {
    include(":app")
}
