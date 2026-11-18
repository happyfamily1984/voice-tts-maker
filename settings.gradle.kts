// Maven Central이 간헐적으로 404를 돌려주는 환경이 있어, 구글이 운영하는 공식 미러를 먼저 찾는다.
val centralMirror = "https://maven-central.storage-download.googleapis.com/maven2"

pluginManagement {
    repositories {
        google()
        maven("https://maven-central.storage-download.googleapis.com/maven2")
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven(centralMirror)
        mavenCentral()
    }
}
rootProject.name = "voice-tts-maker"
include(":app")
