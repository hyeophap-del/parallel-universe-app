package app.paralleluniverse.profile

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.net.Uri

/** 프로필 안(Profile Owner 권한)에서 실행하는 작업들. 앱 복제는 [ApkTransfer] 참고. */
object ProfileActions {

    /** 시스템 삭제 확인 화면을 띄우는 인텐트. 프로필 안의 복제본과 그 데이터만 삭제된다. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 평행 공간 전체(프로필과 그 안의 모든 데이터)를 삭제한다. */
    fun destroy(context: Context) {
        context.getSystemService(DevicePolicyManager::class.java).wipeData(0)
    }
}
