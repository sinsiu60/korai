// 플러그인 버전은 이곳 한 군데서 관리한다.
// coreOnly 모드에서는 Android Gradle Plugin을 아예 내려받지 않는다.
buildscript {
    val coreOnly = project.hasProperty("coreOnly")
    dependencies {
        if (!coreOnly) classpath("com.android.tools.build:gradle:8.7.3")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    }
    repositories {
        google()
        mavenCentral()
    }
}
