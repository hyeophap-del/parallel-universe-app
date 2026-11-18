package app.paralleluniverse.provisioning

import android.app.Activity
import android.os.Bundle
import app.paralleluniverse.admin.CloneAdminReceiver
import app.paralleluniverse.profile.BridgeKeyStore
import app.paralleluniverse.profile.ProfileSetup

/**
 * Android 12+에서 프로필 생성 직후 새 프로필 안에서 실행된다.
 * LocalBridge 비밀 키를 받고 초기 정책을 적용한 뒤 바로 종료한다. 리시버 경로와 겹쳐 실행되어도 안전하다.
 */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BridgeKeyStore.adopt(this, CloneAdminReceiver.adminExtras(intent))
        ProfileSetup.complete(this)
        setResult(RESULT_OK)
        finish()
    }
}
