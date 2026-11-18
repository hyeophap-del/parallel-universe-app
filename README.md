# 평행우주 (Parallel Universe)

Android 공식 **업무 프로필(Managed Profile)** 기능으로 분리된 "평행 공간"을 만들어, 같은 앱(예: 디스코드)을 두 개 동시에 실행하는 앱입니다.
가상화나 hooking을 쓰지 않으며 원본 앱도 수정하지 않습니다.

## 동작 방식
1. **평행 공간 만들기**: 시스템 마법사(`ACTION_PROVISION_MANAGED_PROFILE`)가 업무 프로필을 만들고, 이 앱이 그 프로필의 Profile Owner가 됩니다.
2. **복제**: 개인 공간에서 요청을 보내면 프로필 안에서 `installExistingPackage`가 실행됩니다. 이미 설치된 APK를 공유하므로 따로 다운로드하지 않습니다.
3. 두 앱은 서로 다른 Android 사용자 공간에서 실행됩니다. 로그인, 데이터, 푸시가 모두 분리되므로 동시 실행과 양쪽 알림이 가능합니다.

## 빌드
Android Studio(Ladybug 이상)에서 이 폴더를 열면 Gradle 동기화가 자동으로 진행됩니다.
- JDK 17, compileSdk 35, minSdk 28(Android 9)
- 명령줄: Studio에서 Gradle wrapper를 생성한 뒤 `./gradlew assembleDebug`, `./gradlew test`를 실행합니다.

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
