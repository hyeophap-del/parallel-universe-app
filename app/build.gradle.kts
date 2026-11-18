plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 버전 자동화
 * - versionCode: git 커밋 수. 새 커밋일수록 항상 커지므로 업데이트 설치가 막히지 않는다.
 *   (CI에서는 checkout에 fetch-depth: 0이 필요하다)
 * - versionName: 릴리스 워크플로가 태그에서 넘겨주는 VERSION_NAME(v0.2.0 → 0.2.0). 없으면 개발 버전 표시.
 */
val gitCommitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)
val versionNameFromTag: String = System.getenv("VERSION_NAME")?.removePrefix("v")?.takeIf { it.isNotBlank() }
    ?: "0.2.0-dev.$gitCommitCount"

/** 정식 서명 키: 릴리스 워크플로가 GitHub Secrets에서 꺼내 환경 변수로 넘긴다. 저장소에는 키가 없다. */
val releaseKeystore: String? = System.getenv("RELEASE_KEYSTORE")

android {
    namespace = "app.paralleluniverse"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.paralleluniverse"
        minSdk = 28 // CrossProfileApps
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = versionNameFromTag
    }

    signingConfigs {
        // 디버그 전용 고정 키(비밀 아님). CI와 로컬 빌드의 서명이 같아야 휴대폰에 업데이트 설치가 된다.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 코드 축소(R8)는 실기기 검증 전까지 끈다. NanoHTTPD·리플렉션 경로가 깨질 위험이 있다.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // 평행파일: 휴대폰에서 여는 작은 HTTP 서버
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    testImplementation("junit:junit:4.13.2")
}
