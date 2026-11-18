package app.paralleluniverse.ui

import android.app.admin.DevicePolicyManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.paralleluniverse.profile.ProfileSetup

/**
 * 개인 공간: 평행 공간 만들기와 열기만 담당한다.
 * 평행 공간(프로필) 안: 앱 복제·실행·관리 화면을 보여 준다.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val insideSpace = getSystemService(DevicePolicyManager::class.java).isProfileOwnerApp(packageName)
        if (insideSpace) ProfileSetup.complete(this)

        enableEdgeToEdge()
        setContent {
            ParallelTheme {
                if (insideSpace) ManagerScreen() else MainScreen()
            }
        }
    }
}
