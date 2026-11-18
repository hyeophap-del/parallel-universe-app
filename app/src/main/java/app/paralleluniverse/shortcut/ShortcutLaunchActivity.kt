package app.paralleluniverse.shortcut

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import app.paralleluniverse.R
import app.paralleluniverse.profile.CloneRepository

/**
 * 홈 화면 바로가기가 실행하는 투명 Activity. 평행 공간 안에서 실행되며,
 * 같은 공간의 복제 앱을 열고 바로 닫힌다.
 */
class ShortcutLaunchActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(CloneShortcuts.EXTRA_PACKAGE)
        val launch = packageName?.let { CloneRepository(this).launchIntent(it) }
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, R.string.shortcut_launch_failed, Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
