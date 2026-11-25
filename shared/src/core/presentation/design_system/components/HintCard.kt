package de.franzsw.ctx2cal.core.presentation.design_system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun HintCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    borderWidth: Dp = 1.dp,
    hasDismissButton: Boolean = false,
    onDismiss: () -> Unit = {},
    hintTitle: String = "Info",
    accentColor: Color = MaterialTheme.colorScheme.primary,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
    content: @Composable (ColumnScope.() -> Unit)
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(width = borderWidth, color = accentColor, RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = accentColor, CircleShape)
                    .clearAndSetSemantics {}
            ) {
                Text(
                    text = "i",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = hintTitle,
                color = accentColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (hasDismissButton) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("Dismiss")
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

@Composable
fun ErrorCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    borderWidth: Dp = 1.dp,
    title: String = "Error",
    accentColor: Color = MaterialTheme.colorScheme.error,
    backgroundColor: Color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    content: @Composable (ColumnScope.() -> Unit)
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(width = borderWidth, color = accentColor, RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = accentColor, CircleShape)
                    .clearAndSetSemantics {}
            ) {
                Text(
                    text = "!",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = accentColor,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}
