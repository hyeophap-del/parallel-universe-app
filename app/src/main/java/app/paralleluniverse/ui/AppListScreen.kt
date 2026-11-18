package app.paralleluniverse.ui

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.paralleluniverse.R
import app.paralleluniverse.profile.AppEntry
import app.paralleluniverse.profile.AppsSnapshot
import app.paralleluniverse.profile.CloneRepository
import app.paralleluniverse.profile.ProfileBridge
import app.paralleluniverse.profile.ProfileRequester
import app.paralleluniverse.profile.SpaceName
import app.paralleluniverse.shortcut.CloneShortcuts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MAX_NAME_LENGTH = 30

@Composable
fun AppListScreen(
    profile: UserHandle,
    repository: CloneRepository,
    requester: ProfileRequester,
    isPaused: Boolean,
    refreshKey: Int,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<AppsSnapshot?>(null) }
    var spaceName by remember { mutableStateOf(SpaceName.get(context)) }
    var shortcutTarget by remember { mutableStateOf<AppEntry?>(null) }
    var renaming by remember { mutableStateOf(false) }

    LaunchedEffect(profile, refreshKey) {
        snapshot = withContext(Dispatchers.Default) { repository.load(profile) }
    }

    val send: (Intent) -> Unit = { intent ->
        showRequestResult(context, requester.request(intent, profile))
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
                    onShortcut = { shortcutTarget = entry },
                    onRemove = { send(ProfileBridge.uninstall(entry.packageName)) },
                )
            }
        }

        item { SectionHeader(stringResource(R.string.section_available)) }
        items(candidates, key = { "personal:" + it.packageName }) { entry ->
            AppRow(entry, recommended = entry.packageName == CloneRepository.DISCORD) {
                FilledTonalButton(onClick = {
                    send(ProfileBridge.clone(entry.packageName))
                }) { Text(stringResource(R.string.action_clone)) }
            }
        }

        item { SectionHeader(stringResource(R.string.section_settings)) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                onClick = { send(ProfileBridge.destroy()) },
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
                SpaceName.set(context, name)
                spaceName = name
                send(ProfileBridge.rename(name))
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
}

/** 평행 공간 요청 결과를 사용자에게 알린다. 보낸 경우는 프로필 쪽이 결과를 알려 준다. */
fun showRequestResult(context: Context, result: ProfileRequester.Result) {
    when (result) {
        ProfileRequester.Result.SENT -> Unit
        ProfileRequester.Result.REPAIRING -> toast(context, R.string.repairing)
        ProfileRequester.Result.FAILED -> toast(context, R.string.bridge_failed)
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun NoticeCard(title: String? = null, body: String, container: Color, content: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
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

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(MAX_NAME_LENGTH) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text.trim()) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

private fun toast(context: Context, message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
