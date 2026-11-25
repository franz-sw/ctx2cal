package de.franzsw.ctx2cal.core.presentation.design_system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    backgroundColor: Color = Color.Transparent,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(width = borderWidth, color = accentColor, RoundedCornerShape(cornerRadius))
            .background(backgroundColor)
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "i",
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(width = 1.dp, color = accentColor, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(hintTitle, color = accentColor)
            if (hasDismissButton) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss")
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}

@Composable
fun ErrorCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    borderWidth: Dp = 1.dp,
    accentColor: Color = MaterialTheme.colorScheme.error,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Column(
        horizontalAlignment = Alignment.Start,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .border(width = borderWidth, color = accentColor, RoundedCornerShape(cornerRadius))
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "!",
                style = MaterialTheme.typography.labelLarge,
                color = accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .border(width = 1.dp, color = accentColor, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Error", color = accentColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
