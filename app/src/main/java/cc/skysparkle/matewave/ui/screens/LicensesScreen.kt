package cc.skysparkle.matewave.ui.screens

import cc.skysparkle.matewave.ui.theme.ink

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cc.skysparkle.matewave.R
import cc.skysparkle.matewave.ui.components.AppDialog
import cc.skysparkle.matewave.ui.components.AppIcon
import cc.skysparkle.matewave.ui.components.ButtonIcon
import cc.skysparkle.matewave.ui.components.DialogAccent
import cc.skysparkle.matewave.ui.components.DialogAction
import cc.skysparkle.matewave.ui.components.GlassCard
import cc.skysparkle.matewave.ui.components.GlassOutlinedButton
import cc.skysparkle.matewave.ui.components.GlassScaffold
import cc.skysparkle.matewave.ui.components.SetupSectionLabel

private data class LicenseEntry(
    val name: String,
    val author: String,
    val version: String?,
    val license: String,
    val url: String,
    val noteRes: Int? = null
)

private const val APACHE = "Apache License 2.0"
private const val AOSP = "The Android Open Source Project"

private val LIBRARIES = listOf(
    LicenseEntry(
        "Jetpack Compose (UI, Foundation, Material 3)", AOSP, "BOM 2026.09.00", APACHE,
        "https://developer.android.com/jetpack/androidx/releases/compose"
    ),
    LicenseEntry(
        "AndroidX Activity Compose", AOSP, "1.13.0", APACHE,
        "https://developer.android.com/jetpack/androidx/releases/activity"
    ),
    LicenseEntry(
        "AndroidX Core KTX", AOSP, "1.18.0", APACHE,
        "https://developer.android.com/jetpack/androidx/releases/core"
    ),
    LicenseEntry(
        "AndroidX Navigation Compose", AOSP, "2.9.7", APACHE,
        "https://developer.android.com/jetpack/androidx/releases/navigation"
    ),
    LicenseEntry(
        "AndroidX Lifecycle ViewModel Compose", AOSP, "2.11.0", APACHE,
        "https://developer.android.com/jetpack/androidx/releases/lifecycle"
    ),
    LicenseEntry(
        "AndroidX \u2014 other libraries (pulled in by the ones above)", AOSP, null, APACHE,
        "https://developer.android.com/jetpack/androidx"
    ),
    LicenseEntry(
        "ZXing Core (QR codes)", "ZXing authors", "3.5.4", APACHE,
        "https://github.com/zxing/zxing"
    ),
    LicenseEntry(
        "kotlinx.coroutines", "JetBrains", "1.11.0", APACHE,
        "https://github.com/Kotlin/kotlinx.coroutines"
    ),
    LicenseEntry(
        "Kotlin Standard Library", "JetBrains", null, APACHE,
        "https://github.com/JetBrains/kotlin"
    )
)

private val DATA = listOf(
    LicenseEntry(
        "Lichess Puzzle Database", "Lichess", null, "CC0 1.0",
        "https://database.lichess.org/#puzzles", R.string.licenses_note_puzzles
    ),
    LicenseEntry(
        "Lichess Standard Games Database (2013-05)", "Lichess", null, "CC0 1.0",
        "https://database.lichess.org/#standard_games", R.string.licenses_note_games
    ),
    LicenseEntry(
        "lichess-org/chess-openings", "Lichess contributors", null, "CC0 1.0",
        "https://github.com/lichess-org/chess-openings", R.string.licenses_note_openings
    )
)

private val ASSETS = listOf(
    LicenseEntry(
        "Misty forest scene with pine trees in haze", "Jean-Daniel Francoeur", null, "Pexels License",
        "https://www.pexels.com/photo/misty-forest-scene-with-pine-trees-in-haze-31737036/",
        R.string.licenses_note_photo
    ),
    LicenseEntry(
        "Material Symbols", "Google", null, APACHE,
        "https://iconbuddy.com/material-symbols", R.string.licenses_note_icons
    ),
    LicenseEntry(
        "Vector Chess Pieces", "RhosGFX", null, "CC0 1.0",
        "https://rhosgfx.itch.io/vector-chess-pieces", R.string.licenses_note_pieces
    )
)

private val RADIO = listOf(
    LicenseEntry(
        "Radio Swiss Jazz", "SRG SSR", null, "\u00A9 SRG SSR",
        "https://www.radioswissjazz.ch/en/reception/internet", R.string.licenses_note_swissjazz
    )
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var showApache by remember { mutableStateOf(false) }

    GlassScaffold(title = stringResource(R.string.licenses_title), onBack = onBack) {
        Text(
            stringResource(R.string.licenses_intro),
            color = ink.copy(alpha = 0.75f),
            style = MaterialTheme.typography.bodyMedium
        )

        SetupSectionLabel(stringResource(R.string.licenses_group_libraries))
        LIBRARIES.forEach { entry -> LicenseCard(entry) { openUrl(context, entry.url) } }
        GlassOutlinedButton(onClick = { showApache = true }, modifier = Modifier.fillMaxWidth()) {
            ButtonIcon(R.drawable.ic_copyright)
            Text(stringResource(R.string.licenses_apache_full), color = ink)
        }

        SetupSectionLabel(stringResource(R.string.licenses_group_data))
        DATA.forEach { entry -> LicenseCard(entry) { openUrl(context, entry.url) } }

        SetupSectionLabel(stringResource(R.string.licenses_group_assets))
        ASSETS.forEach { entry -> LicenseCard(entry) { openUrl(context, entry.url) } }

        SetupSectionLabel(stringResource(R.string.licenses_group_radio))
        Text(
            stringResource(R.string.licenses_radio_hint),
            color = ink.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
        RADIO.forEach { entry -> LicenseCard(entry) { openUrl(context, entry.url) } }
    }

    if (showApache) {
        ApacheLicenseDialog(onDismiss = { showApache = false })
    }
}

@Composable
private fun LicenseCard(entry: LicenseEntry, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, color = ink, style = MaterialTheme.typography.titleMedium)
                Text(
                    listOfNotNull(entry.author, entry.version).joinToString(" \u00B7 "),
                    color = ink.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(entry.license, color = DialogAccent, style = MaterialTheme.typography.bodySmall)
                entry.noteRes?.let { note ->
                    Text(
                        stringResource(note),
                        color = ink.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            AppIcon(R.drawable.ic_arrow_forward, size = 16.dp, tint = ink.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun ApacheLicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val text = remember {
        runCatching {
            context.resources.openRawResource(R.raw.license_apache_2_0)
                .bufferedReader().use { it.readText() }
        }.getOrDefault("https://www.apache.org/licenses/LICENSE-2.0")
            .trim()
            .split(Regex("\n\\s*\n"))
            .joinToString("\n\n") { paragraph -> paragraph.lines().joinToString(" ") { it.trim() } }
    }
    AppDialog(
        onDismissRequest = onDismiss,
        title = APACHE,
        actions = { DialogAction(stringResource(R.string.chat_close), onDismiss) }
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
            Text(text, color = ink.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun openUrl(context: Context, url: String) = cc.skysparkle.matewave.settings.AppLinks.open(context, url)
