package app.paralleluniverse.provisioning

import android.app.Activity
import android.os.Bundle
import app.paralleluniverse.profile.ProfileSetup

/**
 * Android 12+에서 프로필 생성 직후 새 프로필 안에서 실행된다.
 * 초기 정책을 적용하고 곧바로 종료한다. 리시버 경로와 겹쳐 실행되어도 ProfileSetup은 멱등적이다.
 */
class PolicyComplianceActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProfileSetup.complete(this)
        setResult(RESULT_OK)
        finish()
    }
}
