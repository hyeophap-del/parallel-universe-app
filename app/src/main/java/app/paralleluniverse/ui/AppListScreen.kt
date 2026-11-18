package app.paralleluniverse.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import app.paralleluniverse.sync.MediaSync
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 하단 탭 */
enum class HomeTab { MY_APPS, CLONE, SETTINGS }

/**
 * 개인 공간의 관리 화면(평행 공간이 있을 때). 탭마다 다른 내용을 보여 주지만 상태와 동작은 한곳에서 관리한다.
 * 목록·실행은 직접, 복제 등 평행 공간 안의 작업은 LocalBridge로 요청한다.
 */
@Composable
fun AppListScreen(
    profile: UserHandle,
    repository: CloneRepository,
    requester: ProfileRequester,
    isPaused: Boolean,
    refreshKey: Int,
    tab: HomeTab,
    onTabChange: (HomeTab) -> Unit,
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
    var mediaSync by remember { mutableStateOf(SpaceSettings.mediaSync(context)) }
    var downloadsAllowed by remember { mutableStateOf(MediaSync.canReadDownloads()) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(profile, refreshKey, localRefresh) {
        snapshot = withContext(Dispatchers.Default) { repository.load(profile) }
    }

    /** 평행 공간에 명령을 보내고 결과를 알린다. 성공하면 [onOk]를 실행한다. */
    fun send(
        command: BridgeCommand,
        okMessage: (() -> String?)? = null,
        payload: ((DataOutputStream, DataInputStream) -> Unit)? = null,
        onOk: () -> Unit = {},
    ) {
        if (busy) return
        busy = true
        scope.launch {
            val outcome = requester.request(command, profile, payload)
            busy = false
            when (outcome) {
                LocalBridge.Outcome.OK -> {
                    onOk()
                    okMessage?.invoke()?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
                    localRefresh++
                }
                LocalBridge.Outcome.FAILED -> toast(context, R.string.bridge_failed)
                LocalBridge.Outcome.NO_ANSWER -> toast(context, R.string.bridge_no_answer)
                LocalBridge.Outcome.UNPAIRED -> toast(context, R.string.bridge_unpaired)
            }
        }
    }

    /** 개인 사진·파일 복사. [clearAll]이면 빈 목록을 보내 평행 공간의 복사본을 모두 지운다. */
    fun runSync(clearAll: Boolean) {
        if (busy) return
        scope.launch {
            val sources = if (clearAll) emptyList() else withContext(Dispatchers.IO) { MediaSync.scan(context) }
            var result: MediaSync.Result? = null
            send(
                BridgeCommand.sync(),
                okMessage = {
                    val r = result
                    when {
                        clearAll -> context.getString(R.string.sync_cleared, r?.deleted ?: 0)
                        r == null || (r.saved == 0 && r.deleted == 0) -> null
                        else -> context.getString(R.string.sync_done, r.saved, r.deleted)
                    }
                },
                payload = { out, input -> result = MediaSync.send(sources, out, input) },
            ) {
                SpaceSettings.markSynced(context)
                if (clearAll) {
                    SpaceSettings.setMediaSync(context, false)
                    mediaSync = false
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (MediaSync.hasMediaPermission(context)) {
            SpaceSettings.setMediaSync(context, true)
            mediaSync = true
            runSync(clearAll = false)
        } else {
            toast(context, R.string.sync_permission_denied)
        }
    }

    // 앱을 열 때 자동 동기화(켜져 있고, 최근 2분 안에 하지 않았을 때)
    LaunchedEffect(paired, refreshKey) {
        downloadsAllowed = MediaSync.canReadDownloads()
        if (paired && mediaSync && MediaSync.hasMediaPermission(context) && SpaceSettings.shouldAutoSync(context)) {
            runSync(clearAll = false)
        }
    }

    val current = snapshot
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(Modifier.fillMaxSize()) {
        // 평행 공간과 통신 중일 때(복제·동기화 등) 상단에 진행 표시
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth()) else Spacer(Modifier.height(4.dp))

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (tab) {
                HomeTab.MY_APPS -> myAppsTab(
                    cloned = current.cloned,
                    paired = paired,
                    isPaused = isPaused,
                    busy = busy,
                    onRecreate = onRecreate,
                    onGoClone = { onTabChange(HomeTab.CLONE) },
                    onLaunch = { entry -> if (!repository.launch(entry)) toast(context, R.string.launch_failed) },
                    onShortcut = { shortcutTarget = it },
                    onBattery = { entry ->
                        if (repository.openAppDetails(entry)) {
                            Toast.makeText(context, R.string.battery_hint, Toast.LENGTH_LONG).show()
                        } else {
                            toast(context, R.string.launch_failed)
                        }
                    },
                    onRemove = { send(BridgeCommand.uninstall(it.packageName)) },
                )

                HomeTab.CLONE -> {
                    val clonedPackages = current.cloned.mapTo(HashSet()) { it.packageName }
                    val candidates = current.personal
                        .filterNot { it.packageName in clonedPackages }
                        .filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
                    cloneTab(
                        candidates = candidates,
                        query = query,
                        onQueryChange = { query = it },
                        busy = busy,
                        onClone = { entry ->
                            Toast.makeText(context, R.string.clone_in_progress, Toast.LENGTH_LONG).show()
                            send(
                                BridgeCommand.clone(entry.packageName),
                                okMessage = { context.getString(R.string.clone_success, entry.label) },
                            ) { onTabChange(HomeTab.MY_APPS) }
                        },
                    )
                }

                HomeTab.SETTINGS -> settingsTab {
                    item {
                        SettingsGroup(stringResource(R.string.settings_group_space)) {
                            SettingRow(
                                title = stringResource(R.string.space_name_label, spaceName),
                                action = {
                                    TextButton(enabled = !busy && paired, onClick = { renaming = true }) {
                                        Text(stringResource(R.string.rename))
                                    }
                                },
                            )
                        }
                    }
                    item {
                        SettingsGroup(stringResource(R.string.settings_group_files)) {
                            if (hasSamsungMyFiles) {
                                SettingRow(
                                    title = stringResource(R.string.personal_files_title),
                                    body = stringResource(R.string.personal_files_body),
                                    action = {
                                        Switch(
                                            checked = personalFiles,
                                            enabled = !busy && paired,
                                            onCheckedChange = { on ->
                                                send(
                                                    BridgeCommand.personalFiles(on),
                                                    okMessage = {
                                                        context.getString(
                                                            if (on) R.string.personal_files_on else R.string.personal_files_off,
                                                        )
                                                    },
                                                ) {
                                                    SpaceSettings.setPersonalFiles(context, on)
                                                    personalFiles = on
                                                }
                                            },
                                        )
                                    },
                                )
                            }
                            if (MediaSync.isSupported()) {
                                SettingRow(
                                    title = stringResource(R.string.sync_title),
                                    body = stringResource(R.string.sync_body),
                                    action = {
                                        Switch(
                                            checked = mediaSync,
                                            enabled = !busy && paired,
                                            onCheckedChange = { on ->
                                                if (on) {
                                                    permissionLauncher.launch(MediaSync.mediaPermissions())
                                                } else {
                                                    runSync(clearAll = true)
                                                }
                                            },
                                        )
                                    },
                                )
                                if (mediaSync) {
                                    if (!downloadsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        SettingRow(
                                            title = stringResource(R.string.sync_downloads_title),
                                            body = stringResource(R.string.sync_downloads_body),
                                            action = {
                                                TextButton(onClick = {
                                                    context.startActivity(
                                                        Intent(
                                                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                            Uri.fromParts("package", context.packageName, null),
                                                        ),
                                                    )
                                                }) { Text(stringResource(R.string.sync_downloads_allow)) }
                                            },
                                        )
                                    }
                                    FilledTonalButton(
                                        enabled = !busy,
                                        onClick = { runSync(clearAll = false) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                    ) { Text(stringResource(R.string.sync_now)) }
                                }
                            }
                        }
                    }
                    item {
                        SettingsGroup(stringResource(R.string.settings_group_help)) {
                            HelpItem(stringResource(R.string.tip_title), stringResource(R.string.tip_body))
                            HorizontalDivider(Modifier.padding(vertical = 8.dp))
                            HelpItem(stringResource(R.string.share_tip_title), stringResource(R.string.share_tip_body))
                        }
                    }
                    item {
                        TextButton(
                            enabled = !busy,
                            onClick = { send(BridgeCommand.destroy()) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.destroy_space), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
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
                send(BridgeCommand.rename(name), okMessage = { context.getString(R.string.rename_done) }) {
                    SpaceName.set(context, name)
                    spaceName = name
                }
            },
            onDismiss = { renaming = false },
        )
    }
}

// ---------------------------------------------------------------- 탭: 내 앱

private fun LazyListScope.myAppsTab(
    cloned: List<AppEntry>,
    paired: Boolean,
    isPaused: Boolean,
    busy: Boolean,
    onRecreate: () -> Unit,
    onGoClone: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onShortcut: (AppEntry) -> Unit,
    onBattery: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
) {
    item {
        when {
            !paired -> {
                StatusBanner(
                    text = stringResource(R.string.recreate_title),
                    detail = stringResource(R.string.recreate_body),
                    ok = false,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onRecreate, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.recreate_action))
                }
            }
            isPaused -> StatusBanner(
                text = stringResource(R.string.status_paused),
                detail = stringResource(R.string.quiet_mode_warning),
                ok = false,
            )
            else -> StatusBanner(text = stringResource(R.string.status_connected), ok = true)
        }
    }

    if (cloned.isEmpty()) {
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.no_clones_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.no_clones),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onGoClone) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.go_clone))
                }
            }
        }
    } else {
        items(cloned, key = { "cloned:" + it.packageName }) { entry ->
            AppCard(entry) {
                Button(onClick = { onLaunch(entry) }) { Text(stringResource(R.string.action_launch)) }
                CloneMenu(
                    enabled = !busy,
                    onShortcut = { onShortcut(entry) },
                    onBattery = { onBattery(entry) },
                    onRemove = { onRemove(entry) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 탭: 복제하기

private fun LazyListScope.cloneTab(
    candidates: List<AppEntry>,
    query: String,
    onQueryChange: (String) -> Unit,
    busy: Boolean,
    onClone: (AppEntry) -> Unit,
) {
    item {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_apps)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (candidates.isEmpty()) {
        item {
            Text(
                stringResource(R.string.no_search_results),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
    }
    items(candidates, key = { "personal:" + it.packageName }) { entry ->
        AppCard(entry, recommended = entry.packageName == CloneRepository.DISCORD) {
            FilledTonalButton(enabled = !busy, onClick = { onClone(entry) }) {
                Text(stringResource(R.string.action_clone))
            }
        }
    }
}

// ---------------------------------------------------------------- 탭: 설정

private fun LazyListScope.settingsTab(content: LazyListScope.() -> Unit) = content()

// ---------------------------------------------------------------- 공용 컴포넌트

@Composable
private fun StatusBanner(text: String, ok: Boolean, detail: String? = null) {
    val container = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (ok) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (ok) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32)),
                    )
                } else {
                    Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(text, style = MaterialTheme.typography.titleSmall)
            }
            if (detail != null) {
                Spacer(Modifier.height(4.dp))
                Text(detail, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { content() }
    }
}

@Composable
private fun SettingRow(title: String, body: String? = null, action: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (body != null) {
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.size(8.dp))
        action()
    }
}

@Composable
private fun HelpItem(title: String, body: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppCard(
    entry: AppEntry,
    recommended: Boolean = false,
    actions: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
}

@Composable
private fun CloneMenu(
    enabled: Boolean,
    onShortcut: () -> Unit,
    onBattery: () -> Unit,
    onRemove: () -> Unit,
) {
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
                text = { Text(stringResource(R.string.battery_settings)) },
                onClick = {
                    open = false
                    onBattery()
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
