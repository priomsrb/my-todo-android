package dev.shafqat.mytodo.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shafqat.mytodo.R
import dev.shafqat.mytodo.data.StorageState

/** Settings: where the markdown files live, and whether the app can currently reach them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(),
) {
    val storageState by viewModel.storageState.collectAsStateWithLifecycle()
    val folderUri by viewModel.folderUri.collectAsStateWithLifecycle()

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::onFolderPicked) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding),
        ) {
            if (storageState is StorageState.PermissionLost) {
                StorageWarning(
                    message = stringResource(R.string.folder_unavailable),
                    actionLabel = stringResource(R.string.choose_folder),
                    onAction = { folderPicker.launch(null) },
                )
            } else if (storageState is StorageState.Error) {
                StorageWarning(
                    message = (storageState as StorageState.Error).message,
                    actionLabel = stringResource(R.string.choose_folder),
                    onAction = { folderPicker.launch(null) },
                )
            }

            SettingRow(
                title = stringResource(R.string.todo_folder),
                subtitle = folderSubtitle(storageState, usingDefault = folderUri == null),
                onClick = { folderPicker.launch(null) },
            )
            if (folderUri != null) {
                TextButton(
                    onClick = viewModel::useDefaultFolder,
                    modifier = Modifier.padding(start = 12.dp),
                ) { Text(stringResource(R.string.use_default_folder)) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.file_format),
                subtitle = stringResource(R.string.file_format_summary),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SettingRow(
                title = stringResource(R.string.version),
                subtitle = stringResource(R.string.version_summary),
            )
        }
    }
}

@Composable
private fun folderSubtitle(state: StorageState, usingDefault: Boolean): String = when (state) {
    is StorageState.Ready -> {
        val where = if (usingDefault) stringResource(R.string.default_folder_label) else state.label
        val lists = if (state.listCount == 1) "1 list" else "${state.listCount} lists"
        "$where · $lists"
    }
    is StorageState.PermissionLost -> "${state.label} — no longer accessible"
    is StorageState.Error -> state.label
    StorageState.Loading -> stringResource(R.string.loading)
}

@Composable
private fun StorageWarning(message: String, actionLabel: String, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
