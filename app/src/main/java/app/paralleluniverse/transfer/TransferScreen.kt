package app.paralleluniverse.transfer

import android.content.ActivityNotFoundException
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.paralleluniverse.R
import app.paralleluniverse.ui.NoticeCard
import app.paralleluniverse.ui.SectionHeader
import app.paralleluniverse.ui.toast

/** 평행파일 화면: 접속 주소·PIN, PC로 보낼 파일, PC에서 받은 파일을 보여 준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(viewModel: TransferViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val shared by viewModel.shared.collectAsStateWithLifecycle()
    val received by viewModel.received.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.pickerOpen = false
        viewModel.addShared(uris)
    }
    val pickFiles = {
        viewModel.pickerOpen = true
        try {
            picker.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            viewModel.pickerOpen = false
            toast(context, R.string.transfer_picker_unavailable)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transfer_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.transfer_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val current = state
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { StatusSection(current, onRetry = viewModel::start) }

            if (current is TransferState.Running) {
                item {
                    NoticeCard(
                        title = stringResource(R.string.transfer_keep_open_title),
                        body = stringResource(R.string.transfer_keep_open_body),
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }

                item {
                    Column {
                        SectionHeader(stringResource(R.string.transfer_section_send))
                        Text(stringResource(R.string.transfer_send_body), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = pickFiles, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.transfer_pick_files))
                        }
                    }
                }
                if (shared.isEmpty()) {
                    item { HintText(stringResource(R.string.transfer_no_shared)) }
                } else {
                    items(shared, key = { it.id }) { item ->
                        FileRow(
                            name = item.name,
                            size = item.size,
                            action = {
                                TextButton(onClick = { viewModel.removeShared(item.id) }) {
                                    Text(stringResource(R.string.transfer_remove))
                                }
                            },
                        )
                    }
                }

                item {
                    Column {
                        SectionHeader(stringResource(R.string.transfer_section_received))
                        Text(
                            stringResource(R.string.transfer_saved_to, viewModel.saveLocation),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (received.isEmpty()) {
                    item { HintText(stringResource(R.string.transfer_no_received)) }
                } else {
                    items(received) { item -> FileRow(name = item.name, size = item.size, action = null) }
                }
            }
        }
    }
}

@Composable
private fun StatusSection(state: TransferState, onRetry: () -> Unit) {
    when (state) {
        is TransferState.Running -> ConnectionCard(url = state.url, pin = state.pin)

        TransferState.Stopped -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(vertical = 16.dp),
        ) {
            CircularProgressIndicator()
            Text(stringResource(R.string.transfer_starting))
        }

        TransferState.NoNetwork -> ErrorSection(
            title = stringResource(R.string.transfer_no_wifi_title),
            body = stringResource(R.string.transfer_no_wifi_body),
            button = stringResource(R.string.transfer_retry),
            onClick = onRetry,
        )

        TransferState.NoPort -> ErrorSection(
            title = stringResource(R.string.transfer_no_port_title),
            body = stringResource(R.string.transfer_no_port_body),
            button = stringResource(R.string.transfer_retry),
            onClick = onRetry,
        )

        TransferState.TimedOut -> ErrorSection(
            title = stringResource(R.string.transfer_timed_out_title),
            body = stringResource(R.string.transfer_timed_out_body),
            button = stringResource(R.string.transfer_restart),
            onClick = onRetry,
        )
    }
}

@Composable
private fun ConnectionCard(url: String, pin: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(stringResource(R.string.transfer_url_label), style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(url, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.transfer_pin_label), style = MaterialTheme.typography.labelLarge)
            Text(
                pin.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
            )
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.transfer_steps), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ErrorSection(title: String, body: String, button: String, onClick: () -> Unit) {
    Column {
        NoticeCard(
            title = title,
            body = body,
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(button) }
    }
}

@Composable
private fun FileRow(name: String, size: Long, action: (@Composable () -> Unit)?) {
    val context = LocalContext.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (size >= 0) {
                    Formatter.formatShortFileSize(context, size)
                } else {
                    stringResource(R.string.transfer_size_unknown)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.invoke()
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
