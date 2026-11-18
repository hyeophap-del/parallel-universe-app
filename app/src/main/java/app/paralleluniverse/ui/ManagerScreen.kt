package app.paralleluniverse.ui

import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.paralleluniverse.R
import app.paralleluniverse.profile.AppEntry
import app.paralleluniverse.profile.AppsSnapshot
import app.paralleluniverse.profile.CloneRepository
import app.paralleluniverse.profile.ProfileActions
import app.paralleluniverse.profile.ProfileSetup
import app.paralleluniverse.profile.SpaceName
import app.paralleluniverse.shortcut.CloneShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 평행 공간(프로필) 안에서 실행되는 관리 화면. 모든 작업을 이 공간 안에서 직접 수행한다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagerScreen() {
    val context = LocalContext.current
    val repository = remember { CloneRepository(context) }
    val scope = rememberCoroutineScope()

    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        onPauseOrDispose { }
    }

    var snapshot by remember { mutableStateOf<AppsSnapshot?>(null) }
    LaunchedEffect(refreshKey) {
        snapshot = withContext(Dispatchers.Default) { repository.load() }
    }

    var spaceName by remember { mutableStateOf(SpaceName.get(context)) }
    var shortcutTarget by remember { mutableStateOf<AppEntry?>(null) }
    var renaming by remember { mutableStateOf(false) }
    var confirmingDestroy by remember { mutableStateOf(false) }
    var cloning by remember { mutableStateOf<String?>(null) }

    val clone: (AppEntry) -> Unit = { entry ->
        cloning = entry.packageName
        scope.launch {
            val ok = withContext(Dispatchers.IO) { ProfileActions.clone(context, entry.packageName) }
            val message = if (ok) R.string.clone_success else R.string.clone_failed
            android.widget.Toast.makeText(context, context.getString(message, entry.label), android.widget.Toast.LENGTH_SHORT).show()
            cloning = null
            refreshKey++
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(spaceName) }) },
    ) { padding ->
        val current = snapshot
        if (current == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
            }
            items(current.cloned, key = { "cloned:" + it.packageName }) { entry ->
                AppRow(entry) {
                    Button(onClick = { launch(context, repository, entry) }) {
                        Text(stringResource(R.string.action_launch))
                    }
                    CloneMenu(
                        onShortcut = { shortcutTarget = entry },
                        onRemove = { context.startActivity(ProfileActions.uninstallIntent(entry.packageName)) },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.section_available)) }
            items(current.candidates, key = { "candidate:" + it.packageName }) { entry ->
                AppRow(entry, recommended = entry.packageName == CloneRepository.DISCORD) {
                    FilledTonalButton(enabled = cloning == null, onClick = { clone(entry) }) {
                        Text(stringResource(R.string.action_clone))
                    }
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
                    TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.rename)) }
                }
            }
            item {
                TextButton(
                    onClick = { confirmingDestroy = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(stringResource(R.string.destroy_space), color = MaterialTheme.colorScheme.error)
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
                ProfileSetup.rename(context, name)
                spaceName = name
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }

    if (confirmingDestroy) {
        AlertDialog(
            onDismissRequest = { confirmingDestroy = false },
            title = { Text(stringResource(R.string.destroy_confirm_title)) },
            text = { Text(stringResource(R.string.destroy_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { ProfileActions.destroy(context) }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDestroy = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

private fun launch(context: Context, repository: CloneRepository, entry: AppEntry) {
    val intent = repository.launchIntent(entry.packageName)
    if (intent != null) context.startActivity(intent) else toast(context, R.string.launch_failed)
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
private fun CloneMenu(onShortcut: () -> Unit, onRemove: () -> Unit) {
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
                text = { Text(stringResource(R.string.action_remove)) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}
