package app.paralleluniverse.ui

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import app.paralleluniverse.R
import app.paralleluniverse.profile.ApkTransfer
import app.paralleluniverse.profile.BridgeCommand
import app.paralleluniverse.profile.BridgeKeyStore
import app.paralleluniverse.profile.LocalBridge
import app.paralleluniverse.profile.ProfileActions
import app.paralleluniverse.profile.ProfileSetup
import app.paralleluniverse.sync.MediaSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 개인 공간: 관리 화면을 보여 준다.
 * 평행 공간 안: 화면 없이 깨어나 LocalBridge로 명령을 받아 실행하고 닫힌다.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (getSystemService(DevicePolicyManager::class.java).isProfileOwnerApp(packageName)) {
            ProfileSetup.complete(this)
            if (savedInstanceState == null) handleBridgeCommand() else finish()
            return
        }

        enableEdgeToEdge()
        setContent {
            ParallelTheme {
                MainScreen()
            }
        }
    }

    private fun handleBridgeCommand() {
        val key = BridgeKeyStore.get(this)
        if (key == null) {
            // 보안 업데이트 이전에 만든 공간: 비밀 키가 없어 개인 공간과 안전하게 연결할 수 없다.
            // 개인 공간 앱의 '다시 만들기' 버튼이나 업무 앱 서랍의 아이콘으로 이 화면이 열린다.
            confirmDestroy(R.string.recreate_confirm_title, R.string.recreate_confirm_body)
            return
        }
        lifecycleScope.launch {
            var destroyRequested = false
            val command = withContext(Dispatchers.IO) {
                LocalBridge.receive(key) { command, input, out ->
                    when (command.action) {
                        BridgeCommand.Action.SYNC -> MediaSync.receive(applicationContext, input, out)
                        BridgeCommand.Action.CLONE ->
                            ApkTransfer.receiveAndInstall(this@MainActivity, command.arg, input)
                        BridgeCommand.Action.RENAME -> {
                            ProfileSetup.rename(applicationContext, command.arg)
                            true
                        }
                        BridgeCommand.Action.PERSONAL_FILES ->
                            ProfileActions.setPersonalFilesInPicker(applicationContext, command.arg == "on")
                        BridgeCommand.Action.UNINSTALL -> true
                        BridgeCommand.Action.DESTROY -> {
                            destroyRequested = true
                            true
                        }
                    }
                }
            }
            when {
                command == null -> {
                    // 업무 앱 서랍의 아이콘을 직접 누른 경우
                    Toast.makeText(applicationContext, R.string.open_from_personal, Toast.LENGTH_LONG).show()
                    finish()
                }
                command.action == BridgeCommand.Action.UNINSTALL -> {
                    startActivity(ProfileActions.uninstallIntent(command.arg))
                    finish()
                }
                destroyRequested -> confirmDestroy(R.string.destroy_confirm_title, R.string.destroy_confirm_body)
                else -> finish()
            }
        }
    }

    private fun confirmDestroy(title: Int, body: Int) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
            .setTitle(title)
            .setMessage(body)
            .setPositiveButton(R.string.delete) { _, _ -> ProfileActions.destroy(applicationContext) }
            .setNegativeButton(R.string.cancel, null)
            .setOnDismissListener { finish() }
            .show()
    }
}
