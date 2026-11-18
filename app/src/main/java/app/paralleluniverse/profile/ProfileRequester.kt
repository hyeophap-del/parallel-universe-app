package app.paralleluniverse.profile

import android.content.Context
import android.os.UserHandle
import app.paralleluniverse.provisioning.ProvisioningManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 개인 공간에서 평행 공간으로 명령을 보낸다. */
class ProfileRequester(private val context: Context, private val provisioning: ProvisioningManager) {

    suspend fun request(command: BridgeCommand, profile: UserHandle): LocalBridge.Outcome =
        withContext(Dispatchers.IO) {
            // 키가 없으면 보안 업데이트 이전에 만든 공간이다. 다시 만들어야 한다.
            val key = BridgeKeyStore.get(context) ?: return@withContext LocalBridge.Outcome.UNPAIRED
            LocalBridge.serve(
                key,
                command,
                payload = { out ->
                    if (command.action == BridgeCommand.Action.CLONE) ApkTransfer.send(context, command.arg, out)
                },
                // 서버를 연 뒤에 평행 공간 앱을 깨운다.
                wake = { provisioning.openSpace(profile) },
            )
        }
}
