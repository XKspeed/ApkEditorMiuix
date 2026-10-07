pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx/maven")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 注：原先为 ARSCLib 与 PhotoView 引入的 jitpack 已不再需要。
        // ARSCLib 改为本地 jar（app/libs/ARSCLib.jar），PhotoView 已移除。
        maven("https://maven.pkg.jetbrains.space/public/p/kotlinx/maven")
    }
}

rootProject.name = "ApkEditorMiuix"
include(":app")
