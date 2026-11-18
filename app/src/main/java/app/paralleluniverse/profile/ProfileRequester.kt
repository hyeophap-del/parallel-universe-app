package app.paralleluniverse.profile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.UserHandle
import android.util.Log
import app.paralleluniverse.ui.MainActivity

/**
 * 개인 공간에서 평행 공간으로 요청을 보낸다. 연결 통로가 없으면 스스로 복구한다.
 *
 * 복구 방법: CrossProfileApps.startMainActivity로 프로필 안의 이 앱(MainActivity)을 실행한다.
 * 이 API는 cross-profile 필터 없이도 동작한다. 프로필 쪽 MainActivity가 ProfileSetup을 다시
 * 적용하고 바로 닫히면, 개인 화면이 다시 보일 때(onResume) 보류해 둔 요청을 한 번 더 보낸다.
 */
class ProfileRequester(private val context: Context) {

    enum class Result { SENT, REPAIRING, FAILED }

    private val crossProfileApps = context.getSystemService(CrossProfileApps::class.java)
    private var pending: Intent? = null

    fun request(intent: Intent, profile: UserHandle): Result {
        if (ProfileBridge.send(context, intent)) return Result.SENT

        pending = intent
        return try {
            crossProfileApps.startMainActivity(ComponentName(context, MainActivity::class.java), profile)
            Result.REPAIRING
        } catch (e: SecurityException) {
            Log.w(TAG, "repair launch failed", e)
            pending = null
            Result.FAILED
        } catch (e: IllegalStateException) {
            // 업무 앱이 일시중지된 경우 등
            Log.w(TAG, "repair launch failed", e)
            pending = null
            Result.FAILED
        }
    }

    /** 화면이 다시 보일 때 호출한다. 보류한 요청이 없으면 null을 반환한다. */
    fun retryPending(): Result? {
        val intent = pending ?: return null
        pending = null
        return if (ProfileBridge.send(context, intent)) Result.SENT else Result.FAILED
    }

    private companion object {
        const val TAG = "ProfileRequester"
    }
}
