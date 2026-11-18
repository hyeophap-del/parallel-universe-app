package app.paralleluniverse.ui

import android.content.Context
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppListScreen(
    profile: UserHandle,
    repository: CloneRepository,
    isPaused: Boolean,
    refreshKey: Int,
) {
    val context = LocalContext.current
    var snapshot by remember { mutableStateOf<AppsSnapshot?>(null) }

    LaunchedEffect(profile, refreshKey) {
        snapshot = withContext(Dispatchers.Default) { repository.load(profile) }
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
        }
        items(current.cloned, key = { "cloned:" + it.packageName }) { entry ->
            AppRow(entry) {
                OutlinedButton(onClick = {
                    sendToProfile(context, ProfileBridge.uninstall(entry.packageName))
                }) { Text(stringResource(R.string.action_remove)) }
                Button(onClick = {
                    if (!repository.launch(entry)) toast(context, R.string.launch_failed)
                }) { Text(stringResource(R.string.action_launch)) }
            }
        }

        item { SectionHeader(stringResource(R.string.section_available)) }
        items(candidates, key = { "personal:" + it.packageName }) { entry ->
            AppRow(entry, recommended = entry.packageName == CloneRepository.DISCORD) {
                FilledTonalButton(onClick = {
                    sendToProfile(context, ProfileBridge.clone(entry.packageName))
                }) { Text(stringResource(R.string.action_clone)) }
            }
        }

        item {
            TextButton(
                onClick = { sendToProfile(context, ProfileBridge.destroy()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Text(stringResource(R.string.destroy_space), color = MaterialTheme.colorScheme.error)
            }
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
    }
}

private fun sendToProfile(context: Context, intent: android.content.Intent) {
    if (!ProfileBridge.send(context, intent)) toast(context, R.string.bridge_failed)
}

private fun toast(context: Context, message: Int) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
