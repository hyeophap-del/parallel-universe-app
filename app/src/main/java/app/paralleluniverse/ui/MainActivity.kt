package app.paralleluniverse.ui

import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.paralleluniverse.R
import app.paralleluniverse.profile.ProfileSetup

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 평행 공간(프로필) 안에서 실행된 경우: 설정을 다시 적용하고 바로 닫는다.
        // 개인 공간의 ProfileRequester가 연결 복구용으로 실행하는 경로다.
        if (getSystemService(DevicePolicyManager::class.java).isProfileOwnerApp(packageName)) {
            ProfileSetup.complete(this)
            Toast.makeText(this, R.string.repair_done, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            ParallelTheme {
                MainScreen()
            }
        }
    }
}
