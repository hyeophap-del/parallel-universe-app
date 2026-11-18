package app.paralleluniverse.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.paralleluniverse.R
import app.paralleluniverse.profile.CloneRepository
import app.paralleluniverse.profile.ProfileRequester
import app.paralleluniverse.provisioning.ProvisioningManager
import app.paralleluniverse.provisioning.SpaceStatus
import app.paralleluniverse.transfer.TransferActivity

/** 개인 공간 화면: 평행 공간을 만들고, 이미 있으면 앱 목록과 관리 기능을 보여 준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val provisioning = remember { ProvisioningManager(context) }
    val repository = remember { CloneRepository(context) }
    val requester = remember { ProfileRequester(context, provisioning) }

    // 화면으로 돌아올 때마다(프로필 생성 마법사, 삭제 확인 화면 등) 상태를 다시 읽는다.
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    val status = remember(refreshKey) { provisioning.status() }
    val profile = remember(refreshKey) { provisioning.ourProfile() }

    val provisionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Toast.makeText(context, R.string.provisioning_cancelled, Toast.LENGTH_SHORT).show()
        }
        refreshKey++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = {
                        context.startActivity(Intent(context, TransferActivity::class.java))
                    }) { Text(stringResource(R.string.parallel_files)) }
                    TextButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.refresh)) }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (status) {
                SpaceStatus.CAN_CREATE -> OnboardingScreen(
                    onCreate = {
                        try {
                            provisionLauncher.launch(provisioning.createProfileIntent())
                        } catch (e: ActivityNotFoundException) {
                            Toast.makeText(context, R.string.provisioning_unavailable, Toast.LENGTH_LONG).show()
                        }
                    },
                )

                SpaceStatus.READY -> if (profile != null) {
                    AppListScreen(
                        profile = profile,
                        repository = repository,
                        requester = requester,
                        isPaused = remember(refreshKey) { provisioning.isPaused(profile) },
                        refreshKey = refreshKey,
                        // 평행 공간 쪽 앱이 키가 없으면 삭제 확인 창을 띄운다.
                        onRecreate = {
                            if (!provisioning.openSpace(profile)) toast(context, R.string.bridge_failed)
                        },
                    )
                }

                // 평행 공간 안에서는 MainActivity가 화면 없이 명령만 처리하므로 여기에 오지 않는다.
                SpaceStatus.INSIDE_PROFILE -> Unit

                SpaceStatus.OTHER_PROFILE_EXISTS -> MessageScreen(
                    title = stringResource(R.string.other_profile_title),
                    body = stringResource(R.string.other_profile_body),
                )

                SpaceStatus.UNSUPPORTED -> MessageScreen(
                    title = stringResource(R.string.unsupported_title),
                    body = stringResource(R.string.unsupported_body),
                )
            }
        }
    }
}
