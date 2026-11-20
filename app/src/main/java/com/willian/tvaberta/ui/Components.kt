package com.willian.tvaberta.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.willian.tvaberta.data.AppSettings
import com.willian.tvaberta.data.ChannelItem

/** Ações disponíveis em cada canal (menu ⋮). */
class ChannelActions(
    val toggleFavorite: (ChannelItem) -> Unit,
    val disable: (ChannelItem) -> Unit,
    val enable: (ChannelItem) -> Unit,
    val delete: (ChannelItem) -> Unit,
    val rename: (ChannelItem) -> Unit,
    val copyLink: (ChannelItem) -> Unit,
)

// ---------------------------------------------------------------------------------- canais

@Composable
fun ChannelLogo(item: ChannelItem, showLogos: Boolean, modifier: Modifier = Modifier) {
    val logo = item.ch.logo
    var failed by remember(logo) { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFF1F3F7)),
        contentAlignment = Alignment.Center
    ) {
        if (showLogos && !logo.isNullOrBlank() && !failed) {
            AsyncImage(
                model = logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        } else {
            val initial = item.title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
            Text(
                initial,
                color = Color(0xFF5B6575),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

@Composable
fun MiniTag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1
    )
}

@Composable
fun ChannelMenu(item: ChannelItem, actions: ChannelActions, tint: Color, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.MoreVert, contentDescription = "Mais ações", tint = tint)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (item.st.favorite) "Remover dos favoritos" else "Favoritar") },
                leadingIcon = {
                    Icon(if (item.st.favorite) Icons.Default.StarBorder else Icons.Default.Star, null)
                },
                onClick = {
                    open = false
                    actions.toggleFavorite(item)
                }
            )
            if (item.st.disabled) {
                DropdownMenuItem(
                    text = { Text("Ativar canal") },
                    leadingIcon = { Icon(Icons.Default.Visibility, null) },
                    onClick = {
                        open = false
                        actions.enable(item)
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Desativar canal") },
                    leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                    onClick = {
                        open = false
                        actions.disable(item)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Renomear") },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = {
                    open = false
                    actions.rename(item)
                }
            )
            DropdownMenuItem(
                text = { Text("Copiar link") },
                leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                onClick = {
                    open = false
                    actions.copyLink(item)
                }
            )
            DropdownMenuItem(
                text = { Text("Excluir canal", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    actions.delete(item)
                }
            )
        }
    }
}

@Composable
fun ChannelGridCard(item: ChannelItem, s: AppSettings, actions: ChannelActions, onPlay: () -> Unit) {
    Card(
        onClick = onPlay,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box {
            ChannelLogo(
                item,
                s.showLogos,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
            )
            if (item.st.favorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favorito",
                    tint = Color(0xFFF5B301),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
            ChannelMenu(item, actions, Color(0xFF5B6575), Modifier.align(Alignment.TopEnd))
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    catLabel(item.ch.groups.first()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (s.showBadges) {
                    item.ch.quality?.let {
                        Spacer(Modifier.width(6.dp))
                        MiniTag(it)
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelListRow(item: ChannelItem, s: AppSettings, actions: ChannelActions, onPlay: () -> Unit) {
    val compact = s.compactList
    Card(
        onClick = onPlay,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.padding(if (compact) 8.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(item, s.showLogos, Modifier.size(if (compact) 44.dp else 58.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    item.ch.groups.joinToString(" · ") { catLabel(it) },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (s.showBadges) {
                item.ch.quality?.let {
                    MiniTag(it)
                    Spacer(Modifier.width(6.dp))
                }
            }
            if (item.st.favorite) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Favorito",
                    tint = Color(0xFFF5B301),
                    modifier = Modifier.size(18.dp)
                )
            }
            ChannelMenu(item, actions, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------------------------- estrutura

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        actions()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

// ---------------------------------------------------------------------------------- configurações

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            title.uppercase(),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Column(
            Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
            content = content
        )
    }
}

@Composable
private fun RowShell(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell(title, subtitle, { onChange(!checked) }) {
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    RowShell(title, options.getOrElse(selected) { options.first() }, { open = true }) {}
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEachIndexed { i, label ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    open = false
                                    onSelect(i)
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = i == selected, onClick = {
                                open = false
                                onSelect(i)
                            })
                            Text(label, Modifier.padding(start = 4.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Fechar") } }
        )
    }
}

/** Escolha em que o valor guardado é um número (não a posição). */
@Composable
fun IntChoiceRow(
    title: String,
    labels: List<String>,
    values: List<Int>,
    current: Int,
    onSelect: (Int) -> Unit
) {
    val idx = values.indexOf(current).coerceAtLeast(0)
    ChoiceRow(title, labels, idx) { onSelect(values[it]) }
}

@Composable
fun NavRow(title: String, subtitle: String? = null, icon: ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionRow(title: String, subtitle: String? = null, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun InfoRow(title: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RowDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant
    )
}

@Composable
fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Cor de destaque", style = MaterialTheme.typography.bodyLarge)
        Text(
            AccentPalette.getOrNull(selected)?.first ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentPalette.forEachIndexed { i, (name, color) ->
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(color)
                        .let {
                            if (i == selected) it.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else it
                        }
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    if (i == selected) Icon(Icons.Default.Check, name, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun SimpleScroll(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        content = content
    )
}

@Composable
fun DialogList(maxHeight: Dp = 380.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        content = content
    )
}
