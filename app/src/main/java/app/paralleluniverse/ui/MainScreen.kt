package app.paralleluniverse.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.os.UserHandle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.paralleluniverse.R
import app.paralleluniverse.provisioning.ProvisioningManager
import app.paralleluniverse.provisioning.SpaceStatus

/** 개인 공간 화면: 평행 공간을 만들거나, 이미 있으면 그 안의 관리 화면을 연다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val provisioning = remember { ProvisioningManager(context) }

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
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
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
                    OpenSpaceScreen(
                        provisioning = provisioning,
                        profile = profile,
                        isPaused = remember(refreshKey) { provisioning.isPaused(profile) },
                    )
                }

                // 평행 공간 안에서는 MainActivity가 ManagerScreen을 보여 주므로 여기에 오지 않는다.
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

@Composable
private fun OpenSpaceScreen(provisioning: ProvisioningManager, profile: UserHandle, isPaused: Boolean) {
    val context = LocalContext.current
    val open = { if (!provisioning.openSpace(profile)) toast(context, R.string.open_failed) }

    // 앱 아이콘을 누르면 바로 관리 화면으로 넘어가도록, 처음 한 번은 자동으로 연다.
    var autoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isPaused) {
        if (!autoOpened && !isPaused) {
            autoOpened = true
            open()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (isPaused) {
            NoticeCard(
                body = stringResource(R.string.quiet_mode_warning),
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(24.dp))
        }
        Text(stringResource(R.string.open_space_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.open_space_body), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Button(onClick = open, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.open_space))
        }
    }
}
