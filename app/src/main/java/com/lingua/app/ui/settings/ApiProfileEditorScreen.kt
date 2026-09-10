package com.lingua.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingua.app.R
import com.lingua.app.ui.common.SectionHeader
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ApiProfileEditorScreen(
  targetProfileId: String?,
  state: ApiEditorUiState,
  onBind: (String?) -> Unit,
  onName: (String) -> Unit,
  onBaseUrl: (String) -> Unit,
  onApiKey: (String) -> Unit,
  onModel: (String) -> Unit,
  onTemperature: (Double) -> Unit,
  onTimeout: (Int) -> Unit,
  onJsonMode: (Boolean) -> Unit,
  onHeaders: (String) -> Unit,
  onPreset: (ProviderPreset) -> Unit,
  onFetchModels: () -> Unit,
  onTestConnection: () -> Unit,
  onTestTranslation: () -> Unit,
  onSave: () -> Unit,
  onDelete: () -> Unit,
  onBack: () -> Unit,
) {
  // Bind from the navigation argument, never from state that binding itself populates.
  LaunchedEffect(targetProfileId) { onBind(targetProfileId) }

  var apiKeyVisible by remember { mutableStateOf(false) }
  var advancedExpanded by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Text(
            stringResource(
              if (state.isEditing) R.string.api_editor_edit_title else R.string.api_editor_new_title
            )
          )
        },
        windowInsets = WindowInsets(0, 0, 0, 0),
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          TextButton(onClick = onSave) { Text(stringResource(R.string.api_editor_save)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
      )
    },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      SectionHeader(stringResource(R.string.api_editor_preset))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProviderPreset.entries.forEach { preset ->
          FilterChip(
            selected = state.preset == preset,
            onClick = { onPreset(preset) },
            label = { Text(stringResource(preset.labelRes)) },
          )
        }
      }

      OutlinedTextField(
        value = state.name,
        onValueChange = onName,
        label = { Text(stringResource(R.string.api_editor_name)) },
        placeholder = { Text(stringResource(R.string.api_editor_name_hint)) },
        singleLine = true,
        isError = state.validation.name,
        supportingText = { if (state.validation.name) Text(stringResource(R.string.api_editor_validation_name)) },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = state.baseUrl,
        onValueChange = onBaseUrl,
        label = { Text(stringResource(R.string.api_editor_base_url)) },
        placeholder = { Text(stringResource(R.string.api_editor_base_url_hint)) },
        singleLine = true,
        isError = state.validation.baseUrl,
        supportingText = {
          Text(
            text =
              if (state.validation.baseUrl) stringResource(R.string.api_editor_validation_url)
              else stringResource(R.string.api_editor_base_url_helper),
            style = MaterialTheme.typography.bodySmall,
          )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      )

      if (state.endpointPreview.isNotEmpty()) {
        Text(
          text = state.endpointPreview,
          style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 4.dp),
        )
      }

      OutlinedTextField(
        value = state.apiKey,
        onValueChange = onApiKey,
        label = { Text(stringResource(R.string.api_editor_api_key)) },
        placeholder = { Text(stringResource(R.string.api_editor_api_key_hint)) },
        singleLine = true,
        isError = state.keyDecryptFailed,
        visualTransformation =
          if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
            Icon(
              imageVector = if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
              contentDescription =
                stringResource(if (apiKeyVisible) R.string.api_editor_hide_key else R.string.api_editor_show_key),
            )
          }
        },
        supportingText = {
          Text(
            text =
              if (state.keyDecryptFailed) stringResource(R.string.api_editor_api_key_missing)
              else stringResource(R.string.api_editor_api_key_helper),
            style = MaterialTheme.typography.bodySmall,
          )
        },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      )

      OutlinedTextField(
        value = state.model,
        onValueChange = onModel,
        label = { Text(stringResource(R.string.api_editor_model)) },
        placeholder = { Text(stringResource(R.string.api_editor_model_hint)) },
        singleLine = true,
        isError = state.validation.model,
        supportingText = { if (state.validation.model) Text(stringResource(R.string.api_editor_validation_model)) },
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      )

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onFetchModels, enabled = !state.isFetchingModels) {
          if (state.isFetchingModels) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
          } else {
            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
          }
          Text(stringResource(R.string.api_editor_fetch_models), modifier = Modifier.padding(start = 8.dp))
        }
        state.modelsMessage?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      if (state.models.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          state.models.take(12).forEach { model ->
            AssistChip(
              onClick = { onModel(model) },
              label = { Text(model, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
          }
        }
      }

      AdvancedSection(
        state = state,
        expanded = advancedExpanded,
        onToggle = { advancedExpanded = !advancedExpanded },
        onTemperature = onTemperature,
        onTimeout = onTimeout,
        onJsonMode = onJsonMode,
        onHeaders = onHeaders,
      )

      TestSection(
        state = state,
        onTestConnection = onTestConnection,
        onTestTranslation = onTestTranslation,
      )

      if (state.isEditing) {
        OutlinedButton(
          onClick = { confirmDelete = true },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
          Text(stringResource(R.string.api_editor_delete), modifier = Modifier.padding(start = 8.dp))
        }
      }
    }
  }

  if (confirmDelete) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.settings_delete_profile_confirm_title)) },
      text = { Text(stringResource(R.string.settings_delete_profile_confirm_body, state.name)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmDelete = false
            onDelete()
          }
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AdvancedSection(
  state: ApiEditorUiState,
  expanded: Boolean,
  onToggle: () -> Unit,
  onTemperature: (Double) -> Unit,
  onTimeout: (Int) -> Unit,
  onJsonMode: (Boolean) -> Unit,
  onHeaders: (String) -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().animateContentSize(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.api_editor_advanced),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggle) {
          Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = stringResource(if (expanded) R.string.cd_collapse else R.string.cd_expand),
          )
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Column {
            Text(
              text = "${stringResource(R.string.api_editor_temperature)} · ${formatTemperature(state.temperature)}",
              style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
              value = state.temperature.toFloat(),
              onValueChange = { onTemperature(it.toDouble()) },
              valueRange = 0f..1f,
            )
          }

          OutlinedTextField(
            value = state.timeoutSeconds.toString(),
            onValueChange = { text -> text.filter { it.isDigit() }.toIntOrNull()?.let(onTimeout) },
            label = { Text(stringResource(R.string.api_editor_timeout)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          )

          Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
              Text(stringResource(R.string.api_editor_json_mode), style = MaterialTheme.typography.bodyLarge)
              Text(
                text = stringResource(R.string.api_editor_json_mode_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(checked = state.jsonMode, onCheckedChange = onJsonMode)
          }

          OutlinedTextField(
            value = state.headersText,
            onValueChange = onHeaders,
            label = { Text(stringResource(R.string.api_editor_headers)) },
            placeholder = { Text(stringResource(R.string.api_editor_headers_hint)) },
            minLines = 2,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun TestSection(
  state: ApiEditorUiState,
  onTestConnection: () -> Unit,
  onTestTranslation: () -> Unit,
) {
  var rawExpanded by remember { mutableStateOf(false) }

  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth().animateContentSize(),
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Science, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.api_test_section), style = MaterialTheme.typography.titleSmall)
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onTestConnection, enabled = state.test !is TestState.Running, modifier = Modifier.weight(1f)) {
          Text(stringResource(R.string.api_test_connection), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        OutlinedButton(
          onClick = onTestTranslation,
          enabled = state.test !is TestState.Running,
          modifier = Modifier.weight(1f),
        ) {
          Text(stringResource(R.string.api_test_translation), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }

      when (val test = state.test) {
        TestState.Idle -> Unit
        TestState.Running -> {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(stringResource(R.string.api_test_section), style = MaterialTheme.typography.bodyMedium)
          }
        }
        is TestState.Success -> TestSuccess(test, rawExpanded, onToggleRaw = { rawExpanded = !rawExpanded })
        is TestState.Failure -> {
          Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                  Icons.Outlined.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                  text = stringResource(R.string.api_test_failure),
                  style = MaterialTheme.typography.titleSmall,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                )
              }
              val context = androidx.compose.ui.platform.LocalContext.current
              Text(
                text =
                  if (test.message.args.isEmpty()) stringResource(test.message.resId)
                  else context.getString(test.message.resId, *test.message.args.toTypedArray()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
              )
              test.detail?.let {
                Text(
                  text = it,
                  style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  maxLines = 8,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun TestSuccess(state: TestState.Success, rawExpanded: Boolean, onToggleRaw: () -> Unit) {
  Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(16.dp)) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
          Icons.Outlined.CheckCircleOutline,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
          text = stringResource(R.string.api_test_success),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Box(modifier = Modifier.weight(1f))
        Text(
          text = stringResource(R.string.api_test_latency, state.latencyMs),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      state.model?.let {
        Text(
          text = it,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      state.modelsAvailable?.let { available ->
        Text(
          text =
            stringResource(
              R.string.api_test_models,
              stringResource(
                if (available) R.string.api_test_models_available else R.string.api_test_models_unavailable
              ),
            ),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
      }

      if (state.reply.isNotBlank()) {
        Text(
          text = stringResource(R.string.api_test_reply),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
          text = state.reply,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSecondaryContainer,
          maxLines = 10,
          overflow = TextOverflow.Ellipsis,
        )
      }

      if (state.raw.isNotBlank()) {
        TextButton(onClick = onToggleRaw) {
          Text(
            stringResource(if (rawExpanded) R.string.api_test_hide_raw else R.string.api_test_show_raw),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
          )
        }
        AnimatedVisibility(visible = rawExpanded) {
          Text(
            text = state.raw,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 20,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

private fun formatTemperature(value: Double): String = (value * 100).roundToInt().let { "${it / 100.0}" }
