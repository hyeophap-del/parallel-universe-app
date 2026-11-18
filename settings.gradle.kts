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

rootProject.name = "ParallelUniverse"
include(":app")

// E2E 테스트 픽스처 앱(e2e/run.sh가 사용)
include(":e2e-fixture")
project(":e2e-fixture").projectDir = file("e2e/fixture")
