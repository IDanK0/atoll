package dev.atoll.island.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.atoll.island.BuildConfig
import dev.atoll.island.R

private data class License(val library: String, val owner: String, val license: String)

private val LICENSES = listOf(
    License("Atoll", "Daniele Malltezi", "MIT"),
    License("AndroidX Core, Activity, Lifecycle, DataStore", "The Android Open Source Project", "Apache 2.0"),
    License("Jetpack Compose, Material 3", "The Android Open Source Project", "Apache 2.0"),
    License("Kotlin, kotlinx.coroutines", "JetBrains s.r.o. and contributors", "Apache 2.0"),
    License("JUnit 4 (tests only)", "JUnit Team", "EPL 1.0")
)

@Composable
fun AboutAndLicensesDialog(onDismiss: () -> Unit) {
    var showLicenses by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                SupportingText(stringResource(R.string.about_meta_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE))
                Paragraph(R.string.about_author_title, R.string.about_author_body)
                Paragraph(R.string.about_privacy_title, R.string.about_privacy_body)
                Paragraph(R.string.about_credits_title, R.string.about_credits_body)
                Text(
                    stringResource(if (showLicenses) R.string.about_hide_licenses else R.string.about_show_licenses),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(showLicenses, role = Role.Button) { showLicenses = it }
                        .padding(vertical = 12.dp)
                )
                if (showLicenses) {
                    LICENSES.forEach { item ->
                        Column {
                            Text(item.library, style = MaterialTheme.typography.bodyMedium)
                            SupportingText("${item.owner} · ${item.license}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } }
    )
}

@Composable
private fun Paragraph(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
        SupportingText(stringResource(body))
    }
}
