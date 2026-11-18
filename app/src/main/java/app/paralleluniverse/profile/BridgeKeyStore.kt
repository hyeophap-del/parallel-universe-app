package app.paralleluniverse.profile

import android.content.Context
import android.os.PersistableBundle
import android.util.Base64

/**
 * LocalBridge 비밀 키 저장소. 개인 공간과 평행 공간에 같은 키가 각각 저장된다.
 *
 * - 개인 공간: 평행 공간을 만들 때 [createForProvisioning]으로 새 키를 만들어 저장하고,
 *   프로비저닝 인텐트의 admin extras로 넘긴다.
 * - 평행 공간: 프로비저닝 완료 시 시스템이 전달한 admin extras에서 [adopt]로 받아 저장한다.
 */
object BridgeKeyStore {
    private const val PREFS = "bridge"
    private const val KEY = "key"
    private const val EXTRA_KEY = "app.paralleluniverse.bridge_key"

    fun get(context: Context): ByteArray? =
        prefs(context).getString(KEY, null)
            ?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            ?.takeIf { it.size == BridgeAuth.KEY_BYTES }

    fun has(context: Context): Boolean = get(context) != null

    /** 개인 공간: 새 키를 만들어 저장하고, 프로비저닝 인텐트에 넣을 bundle을 돌려준다. */
    fun createForProvisioning(context: Context): PersistableBundle {
        val encoded = Base64.encodeToString(BridgeAuth.newKey(), Base64.NO_WRAP)
        prefs(context).edit().putString(KEY, encoded).commit()
        return PersistableBundle().apply { putString(EXTRA_KEY, encoded) }
    }

    /** 평행 공간: 시스템이 전달한 admin extras에서 키를 받는다. 이미 키가 있으면 바꾸지 않는다. */
    fun adopt(context: Context, extras: PersistableBundle?) {
        if (has(context)) return
        val encoded = extras?.getString(EXTRA_KEY) ?: return
        prefs(context).edit().putString(KEY, encoded).commit()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
