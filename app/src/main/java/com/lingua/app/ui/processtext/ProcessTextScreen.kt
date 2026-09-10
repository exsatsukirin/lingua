package com.lingua.app.ui.processtext

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingua.app.MainActivity
import com.lingua.app.R
import com.lingua.app.ui.common.ErrorCard
import kotlinx.coroutines.launch

private const val CLIP_LABEL = "Lingua"

/**
 * Dialog-style popup shown over whatever app the text was selected in.
 *
 * Tapping the scrim or pressing back dismisses it; the card itself swallows taps so the selection
 * stays readable while the user copies the result.
 */
@Composable
fun ProcessTextScreen(
  state: ProcessTextUiState,
  onStart: () -> Unit,
  onRetry: () -> Unit,
  onToggleFavorite: () -> Unit,
  onDismiss: () -> Unit,
) {
  LaunchedEffect(Unit) { onStart() }

  val context = LocalContext.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()

  val openSettings = {
    context.startActivity(
      Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    )
    onDismiss()
  }

  Box(
    modifier =
      Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f))
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = onDismiss,
        ),
    contentAlignment = Alignment.Center,
  ) {
    Card(
      shape = MaterialTheme.shapes.extraLarge,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 20.dp)
          .heightIn(max = 560.dp)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
          ),
    ) {
      Column(modifier = Modifier.fillMaxWidth()) {
        Header(state = state, onDismiss = onDismiss)

        HorizontalDivider()

        Column(
          modifier =
            Modifier.fillMaxWidth()
              .verticalScroll(rememberScrollState())
              .padding(horizontal = 20.dp)
              .padding(vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          // Source
          Text(
            text = stringResource(R.string.process_text_source_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
          ) {
            Text(
              text = state.sourceText,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
          }

          when {
            state.isLoading -> TranslatingIndicator()
            state.error != null -> {
              ErrorCard(message = state.error, detail = state.errorDetail, onRetry = onRetry)
              if (state.needsSetup) {
                // Without this the popup would be a dead end for a fresh install.
                TextButton(onClick = openSettings, modifier = Modifier.fillMaxWidth()) {
                  Text(stringResource(R.string.translate_no_profile_action))
                }
              }
            }
            state.hasResult -> {
              Text(
                text = stringResource(R.string.history_target_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
              ResultBlock(text = state.translation.orEmpty())
            }
          }
        }

        if (state.hasResult) {
          HorizontalDivider()
          Actions(
            favorite = state.favorite,
            canFavorite = state.savedRecordId != null,
            onCopy = {
              val text = state.translation.orEmpty()
              scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text))) }
            },
            onShare = {
              val text = state.translation.orEmpty()
              val send =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, text)
                }
              context.startActivity(Intent.createChooser(send, null))
            },
            onToggleFavorite = onToggleFavorite,
            onDismiss = onDismiss,
          )
        }
      }
    }
  }
}

@Composable
private fun Header(state: ProcessTextUiState, onDismiss: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = state.targetLanguage.nativeName,
      style = MaterialTheme.typography.titleMedium,
    )
    state.detectedLanguage?.let { detected ->
      Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
        Text(
          text = stringResource(R.string.translate_detected, detected.nativeName),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
      }
    }
    Box(modifier = Modifier.weight(1f))
    IconButton(onClick = onDismiss) {
      Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
    }
  }
}

@Composable
private fun TranslatingIndicator() {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    Text(
      text = stringResource(R.string.process_text_translating),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun ResultBlock(text: String) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large) {
    SelectionContainer {
      Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.fillMaxWidth().padding(12.dp),
      )
    }
  }
}

@Composable
private fun Actions(
  favorite: Boolean,
  canFavorite: Boolean,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  onToggleFavorite: () -> Unit,
  onDismiss: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onCopy) {
      Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.translate_copy))
    }
    IconButton(onClick = onShare) {
      Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.translate_share))
    }
    if (canFavorite) {
      IconButton(onClick = onToggleFavorite) {
        Icon(
          imageVector = if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
          contentDescription =
            stringResource(if (favorite) R.string.translate_unfavorite else R.string.translate_favorite),
          tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    Box(modifier = Modifier.weight(1f))
    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
  }
}
