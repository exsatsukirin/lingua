package com.lingua.app.ui.translate

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingua.app.R
import com.lingua.app.domain.Language
import com.lingua.app.ui.common.EmptyState
import com.lingua.app.ui.common.ErrorCard
import com.lingua.app.ui.common.LanguagePickerSheet
import kotlinx.coroutines.launch

private const val CLIP_LABEL = "Lingua"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslateScreen(
  state: TranslateUiState,
  onSourceChange: (String) -> Unit,
  onClear: () -> Unit,
  onTranslate: () -> Unit,
  onTargetLanguage: (Language) -> Unit,
  onSourceOverride: (Language?) -> Unit,
  onSwap: () -> Unit,
  onFavorite: () -> Unit,
  onSave: () -> Unit,
  onExample: (String) -> Unit,
  onOpenSettings: () -> Unit,
  onOpenScreenOcr: () -> Unit,
  onScreenTranslateToggle: (Boolean) -> Unit,
  notificationsBlocked: Boolean,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val copiedMessage = stringResource(R.string.translate_copied)

  var showTargetPicker by remember { mutableStateOf(false) }
  var showSourcePicker by remember { mutableStateOf(false) }

  fun copy(text: String) {
    scope.launch {
      clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text)))
      snackbarHostState.showSnackbar(copiedMessage)
    }
  }

  fun share(text: String) {
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
      }
    context.startActivity(Intent.createChooser(intent, null))
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.translate_title)) },
        windowInsets = WindowInsets(0, 0, 0, 0),
        actions = {
          IconButton(onClick = onOpenScreenOcr) {
            Icon(
              imageVector = Icons.Outlined.Screenshot,
              contentDescription = stringResource(R.string.screen_ocr_open),
            )
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(bottom = 24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.hasProfile) {
        NoProfileCard(onOpenSettings = onOpenSettings)
      }

      ScreenTranslateCard(
        enabled = state.screenTranslateEnabled,
        notificationsBlocked = notificationsBlocked,
        onToggle = onScreenTranslateToggle,
      )

      SourceCard(
        state = state,
        onSourceChange = onSourceChange,
        onClear = onClear,
        onSourceClick = { showSourcePicker = true },
        onExample = onExample,
      )

      TargetRow(
        target = state.targetLanguage,
        sourceOverride = state.sourceOverride,
        canSwap = state.detectedLanguage != null,
        onTargetClick = { showTargetPicker = true },
        onSwap = onSwap,
      )

      Button(
        onClick = onTranslate,
        enabled = state.canTranslate,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
      ) {
        AnimatedVisibility(visible = state.isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp).padding(end = 8.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary,
          )
        }
        Text(stringResource(R.string.translate_action))
      }

      AnimatedVisibility(visible = state.sameLanguageWarning) {
        Text(
          text = stringResource(R.string.translate_same_language_hint),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      state.error?.let { error ->
        ErrorCard(message = error, detail = state.errorDetail, onRetry = onTranslate)
      }

      state.result?.let { result ->
        ResultCard(
          result = result,
          onCopy = { copy(result.text) },
          onShare = { share(result.text) },
          onFavorite = onFavorite,
          onSave = onSave,
        )
      }

      if (state.result == null && state.error == null && !state.isLoading) {
        EmptyState(
          icon = Icons.Outlined.Translate,
          title = stringResource(R.string.translate_empty_title),
          body = stringResource(R.string.translate_empty_body),
        )
      }
    }
  }

  if (showTargetPicker) {
    LanguagePickerSheet(
      title = stringResource(R.string.language_picker_target_title),
      selected = state.targetLanguage,
      includeAuto = false,
      onDismiss = { showTargetPicker = false },
      onSelect = onTargetLanguage,
    )
  }

  if (showSourcePicker) {
    LanguagePickerSheet(
      title = stringResource(R.string.language_picker_source_title),
      selected = state.sourceOverride ?: Language.Auto,
      includeAuto = true,
      onDismiss = { showSourcePicker = false },
      onSelect = { language -> onSourceOverride(language.takeIf { it.code != Language.Auto.code }) },
    )
  }
}

@Composable
private fun SourceCard(
  state: TranslateUiState,
  onSourceChange: (String) -> Unit,
  onClear: () -> Unit,
  onSourceClick: () -> Unit,
  onExample: (String) -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      OutlinedTextField(
        value = state.sourceText,
        onValueChange = onSourceChange,
        placeholder = { Text(stringResource(R.string.translate_source_hint)) },
        isError = state.isTooLong,
        shape = MaterialTheme.shapes.large,
        trailingIcon = {
          if (state.sourceText.isNotEmpty()) {
            IconButton(onClick = onClear) {
              Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.translate_clear))
            }
          }
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 148.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        AssistChip(
          onClick = onSourceClick,
          leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
          label = {
            Text(
              text = state.sourceOverride?.nativeName ?: stringResource(R.string.language_auto),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
        )

        state.detectedLanguage?.let { detected ->
          Text(
            text = stringResource(R.string.translate_detected, detected.nativeName),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
        } ?: Box(modifier = Modifier.weight(1f))

        Text(
          text = stringResource(R.string.translate_char_counter, state.sourceText.length, MAX_SOURCE_LENGTH),
          style = MaterialTheme.typography.labelMedium,
          color =
            if (state.isTooLong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      if (state.isTooLong) {
        Text(
          text = stringResource(R.string.translate_too_long, MAX_SOURCE_LENGTH),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 6.dp),
        )
      }

      if (state.sourceText.isEmpty()) {
        val exampleOne = stringResource(R.string.translate_example_one)
        val exampleTwo = stringResource(R.string.translate_example_two)
        Column(
          modifier = Modifier.padding(top = 10.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          Text(
            text = stringResource(R.string.translate_example_hint),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
          ) {
            AssistChip(
              onClick = { onExample(exampleOne) },
              label = { Text(exampleOne, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
            AssistChip(
              onClick = { onExample(exampleTwo) },
              label = { Text(exampleTwo, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun TargetRow(
  target: Language,
  sourceOverride: Language?,
  canSwap: Boolean,
  onTargetClick: () -> Unit,
  onSwap: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        text = stringResource(R.string.translate_target_label),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      AssistChip(
        onClick = onTargetClick,
        label = { Text("${target.nativeName} · ${target.englishName}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
      )
      Box(modifier = Modifier.weight(1f))
      IconButton(onClick = onSwap, enabled = canSwap || sourceOverride != null) {
        Icon(
          imageVector = Icons.Outlined.SwapVert,
          contentDescription = stringResource(R.string.translate_swap),
        )
      }
    }
    sourceOverride?.let {
      Text(
        text = "${stringResource(R.string.translate_manual_source)}: ${it.nativeName}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
      )
    }
  }
}

@Composable
private fun ResultCard(
  result: TranslateResultState,
  onCopy: () -> Unit,
  onShare: () -> Unit,
  onFavorite: () -> Unit,
  onSave: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    modifier = Modifier.fillMaxWidth().animateContentSize(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = stringResource(R.string.translate_result_title),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
        )
        result.sourceLanguage?.let {
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.small,
          ) {
            Text(
              text = it.nativeName,
              style = MaterialTheme.typography.labelSmall,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
          }
        }
        Box(modifier = Modifier.weight(1f))
        Text(
          text = "${result.latencyMs} ms",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      SelectionContainer {
        Text(text = result.text, style = MaterialTheme.typography.bodyLarge)
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onCopy) {
          Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.translate_copy))
        }
        IconButton(onClick = onShare) {
          Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.translate_share))
        }
        if (result.savedRecordId != null) {
          IconButton(onClick = onFavorite) {
            Icon(
              imageVector = if (result.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
              contentDescription =
                stringResource(
                  if (result.favorite) R.string.translate_unfavorite else R.string.translate_favorite
                ),
              tint = if (result.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
        Box(modifier = Modifier.weight(1f))
        if (result.savedRecordId == null) {
          TextButton(onClick = onSave) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.translate_save), modifier = Modifier.padding(start = 6.dp))
          }
        }
      }
    }
  }
}

/**
 * The home-page switch for the resident notification.
 *
 * Off on every launch by design: it is a "use it now" control rather than a setting, and a stale
 * notification the user forgot about is worse than one extra tap.
 */
@Composable
private fun ScreenTranslateCard(
  enabled: Boolean,
  notificationsBlocked: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.padding(16.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
        Icon(
          Icons.Outlined.Screenshot,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(8.dp).size(20.dp),
        )
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.translate_screen_translate_title),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          text =
            when {
              notificationsBlocked ->
                stringResource(R.string.translate_screen_translate_needs_permission)
              enabled -> stringResource(R.string.translate_screen_translate_on)
              else -> stringResource(R.string.translate_screen_translate_off)
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = enabled, onCheckedChange = onToggle)
    }
  }
}

@Composable
private fun NoProfileCard(onOpenSettings: () -> Unit) {
  Card(
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Settings, contentDescription = null)
        Text(text = stringResource(R.string.translate_no_profile_title), style = MaterialTheme.typography.titleSmall)
      }
      Text(text = stringResource(R.string.translate_no_profile_body), style = MaterialTheme.typography.bodyMedium)
      TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.translate_no_profile_action)) }
    }
  }
}
