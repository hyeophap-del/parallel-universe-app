package app.paralleluniverse.ui

import android.app.Activity
import android.content.ActivityNotFoundException
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
import app.paralleluniverse.provisioning.ProvisioningManager
import app.paralleluniverse.provisioning.SpaceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val provisioning = remember { ProvisioningManager(context) }
    val repository = remember { CloneRepository(context) }

    // 화면으로 돌아올 때마다(프로필 생성 마법사, 복제 요청 처리 후 등) 상태를 다시 읽는다.
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
                    TextButton(onClick = { refreshKey++ }) {
                        Text(stringResource(R.string.refresh))
                    }
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
                            Toast.makeText(
                                context,
                                R.string.provisioning_unavailable,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )

                SpaceStatus.READY -> if (profile != null) {
                    AppListScreen(
                        profile = profile,
                        repository = repository,
                        isPaused = remember(refreshKey) { provisioning.isPaused(profile) },
                        refreshKey = refreshKey,
                    )
                }

                SpaceStatus.INSIDE_PROFILE -> InsideProfileScreen()

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
