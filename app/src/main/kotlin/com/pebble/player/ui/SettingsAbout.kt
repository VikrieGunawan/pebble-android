package com.pebble.player.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val REPO = "https://github.com/VikrieGunawan/pebble-android"

/** Settings → About: hero, update check, app/device info, copy / share / source actions. */
@Composable
internal fun AboutPage() {
    val cs = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val version = currentVersion(ctx)
    val pi = remember { try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (_: Exception) { null } }
    val build = if (pi == null) "?" else if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toString() else @Suppress("DEPRECATION") pi.versionCode.toString()
    val updated = pi?.let { java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(it.lastUpdateTime)) } ?: "—"
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
    val device = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
    val android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    val info = "Pebble Player v$version\n${ctx.packageName}\n$device\n$android\nABI: $abi"

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Hero
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(cs.primaryContainer, cs.tertiaryContainer)))
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile("play_circle", 76.dp, filled = true)
            Text("Pebble Player", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = cs.onPrimaryContainer)
            Text(
                "v$version", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = cs.onPrimary,
                modifier = Modifier.clip(CircleShape).background(cs.primary).padding(horizontal = 14.dp, vertical = 5.dp),
            )
            Text(
                t("A calm, fast video player built with Material 3 Expressive."),
                style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer, textAlign = TextAlign.Center,
            )
        }

        PlayCard { UpdateRow() }

        PlayCard {
            SectionLabel("info", t("App info"))
            InfoRow("info", t("Version"), "$version (build $build)")
            InfoRow("label", "Package", ctx.packageName)
            InfoRow("calendar_month", t("Last updated"), updated)
            InfoRow("settings", t("Requires"), "Android 10 or newer")
        }

        PlayCard {
            AboutAction("content_copy", t("Copy app info")) {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pebble Player", info))
                Toast.makeText(ctx, t("Copied"), Toast.LENGTH_SHORT).show()
            }
            AboutAction("share", t("Share Pebble Player")) {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Pebble Player – a calm, fast video player\n$REPO")
                ctx.startActivity(Intent.createChooser(send, t("Share Pebble Player")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            AboutAction("open_in_new", t("Source code on GitHub")) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: Exception) { Toast.makeText(ctx, t("No browser found"), Toast.LENGTH_SHORT).show() }
            }
        }

    }
}

@Composable
private fun InfoRow(icon: String, label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon, 40.dp)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AboutAction(icon: String, text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(18.dp)).background(cs.surfaceContainerHigh).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, null, Modifier.size(22.dp), tint = cs.primary)
        Text(text, Modifier.weight(1f), fontWeight = FontWeight.Bold)
        Icon("chevron_right", null, Modifier.size(22.dp), tint = cs.onSurfaceVariant)
    }
}
