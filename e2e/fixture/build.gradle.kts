// E2E 테스트 전용 최소 앱: 개인 공간에만 설치해 두고 "복제"가 평행 공간에 설치하는지 확인한다.
plugins {
    id("com.android.application")
}

android {
    namespace = "app.paralleluniverse.e2e.fixture"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.paralleluniverse.e2e.fixture"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
