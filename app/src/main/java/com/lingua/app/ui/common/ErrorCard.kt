package com.lingua.app.ui.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingua.app.R

/**
 * Inline error surface used by the translate screen and the API tests. Carries an optional retry
 * action and a collapsible raw payload so failures stay diagnosable without being noisy.
 */
@Composable
fun ErrorCard(
  message: ErrorMessage,
  modifier: Modifier = Modifier,
  detail: String? = null,
  onRetry: (() -> Unit)? = null,
) {
  val context = LocalContext.current
  val text =
    if (message.args.isEmpty()) stringResource(message.resId)
    else context.getString(message.resId, *message.args.toTypedArray())

  var expanded by remember { mutableStateOf(false) }

  Card(
    modifier = modifier.fillMaxWidth().animateContentSize(),
    shape = RoundedCornerShape(20.dp),
    colors =
      CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
      ),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = Icons.Outlined.ErrorOutline, contentDescription = null, modifier = Modifier.size(22.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
      }

      if (!detail.isNullOrBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
          if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
          }
          TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(R.string.error_detail))
            Icon(
              imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
              contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand),
              modifier = Modifier.size(18.dp),
            )
          }
        }
        if (expanded) {
          Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = detail,
              style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
              maxLines = 12,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.padding(12.dp),
            )
          }
        }
      } else if (onRetry != null) {
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
      }
    }
  }
}

/** Section heading used across the settings screen. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
  )
}
