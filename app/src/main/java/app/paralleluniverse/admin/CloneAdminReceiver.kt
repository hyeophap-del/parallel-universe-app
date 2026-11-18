package app.paralleluniverse.admin

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import app.paralleluniverse.profile.ProfileSetup

/**
 * 평행 공간(관리 프로필)의 Profile Owner로 등록되는 관리자 리시버.
 * 이 앱은 프로필 안에서 DPC 역할만 하며, 개인 공간이나 기기 전체를 관리하지 않는다.
 */
class CloneAdminReceiver : DeviceAdminReceiver() {

    /** 프로필 생성이 끝나면 프로필 안에서 호출된다. Android 11 이하에서는 이 경로가 기본이다. */
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        ProfileSetup.complete(context)
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, CloneAdminReceiver::class.java)
    }
}
