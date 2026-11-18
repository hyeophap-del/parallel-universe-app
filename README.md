# 평행우주 (Parallel Universe)

Android 공식 **업무 프로필(Managed Profile)** 기능으로 분리된 "평행 공간"을 만들어, 같은 앱(예: 디스코드)을 두 개 동시에 실행하는 앱입니다.
가상화나 hooking을 쓰지 않으며 원본 앱도 수정하지 않습니다.

## 동작 방식
1. **평행 공간 만들기**: 시스템 마법사(`ACTION_PROVISION_MANAGED_PROFILE`)가 업무 프로필을 만들고, 이 앱이 그 프로필의 Profile Owner가 됩니다.
2. **개인 공간 ↔ 평행 공간 통신(`LocalBridge`)**:
   - 개인 공간 앱이 127.0.0.1에 일회용 서버를 엽니다.
   - `CrossProfileApps.startMainActivity`로 평행 공간 쪽 앱을 깨웁니다.
   - 평행 공간 쪽 앱이 서버에 접속해 명령을 받습니다.
   - cross-profile intent filter를 쓰지 않는 이유: 일부 기기(Galaxy · Android 16에서 확인)는 이 필터를 오류 없이 받아 놓고도 적용하지 않습니다.
3. **복제**:
   - 원본 앱의 APK(본체와 분할 파일)를 위 통로로 보내 프로필 안에서 `PackageInstaller`로 설치합니다.
   - 설치할 때마다 시스템 설치 확인 창이 뜨고, 처음 한 번은 '이 출처 허용'을 켜야 합니다.
   - `installExistingPackage`를 쓰지 않는 이유: 기기 소유자나 제휴된 프로필 관리자만 쓸 수 있어서, 사용자가 만든 업무 프로필에서는 동작하지 않습니다.
4. 두 앱은 서로 다른 Android 사용자 공간에서 실행됩니다. 로그인, 데이터, 푸시가 모두 분리되므로 동시 실행과 양쪽 알림이 가능합니다.
5. **평행파일**:
   - 앱의 [평행파일] 화면이 열려 있는 동안 휴대폰이 Wi-Fi 주소에 작은 웹 서버를 엽니다.
   - PC는 브라우저로 그 주소에 들어가 PIN을 입력합니다. PC에 따로 설치할 것은 없습니다.
   - PC에서 보낸 파일은 휴대폰의 `Download/평행파일`에 저장됩니다.
   - 휴대폰에서 고른 파일은 PC 브라우저에서 내려받을 수 있습니다.

## 빌드
Android Studio(Ladybug 이상)에서 이 폴더를 열면 Gradle 동기화가 자동으로 진행됩니다.
- JDK 17, compileSdk 35, minSdk 28(Android 9)
- 명령줄: Studio에서 Gradle wrapper를 생성한 뒤 `./gradlew assembleDebug`, `./gradlew test`를 실행합니다.

## 릴리스
`v`로 시작하는 태그를 푸시하면 GitHub Actions(`.github/workflows/release.yml`)가 디버그 APK를 빌드해 GitHub Release를 만들고 `parallel-universe-<태그>.apk`를 첨부합니다.
```sh
git tag v0.2.0 && git push origin v0.2.0
```
- 저장소에 들어 있는 고정 디버그 키로 서명하므로, 이전 릴리스나 CI 빌드 위에 그대로 업데이트 설치할 수 있습니다.
- 태그를 올리기 전에 `app/build.gradle.kts`의 `versionCode`/`versionName`을 올려 두세요.

## 자동 E2E 테스트
`.github/workflows/e2e.yml`이 에뮬레이터(API 34)에서 `e2e/run.sh`를 실행해 복제 흐름 전체를 확인합니다.
1. 프로비저닝 마법사 없이 셸 명령으로 업무 프로필을 만들고 이 앱을 Profile Owner로 지정합니다.
2. 개인 공간에만 픽스처 앱(`e2e/fixture`)을 설치합니다.
3. 앱 화면에서 픽스처의 "복제"를 누르고, 설치 확인 창과 '알 수 없는 앱 설치' 설정 화면을 자동으로 처리합니다.
4. 픽스처가 프로필 사용자에 설치되었는지 확인합니다.

실패하면 스크린샷, UI 덤프, logcat이 `e2e-out` 아티팩트로 올라갑니다. 직접 실행할 때는 에뮬레이터를 연결한 뒤 다음을 실행합니다(프로필을 새로 만들므로 실제 휴대폰에서는 쓰지 마세요).
```sh
gradle :app:assembleDebug :e2e-fixture:assembleDebug
bash e2e/run.sh
```

## 수동 테스트
에뮬레이터는 Google Play 이미지 API 34 이상을 권장합니다.
```sh
adb install app/build/outputs/apk/debug/app-debug.apk
# 앱 실행 → "평행 공간 만들기" 진행 후
adb shell pm list users                            # 프로필 사용자(예: 10)가 생겼는지 확인
adb shell dumpsys device_policy | grep -i owner    # 이 앱이 Profile Owner인지 확인
# 앱에서 디스코드 "복제" 후
adb shell pm list packages --user 10 | grep discord
```
E2E 확인 절차:
1. 개인 공간의 디스코드는 A 계정, 평행 공간의 디스코드는 B 계정으로 로그인합니다.
2. 두 앱을 모두 백그라운드로 보낸 뒤, 각 계정으로 DM을 보냅니다.
3. 양쪽에서 알림이 오는지 확인합니다.

## 제약
- 업무 프로필은 **기기당 1개**만 만들 수 있습니다. 회사 업무 프로필이나 Shelter/Island 같은 앱이 만든 공간이 있으면 사용할 수 없습니다.
- 같은 앱은 최대 2개(개인 1 + 평행 공간 1)까지 실행할 수 있습니다.
- "업무 앱 일시중지"를 켜면 복제한 앱이 실행되지 않고 알림도 오지 않습니다.
- Play 스토어에 출시하기 전에 Device Admin / DPC 관련 정책과 권한 선언 요건을 반드시 확인해야 합니다.
