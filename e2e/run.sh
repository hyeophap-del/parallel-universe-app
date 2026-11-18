#!/usr/bin/env bash
# 평행우주 E2E: 업무 프로필을 만들고(프로비저닝 UI 없이), 픽스처 앱을 "복제"해 프로필에 설치되는지 확인한다.
#
# 사용법: e2e/run.sh [APP_APK] [FIXTURE_APK]
#   기본값: app/build/outputs/apk/debug/app-debug.apk, e2e/fixture/build/outputs/apk/debug/e2e-fixture-debug.apk
# 환경 변수:
#   ANDROID_SERIAL  대상 기기(adb가 직접 읽는다)
#   E2E_OUT         스크린샷·UI 덤프·logcat 저장 위치(기본 e2e/out)
#   E2E_USER        이미 있는 평행 공간의 사용자 id. 지정하면 프로필을 새로 만들지 않는다.
#   E2E_PREGRANT    1이면 '알 수 없는 앱 설치' 권한을 미리 허용한다(기본 0: 설정 화면을 직접 거친다).
#   E2E_TIMEOUT     복제 완료까지 기다리는 시간(초, 기본 240)
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
APP_APK=${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}
FIXTURE_APK=${2:-$ROOT/e2e/fixture/build/outputs/apk/debug/e2e-fixture-debug.apk}
OUT=${E2E_OUT:-$ROOT/e2e/out}
PREGRANT=${E2E_PREGRANT:-0}
CLONE_TIMEOUT=${E2E_TIMEOUT:-240}

APP=app.paralleluniverse
ADMIN=$APP/.admin.CloneAdminReceiver
FIXTURE=app.paralleluniverse.e2e.fixture
FIXTURE_LABEL="E2E Fixture"
CLONE_TEXT="복제"

mkdir -p "$OUT"
STEP=0

log() { printf '[e2e %s] %s\n' "$(date +%H:%M:%S)" "$*"; }
fail() { log "FAIL: $*"; exit 1; }
# adb shell 출력에서 CR을 지운다.
sh_() { adb shell "$@" | tr -d '\r'; }

snap() {
    STEP=$((STEP + 1))
    name=$(printf '%02d-%s' "$STEP" "$1")
    adb exec-out screencap -p >"$OUT/$name.png" 2>/dev/null || true
}

collect() {
    status=$?
    log "collecting logcat and final screen (exit $status)"
    snap final
    adb logcat -d -v threadtime >"$OUT/logcat.txt" 2>/dev/null || true
    sh_ dumpsys user >"$OUT/dumpsys-user.txt" 2>/dev/null || true
    sh_ dumpsys package "$FIXTURE" >"$OUT/dumpsys-fixture.txt" 2>/dev/null || true
    exit $status
}
trap collect EXIT

# wait_for <설명> <초> <명령...>: 명령이 성공할 때까지 2초 간격으로 다시 시도한다.
wait_for() {
    desc=$1 timeout=$2
    shift 2
    deadline=$(($(date +%s) + timeout))
    until "$@"; do
        [ "$(date +%s)" -lt "$deadline" ] || fail "timed out after ${timeout}s: $desc"
        sleep 2
    done
}

# ---------- UI 도우미 (uiautomator dump) ----------
UI=$OUT/ui.xml
UI_N=0

# 현재 화면을 $UI에 덤프한다. 애니메이션 중이면 실패하므로 몇 번 다시 시도한다.
dump_ui() {
    for _ in 1 2 3 4 5; do
        if sh_ uiautomator dump /sdcard/e2e-ui.xml 2>&1 | grep -q 'dumped to'; then
            adb exec-out cat /sdcard/e2e-ui.xml >"$UI"
            UI_N=$((UI_N + 1))
            cp "$UI" "$OUT/ui-$(printf '%03d' "$UI_N").xml"
            return 0
        fi
        sleep 1
    done
    return 1
}

# 노드마다 한 줄로 나눈다.
nodes() { tr '>' '\n' <"$UI" | grep '<node '; }

# 텍스트(대소문자 무시, 정확히 일치)로 노드 줄을 찾는다.
nodes_with_text() { nodes | grep -i "text=\"$1\"" || true; }

# 노드 줄 → "x1 y1 x2 y2"
bounds() { sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p'; }

tap_bounds() {
    set -- $1
    x=$((($1 + $3) / 2)) y=$((($2 + $4) / 2))
    log "tap ($x,$y)"
    adb shell input tap "$x" "$y"
}

# 텍스트가 정확히 일치하고 enabled인 첫 노드를 누른다.
tap_text() {
    line=$(nodes_with_text "$1" | grep -v 'enabled="false"' | head -n 1)
    [ -n "$line" ] || return 1
    log "tap \"$1\""
    tap_bounds "$(printf '%s\n' "$line" | bounds)"
}

has_text() { [ -n "$(nodes_with_text "$1")" ]; }

# ---------- 기기 상태 ----------
user_unlocked() { sh_ am get-started-user-state "$1" 2>/dev/null | grep -q RUNNING_UNLOCKED; }
installed_for() { sh_ dumpsys package "$2" | grep -E "^ *User $1:" | grep -q 'installed=true'; }

# 업무 프로필 사용자가 활성화(FLAG_DISABLED 없음) 되었는지. dumpsys user 형식: UserInfo{10:Parallel:1030}
profile_enabled() {
    flags=$(sh_ dumpsys user | sed -n "s/.*UserInfo{$1:[^:]*:\([0-9a-fA-F]*\)}.*/\1/p" | head -n 1)
    [ -n "$flags" ] && [ $((0x$flags & 0x40)) -eq 0 ]
}

prepare_device() {
    log "waiting for device"
    adb wait-for-device
    wait_for "boot completed" 300 sh -c '[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = 1 ]'
    adb shell input keyevent KEYCODE_WAKEUP || true
    adb shell wm dismiss-keyguard || true
    adb shell svc power stayon true || true
    adb shell settings put system screen_off_timeout 1800000 || true
    for key in window_animation_scale transition_animation_scale animator_duration_scale; do
        adb shell settings put global "$key" 0 || true
    done
    adb logcat -c || true
}

create_profile() {
    log "installing $APP_APK"
    adb install -r -t "$APP_APK"

    if [ -n "${E2E_USER:-}" ]; then
        PROFILE=$E2E_USER
        log "using existing profile user $PROFILE"
    else
        out=$(sh_ pm create-user --profileOf 0 --managed Parallel)
        log "pm create-user: $out"
        PROFILE=$(printf '%s\n' "$out" | sed -n 's/.*created user id \([0-9][0-9]*\).*/\1/p')
        [ -n "$PROFILE" ] || fail "could not create managed profile"
        out=$(sh_ pm install-existing --user "$PROFILE" "$APP")
        log "pm install-existing: $out"
        installed_for "$PROFILE" "$APP" || fail "$APP not installed in profile $PROFILE"
        out=$(sh_ dpm set-profile-owner --user "$PROFILE" "$ADMIN" 2>&1)
        log "dpm set-profile-owner: $out"
        printf '%s\n' "$out" | grep -q '^Success' || fail "could not set profile owner"
    fi

    sh_ am start-user -w "$PROFILE"
    wait_for "profile user $PROFILE unlocked" 120 user_unlocked "$PROFILE"

    # 실제 프로비저닝에서는 마법사가 끝난 뒤 DPC(PolicyComplianceActivity)가 ProfileSetup.complete를 실행한다.
    # 그 Activity는 BIND_DEVICE_ADMIN으로 보호되어 셸에서 못 여니, 같은 일을 하는 프로필 쪽 MainActivity를 한 번 연다.
    # (명령을 기다리는 서버가 없으므로 안내 토스트만 띄우고 바로 닫힌다.)
    sh_ am start --user "$PROFILE" -W -n "$APP/.ui.MainActivity"
    wait_for "profile user $PROFILE enabled" 60 profile_enabled "$PROFILE"
    sh_ dumpsys user | grep -E 'UserInfo\{' || true

    if [ "$PREGRANT" = 1 ]; then
        log "pre-granting REQUEST_INSTALL_PACKAGES in profile"
        sh_ appops set --user "$PROFILE" "$APP" REQUEST_INSTALL_PACKAGES allow
    fi
}

install_fixture() {
    log "installing fixture for user 0 only"
    sh_ pm uninstall --user "$PROFILE" "$FIXTURE" >/dev/null 2>&1 || true
    adb install -r -t --user 0 "$FIXTURE_APK"
    installed_for 0 "$FIXTURE" || fail "fixture not installed for user 0"
    if installed_for "$PROFILE" "$FIXTURE"; then fail "fixture already installed in profile $PROFILE"; fi
}

# 앱 목록에서 픽스처 줄의 "복제" 버튼을 찾아 누른다. 화면 밖이면 아래로 스크롤한다.
tap_clone_button() {
    size=$(sh_ wm size | sed -n 's/.*: *\([0-9]*\)x\([0-9]*\).*/\1 \2/p' | tail -n 1)
    set -- $size
    w=$1 h=$2
    deadline=$(($(date +%s) + 90))
    swipes=0
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if dump_ui; then
            label=$(nodes_with_text "$FIXTURE_LABEL" | head -n 1 | bounds)
            if [ -n "$label" ]; then
                set -- $label
                cy=$((($2 + $4) / 2))
                # 같은 줄(세로 범위에 라벨 중심이 들어가는) "복제" 버튼
                button=$(nodes_with_text "$CLONE_TEXT" | bounds | while read -r x1 y1 x2 y2; do
                    if [ "$y1" -le "$cy" ] && [ "$cy" -le "$y2" ]; then echo "$x1 $y1 $x2 $y2"; fi
                done | head -n 1)
                if [ -n "$button" ]; then
                    snap app-list
                    tap_bounds "$button"
                    return 0
                fi
            elif has_text "$CLONE_TEXT" || [ "$swipes" -gt 0 ]; then
                # 목록은 떴는데 픽스처가 화면 밖이다.
                [ "$swipes" -lt 15 ] || fail "fixture row not found after scrolling"
                swipes=$((swipes + 1))
                adb shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 300
            fi
        fi
        sleep 2
    done
    snap no-clone-button
    fail "could not find the \"$CLONE_TEXT\" button for $FIXTURE_LABEL (is the parallel space detected?)"
}

# 시스템 설치 확인 창(프로필 안)과 '알 수 없는 앱' 설정 화면을 처리하며 설치를 기다린다.
drive_install() {
    deadline=$(($(date +%s) + CLONE_TIMEOUT))
    last=""
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if installed_for "$PROFILE" "$FIXTURE"; then return 0; fi
        if dump_ui; then
            if nodes | grep -qiE 'text="(Allow from this source|이 출처 허용)"'; then
                # 설정 화면: 스위치를 켜고 뒤로 간다.
                if nodes | grep -i 'class="android.widget.Switch"' | grep -q 'checked="false"'; then
                    [ "$last" = toggle ] || snap unknown-sources
                    tap_text "Allow from this source" || tap_text "이 출처 허용" || true
                    last=toggle
                else
                    log "unknown sources allowed; going back"
                    adb shell input keyevent KEYCODE_BACK
                    last=back
                fi
            elif tap_text "Install" || tap_text "설치"; then
                snap after-install-tap
                last=install
            elif nodes | grep -qiE 'text="(Settings|설정)"' && nodes | grep -qiE 'unknown apps|알 수 없는 앱|isn.t allowed'; then
                snap unknown-sources-dialog
                tap_text "Settings" || tap_text "설정" || true
                last=settings
            fi
        fi
        sleep 2
    done
    return 1
}

prepare_device
create_profile
install_fixture

log "launching $APP in user 0"
sh_ am start --user 0 -W -n "$APP/.ui.MainActivity"
tap_clone_button
log "clone requested; driving install dialogs (timeout ${CLONE_TIMEOUT}s)"
drive_install || fail "$FIXTURE was not installed in profile $PROFILE within ${CLONE_TIMEOUT}s"

snap done
sh_ dumpsys package "$FIXTURE" | grep -E "^ *User [0-9]+:" || true
log "PASS: $FIXTURE installed in profile user $PROFILE"
