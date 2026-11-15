package com.pebble.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MxButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog
import androidx.compose.material3.AlertDialog as M3AlertDialog
import androidx.compose.material3.Button as M3Button
import androidx.compose.material3.FilledTonalButton as M3FilledTonalButton
import androidx.compose.material3.LoadingIndicator as M3LoadingIndicator
import androidx.compose.material3.ModalBottomSheet as M3ModalBottomSheet
import androidx.compose.material3.OutlinedButton as M3OutlinedButton
import androidx.compose.material3.OutlinedTextField as M3OutlinedTextField
import androidx.compose.material3.TextButton as M3TextButton
import top.yukonga.miuix.kmp.basic.Button as MxButton
import top.yukonga.miuix.kmp.basic.TextField as MxTextField

/*
 * Same-package versions of the Material components the app uses. Files call them by their plain names; inside the
 * Miuix style they draw the real Miuix components, otherwise the Material ones (nothing else changes).
 */

/** Runs [content] with Material text/icons tinted like the Miuix button they sit in. */
@Composable
private fun RowScope.Tinted(color: Color, content: @Composable RowScope.() -> Unit) =
    CompositionLocalProvider(LocalContentColor provides color) { content() }

@Composable
fun Button(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shapes: ButtonShapes? = null, content: @Composable RowScope.() -> Unit) {
    if (LocalMiuix.current) MxButton(onClick, modifier, enabled = enabled, colors = MxButtonDefaults.buttonColorsPrimary()) { Tinted(MiuixTheme.colorScheme.onPrimary, content) }
    else M3Button(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes ?: ButtonDefaults.shapes(), content = content)
}

@Composable
fun FilledTonalButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shapes: ButtonShapes? = null, content: @Composable RowScope.() -> Unit) {
    if (LocalMiuix.current) MxButton(onClick, modifier, enabled = enabled) { Tinted(MiuixTheme.colorScheme.onSecondaryVariant, content) }
    else M3FilledTonalButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes ?: ButtonDefaults.shapes(), content = content)
}

@Composable
fun OutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, shapes: ButtonShapes? = null, content: @Composable RowScope.() -> Unit) {
    if (LocalMiuix.current) MxButton(onClick, modifier, enabled = enabled) { Tinted(MiuixTheme.colorScheme.onSecondaryVariant, content) }
    else M3OutlinedButton(onClick = onClick, modifier = modifier, enabled = enabled, shapes = shapes ?: ButtonDefaults.shapes(), content = content)
}

@Composable
fun TextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    if (LocalMiuix.current) {
        val mx = MiuixTheme.colorScheme
        MxButton(
            onClick, modifier, enabled = enabled, minWidth = 0.dp,
            colors = MxButtonDefaults.buttonColors(color = Color.Transparent, disabledColor = Color.Transparent, contentColor = mx.primary, disabledContentColor = mx.disabledOnSecondaryVariant),
        ) { Tinted(if (enabled) mx.primary else mx.disabledOnSecondaryVariant, content) }
    } else M3TextButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    if (LocalMiuix.current) InfiniteProgressIndicator(modifier, color = MiuixTheme.colorScheme.primary)
    else M3LoadingIndicator(modifier)
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
) {
    if (LocalMiuix.current) {
        WindowDialog(show = true, onDismissRequest = onDismissRequest) {
            CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurfaceContainer) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (icon != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { icon() }
                    if (title != null) ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center)) { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { title() } }
                    if (text != null) ProvideTextStyle(MaterialTheme.typography.bodyMedium) { text() }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (dismissButton != null) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { dismissButton() }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { confirmButton() }
                    }
                }
            }
        }
    } else M3AlertDialog(onDismissRequest = onDismissRequest, confirmButton = confirmButton, modifier = modifier, dismissButton = dismissButton, icon = icon, title = title, text = text)
}

@Composable
fun ModalBottomSheet(onDismissRequest: () -> Unit, containerColor: Color = Color.Unspecified, content: @Composable ColumnScope.() -> Unit) {
    if (LocalMiuix.current) {
        WindowBottomSheet(show = true, onDismissRequest = onDismissRequest) {
            CompositionLocalProvider(LocalContentColor provides MiuixTheme.colorScheme.onSurfaceContainer) { Column(content = content) }
        }
    } else M3ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = if (containerColor == Color.Unspecified) BottomSheetDefaults.ContainerColor else containerColor,
        content = content,
    )
}

/** Text field: the Miuix one (label inside) in the Miuix style. */
@Composable
fun OutlinedTextField(
    value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, label: String = "",
    singleLine: Boolean = false, shape: Shape? = null, keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    if (LocalMiuix.current) MxTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, label = label,
        singleLine = singleLine, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
    ) else M3OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = modifier, enabled = enabled, label = { Text(label) },
        singleLine = singleLine, shape = shape ?: MaterialTheme.shapes.small, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions,
    )
}
