package com.lingua.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingua.app.R
import com.lingua.app.data.remote.PromptBuilder
import com.lingua.app.data.settings.AppSettings
import com.lingua.app.domain.Language

/**
 * Prompt configuration.
 *
 * The built-in prompt is shown read-only — it carries the JSON contract the parser depends on. Users
 * can keep their own instructions alongside it, and those are appended when the request is built.
 */
@Composable
fun PromptSection(
  settings: AppSettings,
  targetLanguage: Language,
  onCustomPromptChange: (String) -> Unit,
) {
  var showPreview by remember { mutableStateOf(false) }
  var editorDraft by remember { mutableStateOf<String?>(null) }

  val customPrompt = settings.customPrompt
  val preview =
    customPrompt
      .replace('\n', ' ')
      .trim()
      .takeIf { it.isNotEmpty() }
      ?: stringResource(R.string.settings_prompt_custom_empty)

  Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
    Column {
      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_prompt_builtin)) },
        supportingContent = { Text(stringResource(R.string.settings_prompt_builtin_summary)) },
        leadingContent = {
          Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        trailingContent = {
          Icon(
            Icons.Outlined.Visibility,
            contentDescription = stringResource(R.string.settings_prompt_preview_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable { showPreview = true },
      )

      HorizontalDivider()

      ListItem(
        headlineContent = { Text(stringResource(R.string.settings_prompt_custom)) },
        supportingContent = { Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        leadingContent = {
          Icon(
            Icons.Outlined.Edit,
            contentDescription = null,
            tint =
              if (customPrompt.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
              else MaterialTheme.colorScheme.primary,
          )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable { editorDraft = customPrompt },
      )
    }
  }

  if (showPreview) {
    PromptPreviewSheet(
      targetLanguage = targetLanguage,
      customPrompt = customPrompt,
      onEdit = {
        showPreview = false
        editorDraft = customPrompt
      },
      onDismiss = { showPreview = false },
    )
  }

  editorDraft?.let { draft ->
    CustomPromptEditorSheet(
      initialText = draft,
      onDismiss = { editorDraft = null },
      onSave = { text ->
        onCustomPromptChange(text)
        editorDraft = null
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptPreviewSheet(
  targetLanguage: Language,
  customPrompt: String,
  onEdit: () -> Unit,
  onDismiss: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val builtIn = remember(targetLanguage) { PromptBuilder.builtInPrompt(targetLanguage, null) }
  val custom = PromptBuilder.sanitizeCustomInstructions(customPrompt)

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(bottom = 32.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.settings_prompt_preview_title),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
        }
      }

      Text(
        text = stringResource(R.string.settings_prompt_preview_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      PromptBlock(
        label = stringResource(R.string.settings_prompt_preview_builtin),
        text = builtIn,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      )

      PromptBlock(
        label = stringResource(R.string.settings_prompt_preview_custom),
        text = custom ?: stringResource(R.string.settings_prompt_custom_empty),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
      )

      TextButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.settings_prompt_custom), modifier = Modifier.padding(start = 8.dp))
      }
    }
  }
}

@Composable
private fun PromptBlock(label: String, text: String, containerColor: Color) {
  Surface(color = containerColor, shape = MaterialTheme.shapes.large) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
      )
      SelectionContainer {
        Text(
          text = text,
          style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomPromptEditorSheet(
  initialText: String,
  onDismiss: () -> Unit,
  onSave: (String) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var text by rememberSaveable(initialText) { mutableStateOf(initialText) }

  val limit = PromptBuilder.MAX_CUSTOM_PROMPT_LENGTH
  val isTooLong = text.length > limit

  val examples =
    listOf(
      stringResource(R.string.settings_prompt_example_formal),
      stringResource(R.string.settings_prompt_example_tech),
      stringResource(R.string.settings_prompt_example_short),
    )

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .padding(horizontal = 24.dp)
          .padding(bottom = 32.dp)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.settings_prompt_edit_title),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss) {
          Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_close))
        }
      }

      Text(
        text = stringResource(R.string.settings_prompt_edit_helper),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= limit) text = it },
        placeholder = { Text(stringResource(R.string.settings_prompt_edit_hint)) },
        isError = isTooLong,
        minLines = 4,
        maxLines = 10,
        shape = MaterialTheme.shapes.large,
        supportingText = {
          Text(
            text =
              if (isTooLong) stringResource(R.string.settings_prompt_too_long, limit)
              else stringResource(R.string.settings_prompt_counter, text.length, limit),
            style = MaterialTheme.typography.bodySmall,
          )
        },
        modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
      )

      Text(
        text = stringResource(R.string.settings_prompt_examples),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      examples.forEach { example ->
        AssistChip(
          onClick = { text = if (text.isBlank()) example else "$text\n$example" },
          label = { Text(example, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (text.isNotBlank()) {
          TextButton(onClick = { text = "" }) { Text(stringResource(R.string.settings_prompt_clear)) }
        }
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        TextButton(onClick = { onSave(text) }, enabled = !isTooLong) {
          Text(stringResource(R.string.api_editor_save))
        }
      }
    }
  }
}
