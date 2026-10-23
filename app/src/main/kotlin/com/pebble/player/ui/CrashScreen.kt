package com.pebble.player.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown on the next launch after a crash, so the stack trace can be copied and shared. */
@Composable
fun CrashScreen(report: String, onContinue: () -> Unit) {
    val ctx = LocalContext.current
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pebble closed unexpectedly", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Copy this report and send it so the problem can be fixed.", style = MaterialTheme.typography.bodyMedium)
                SelectionContainer(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    Text(report.take(6000), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button({
                        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Pebble crash", report))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }) { Text("Copy") }
                    OutlinedButton(onContinue) { Text("Continue") }
                }
            }
        }
    }
}
