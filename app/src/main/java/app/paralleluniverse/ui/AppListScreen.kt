package app.paralleluniverse.ui

import android.os.UserHandle
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.paralleluniverse.R
import app.paralleluniverse.profile.AppEntry
import app.paralleluniverse.profile.AppsSnapshot
import app.paralleluniverse.profile.BridgeCommand
import app.paralleluniverse.profile.BridgeKeyStore
import app.paralleluniverse.profile.CloneRepository
import app.paralleluniverse.profile.LocalBridge
import app.paralleluniverse.profile.ProfileRequester
import app.paralleluniverse.profile.SpaceName
import app.paralleluniverse.profile.SpaceSettings
import app.paralleluniverse.shortcut.CloneShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 개인 공간의 관리 화면. 목록·실행은 직접, 복제 등 평행 공간 안의 작업은 LocalBridge로 요청한다. */
@Composable
fun AppListScreen(
    profile: UserHandle,
    repository: CloneRepository,
    requester: ProfileRequester,
    isPaused: Boolean,
    refreshKey: Int,
    onRecreate: () -> Unit,
) {
    val context = LocalContext.current
    val paired = remember(refreshKey) { BridgeKeyStore.has(context) }
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<AppsSnapshot?>(null) }
    var localRefresh by remember { mutableIntStateOf(0) }
    var spaceName by remember { mutableStateOf(SpaceName.get(context)) }
    var shortcutTarget by remember { mutableStateOf<AppEntry?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var personalFiles by remember { mutableStateOf(SpaceSettings.personalFiles(context)) }
    val hasSamsungMyFiles = remember { SpaceSettings.hasSamsungMyFiles(context) }

    LaunchedEffect(profile, refreshKey, localRefresh) {
        snapshot = withContext(Dispatchers.Default) { repository.load(profile) }
    }

    /** 평행 공간에 명령을 보내고 결과를 알린다. 성공하면 [onOk]를 실행한다. */
    fun send(command: BridgeCommand, okMessage: String? = null, onOk: () -> Unit = {}) {
        if (busy) return
        busy = true
        scope.launch {
            val outcome = requester.request(command, profile)
            busy = false
            when (outcome) {
                LocalBridge.Outcome.OK -> {
                    onOk()
                    okMessage?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    localRefresh++
                }
                LocalBridge.Outcome.FAILED -> toast(context, R.string.bridge_failed)
                LocalBridge.Outcome.NO_ANSWER -> toast(context, R.string.bridge_no_answer)
                LocalBridge.Outcome.UNPAIRED -> toast(context, R.string.bridge_unpaired)
            }
        }
    }

    val current = snapshot
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val clonedPackages = current.cloned.mapTo(HashSet()) { it.packageName }
    val candidates = current.personal.filterNot { it.packageName in clonedPackages }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!paired) {
            item {
                NoticeCard(
                    title = stringResource(R.string.recreate_title),
                    body = stringResource(R.string.recreate_body),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            item {
                Button(onClick = onRecreate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recreate_action))
                }
            }
        }
        if (isPaused) {
            item {
                NoticeCard(
                    body = stringResource(R.string.quiet_mode_warning),
                    container = MaterialTheme.colorScheme.errorContainer,
                    content = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
        item {
            NoticeCard(
                title = stringResource(R.string.tip_title),
                body = stringResource(R.string.tip_body),
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }

        item { SectionHeader(stringResource(R.string.section_cloned)) }
        if (current.cloned.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_clones),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        } else {
            item {
                NoticeCard(
                    title = stringResource(R.string.share_tip_title),
                    body = stringResource(R.string.share_tip_body),
                    container = MaterialTheme.colorScheme.surfaceVariant,
                    content = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(current.cloned, key = { "cloned:" + it.packageName }) { entry ->
            AppRow(entry) {
                Button(onClick = {
                    if (!repository.launch(entry)) toast(context, R.string.launch_failed)
                }) { Text(stringResource(R.string.action_launch)) }
                CloneMenu(
                    enabled = !busy,
                    onShortcut = { shortcutTarget = entry },
                    onRemove = { send(BridgeCommand.uninstall(entry.packageName)) },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.section_available)) }
        items(candidates, key = { "personal:" + it.packageName }) { entry ->
            AppRow(entry, recommended = entry.packageName == CloneRepository.DISCORD) {
                FilledTonalButton(enabled = !busy, onClick = {
                    Toast.makeText(context, R.string.clone_in_progress, Toast.LENGTH_LONG).show()
                    send(
                        BridgeCommand.clone(entry.packageName),
                        okMessage = context.getString(R.string.clone_success, entry.label),
                    )
                }) { Text(stringResource(R.string.action_clone)) }
            }
        }

        item { SectionHeader(stringResource(R.string.section_settings)) }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.space_name_label, spaceName),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(enabled = !busy, onClick = { renaming = true }) {
                    Text(stringResource(R.string.rename))
                }
            }
        }
        if (hasSamsungMyFiles) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.personal_files_title), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.personal_files_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = personalFiles,
                        enabled = !busy && paired,
                        onCheckedChange = { on ->
                            send(
                                BridgeCommand.personalFiles(on),
                                okMessage = context.getString(
                                    if (on) R.string.personal_files_on else R.string.personal_files_off,
                                ),
                            ) {
                                SpaceSettings.setPersonalFiles(context, on)
                                personalFiles = on
                            }
                        },
                    )
                }
            }
        }
        item {
            TextButton(
                enabled = !busy,
                onClick = { send(BridgeCommand.destroy()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            ) {
                Text(stringResource(R.string.destroy_space), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    shortcutTarget?.let { entry ->
        TextInputDialog(
            title = stringResource(R.string.shortcut_dialog_title),
            initial = CloneShortcuts.defaultLabel(entry.label, spaceName),
            confirmLabel = stringResource(R.string.add),
            onConfirm = { label ->
                if (!CloneShortcuts.pin(context, entry, label)) toast(context, R.string.shortcut_unsupported)
                shortcutTarget = null
            },
            onDismiss = { shortcutTarget = null },
        )
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.rename_title),
            initial = spaceName,
            confirmLabel = stringResource(R.string.save),
            onConfirm = { name ->
                renaming = false
                send(BridgeCommand.rename(name), okMessage = context.getString(R.string.rename_done)) {
                    SpaceName.set(context, name)
                    spaceName = name
                }
            },
            onDismiss = { renaming = false },
        )
    }
}

@Composable
private fun AppRow(
    entry: AppEntry,
    recommended: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(bitmap = entry.icon, contentDescription = null, modifier = Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                entry.label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (recommended) {
                AssistChip(onClick = {}, label = { Text(stringResource(R.string.recommended)) })
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { actions() }
    }
}

@Composable
private fun CloneMenu(enabled: Boolean, onShortcut: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.add_shortcut)) },
                onClick = {
                    open = false
                    onShortcut()
                },
            )
            DropdownMenuItem(
                enabled = enabled,
                text = { Text(stringResource(R.string.action_remove)) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}
