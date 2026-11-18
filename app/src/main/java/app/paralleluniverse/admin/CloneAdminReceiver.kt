package app.paralleluniverse.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.core.content.IntentCompat
import app.paralleluniverse.profile.BridgeKeyStore
import app.paralleluniverse.profile.ProfileSetup

/**
 * 평행 공간(관리 프로필)의 Profile Owner로 등록되는 관리자 리시버.
 * 이 앱은 프로필 안에서 DPC 역할만 하며, 개인 공간이나 기기 전체를 관리하지 않는다.
 */
class CloneAdminReceiver : DeviceAdminReceiver() {

    /** 프로필 생성이 끝나면 프로필 안에서 호출된다. Android 11 이하에서는 이 경로가 기본이다. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        BridgeKeyStore.adopt(context, adminExtras(intent))
        ProfileSetup.complete(context)
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, CloneAdminReceiver::class.java)

        /** 개인 공간이 프로비저닝 인텐트에 넣은 admin extras(LocalBridge 비밀 키 포함). */
        fun adminExtras(intent: Intent): PersistableBundle? = IntentCompat.getParcelableExtra(
            intent,
            DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
            PersistableBundle::class.java,
        )
    }
}
