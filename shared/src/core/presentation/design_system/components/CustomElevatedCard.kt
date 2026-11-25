package de.franzsw.ctx2cal.core.presentation.design_system.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun CustomElevatedCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.elevatedShape,
    border: BorderStroke? = null,
    elevation: CardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 6.dp
    ),
    colors: CardColors = CardDefaults.elevatedCardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ),
    content: @Composable (ColumnScope.() -> Unit)
) {
    Card(
        modifier = modifier,
        shape = shape,
        border = border,
        elevation = elevation,
        colors = colors
    ) {
        content()
    }
}

@Composable
fun CustomElevatedCard(
    onClick: () -> Unit,
    shape: Shape = CardDefaults.elevatedShape,
    elevation: CardElevation = CardDefaults.elevatedCardElevation(
        defaultElevation = 6.dp,
        hoveredElevation = 10.dp
    ),
    colors: CardColors = CardDefaults.elevatedCardColors(MaterialTheme.colorScheme.surface),
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)
) {
    ElevatedCard(
        modifier = modifier,
        shape = shape,
        elevation = elevation,
        onClick = onClick,
        enabled = enabled,
        colors = colors,
    ) {
        content()
    }
}