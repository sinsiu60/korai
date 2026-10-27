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

rootProject.name = "SiwooParty"

// 순수 Kotlin(JVM) 로직 모듈: Android SDK 없이도 테스트 가능
//   :common:logic        세션·점수·벌칙 문구·터치 구역
//   :games:<게임>:logic   게임별 규칙 엔진
// Android 모듈 (SDK 필요)
//   :common:ui           공통 UI (테마·버튼·팝업·연출·결과 화면·룰렛·규칙 카드…)
//   :games:<게임>:ui      게임 화면
//   :hub                 앱 (로고→타이틀→허브)
// SDK 없는 환경에서 로직 테스트만: ./gradlew -PcoreOnly :common:logic:test :games:marble:logic:test
// games/<게임>/logic·ui 폴더가 있으면 자동으로 포함된다 (게임 추가 시 이 파일은 고치지 않아도 됨)
val coreOnly = providers.gradleProperty("coreOnly").isPresent
val games = file("games").listFiles { f -> f.isDirectory }.orEmpty().map { it.name }.sorted()
include(":common:logic")
for (g in games) if (file("games/$g/logic/build.gradle.kts").exists()) include(":games:$g:logic")
if (!coreOnly) {
    include(":common:ui")
    for (g in games) if (file("games/$g/ui/build.gradle.kts").exists()) include(":games:$g:ui")
    include(":hub")
}
