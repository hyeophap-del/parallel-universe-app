package app.paralleluniverse.profile

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * 개인 공간 → 평행 공간 요청 통로.
 *
 * 프로필 쪽 ProfileSetup이 아래 action들을 cross-profile intent filter로 등록한다.
 * 개인 공간에서 ProfileActionActivity는 비활성화되어 있으므로, 이 인텐트들은 시스템을 거쳐
 * 곧바로 프로필 안의 ProfileActionActivity로 전달된다.
 */
object ProfileBridge {
    const val ACTION_CLONE = "app.paralleluniverse.action.CLONE"
    const val ACTION_UNINSTALL = "app.paralleluniverse.action.UNINSTALL"
    const val ACTION_DESTROY = "app.paralleluniverse.action.DESTROY"
    const val EXTRA_PACKAGE = "app.paralleluniverse.extra.PACKAGE"

    fun intentFilter(): IntentFilter = IntentFilter().apply {
        addAction(ACTION_CLONE)
        addAction(ACTION_UNINSTALL)
        addAction(ACTION_DESTROY)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    fun clone(packageName: String): Intent =
        Intent(ACTION_CLONE).putExtra(EXTRA_PACKAGE, packageName)

    fun uninstall(packageName: String): Intent =
        Intent(ACTION_UNINSTALL).putExtra(EXTRA_PACKAGE, packageName)

    fun destroy(): Intent = Intent(ACTION_DESTROY)

    /** @return 요청을 전달했으면 true, 받을 대상(프로필)이 없으면 false */
    fun send(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
