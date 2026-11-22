package com.willian.tvaberta.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private class LongFlag {
    var fired = false
}

/**
 * Item que o controle remoto alcança e aciona. Quando está com o foco ganha borda branca e cresce um pouco.
 * OK (ou Enter) = onClick. OK segurado ou tecla Menu = onLongClick (se informado).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.tvClickable(
    shape: Shape = RoundedCornerShape(14.dp),
    onFocus: ((Boolean) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val flag = remember { LongFlag() }
    // Itens recém-criados (menus, janelas) ignoram o OK por um instante: evita acionar sem querer
    // a primeira opção quando o botão que abriu o menu ainda está apertado.
    val born = remember { System.currentTimeMillis() }
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "tvscale")
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .border(if (focused) 3.dp else 0.dp, if (focused) Color.White else Color.Transparent, shape)
        .onPreviewKeyEvent { e ->
            if (!focused) return@onPreviewKeyEvent false
            val k = e.key
            val ok = k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter
            when {
                // Tecla Menu: abre as ações na hora; soltar ou segurar não faz mais nada.
                k == Key.Menu && onLongClick != null -> {
                    if (e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount == 0) onLongClick()
                    true
                }
                // OK segurado: repetições nunca viram novos cliques; a primeira dispara o menu.
                ok && e.type == KeyEventType.KeyDown && e.nativeKeyEvent.repeatCount > 0 -> {
                    if (onLongClick != null && !flag.fired && e.nativeKeyEvent.isLongPress) {
                        flag.fired = true
                        onLongClick()
                    }
                    true
                }
                ok && e.type == KeyEventType.KeyUp && flag.fired -> {
                    flag.fired = false
                    true
                }
                else -> false
            }
        }
        .onFocusChanged {
            focused = it.isFocused
            onFocus?.invoke(it.isFocused)
        }
        .combinedClickable(
            onClick = { if (System.currentTimeMillis() - born > 450L) onClick() },
            onLongClick = onLongClick
        )
}

@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    danger: Boolean = false,
    selected: Boolean = false,
    requester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused && danger -> Color(0xFFB3261E)
        focused -> Color(0xFF2E7D32)
        danger -> Color(0xFF5A1D22)
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focused -> Color.White
        danger -> Color(0xFFFFB4B4)
        selected -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .tvClickable(shape, onFocus = { focused = it }, onLongClick = onLongClick, onClick = onClick)
            .background(bg, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = fg, maxLines = 1, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TvTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TvSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        content()
    }
}

@Composable
private fun RowBox(
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .tvClickable(shape, onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) Color(0xFFFF8A8A) else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

@Composable
fun TvSwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowBox(title, subtitle, onClick = { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** OK passa para a próxima opção; mostra a opção atual à direita. */
@Composable
fun TvChoiceRow(
    title: String,
    options: List<String>,
    selected: Int,
    requester: FocusRequester? = null,
    onSelect: (Int) -> Unit
) {
    val i = selected.coerceIn(0, options.lastIndex)
    RowBox(title, "Pressione OK para trocar", requester = requester, onClick = { onSelect((i + 1) % options.size) }) {
        Text(
            options[i] + "  ›",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun TvIntChoiceRow(title: String, labels: List<String>, values: List<Int>, current: Int, onSelect: (Int) -> Unit) {
    val idx = values.indexOf(current).let { if (it < 0) 0 else it }
    TvChoiceRow(title, labels, idx) { onSelect(values[it]) }
}

@Composable
fun TvActionRow(title: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    RowBox(title, subtitle, danger, onClick = onClick) {}
}

@Composable
fun TvInfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TvSpacer(h: Int = 8) {
    Spacer(Modifier.height(h.dp))
}

@Composable
fun TvEmpty(title: String, text: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 48.dp)
        )
        if (actionLabel != null) {
            TvSpacer(8)
            TvButton(actionLabel, onClick = onAction)
        }
    }
}
