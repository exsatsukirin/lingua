package com.lingua.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingua.app.R
import com.lingua.app.data.settings.ApiProfile
import com.lingua.app.data.settings.ThemeMode
import com.lingua.app.domain.Language
import com.lingua.app.ui.common.LanguagePickerSheet
import com.lingua.app.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
  state: SettingsUiState,
  onThemeMode: (ThemeMode) -> Unit,
  onDynamicColor: (Boolean) -> Unit,
  onAutoSave: (Boolean) -> Unit,
  onTargetLanguage: (Language) -> Unit,
  onSetActiveProfile: (ApiProfile) -> Unit,
  onDeleteProfile: (ApiProfile) -> Unit,
  onEditProfile: (ApiProfile) -> Unit,
  onAddProfile: () -> Unit,
) {
  var showLanguagePicker by remember { mutableStateOf(false) }
  var profileToDelete by remember { mutableStateOf<ApiProfile?>(null) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.settings_title)) },
        windowInsets = WindowInsets(0, 0, 0, 0),
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
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      SectionHeader(stringResource(R.string.settings_section_appearance))

      Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleSmall)
          }
          SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val modes = listOf(ThemeMode.System, ThemeMode.Light, ThemeMode.Dark)
            val labels =
              listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark),
              )
            modes.forEachIndexed { index, mode ->
              SegmentedButton(
                selected = state.settings.themeMode == mode,
                onClick = { onThemeMode(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                label = { Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis) },
              )
            }
          }
          SettingSwitch(
            title = stringResource(R.string.settings_dynamic_color),
            summary = stringResource(R.string.settings_dynamic_color_summary),
            checked = state.settings.dynamicColor,
            onCheckedChange = onDynamicColor,
          )
        }
      }

      SectionHeader(stringResource(R.string.settings_section_translation))

      Card(shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column {
          ListItem(
            headlineContent = { Text(stringResource(R.string.settings_default_target)) },
            supportingContent = {
              Text("${state.targetLanguage.nativeName} · ${state.targetLanguage.englishName}")
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().clickableRow { showLanguagePicker = true },
          )
          HorizontalDivider()
          SettingSwitch(
            title = stringResource(R.string.settings_auto_save),
            summary = stringResource(R.string.settings_auto_save_summary),
            checked = state.settings.autoSaveHistory,
            onCheckedChange = onAutoSave,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }
      }

      SectionHeader(stringResource(R.string.settings_section_api))

      if (state.settings.profiles.isEmpty()) {
        Card(
          shape = MaterialTheme.shapes.large,
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Icon(Icons.Outlined.Dns, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.settings_api_empty), style = MaterialTheme.typography.bodyMedium)
          }
        }
      } else {
        state.settings.profiles.forEach { profile ->
          ProfileCard(
            profile = profile,
            isActive = profile.id == state.settings.activeProfile?.id,
            onUse = { onSetActiveProfile(profile) },
            onEdit = { onEditProfile(profile) },
            onDelete = { profileToDelete = profile },
          )
        }
      }

      FilledTonalButton(onClick = onAddProfile, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.settings_api_add), modifier = Modifier.padding(start = 8.dp))
      }

      SectionHeader(stringResource(R.string.settings_section_about))

      AboutCard()
    }
  }

  if (showLanguagePicker) {
    LanguagePickerSheet(
      title = stringResource(R.string.language_picker_target_title),
      selected = state.targetLanguage,
      includeAuto = false,
      onDismiss = { showLanguagePicker = false },
      onSelect = onTargetLanguage,
    )
  }

  profileToDelete?.let { profile ->
    AlertDialog(
      onDismissRequest = { profileToDelete = null },
      title = { Text(stringResource(R.string.settings_delete_profile_confirm_title)) },
      text = { Text(stringResource(R.string.settings_delete_profile_confirm_body, profile.displayName)) },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteProfile(profile)
            profileToDelete = null
          }
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { profileToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }
}

@Composable
private fun ProfileCard(
  profile: ApiProfile,
  isActive: Boolean,
  onUse: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
      ),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp)) {
      ListItem(
        headlineContent = {
          Text(profile.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
          Column {
            Text(
              text = stringResource(R.string.settings_profile_subtitle, profile.model, profile.baseUrl),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            if (isActive) {
              Text(
                text = stringResource(R.string.settings_api_active),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
              )
            }
          }
        },
        leadingContent = { RadioButton(selected = isActive, onClick = onUse) },
        trailingContent = {
          Row {
            IconButton(onClick = onEdit) {
              Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.settings_api_edit))
            }
            IconButton(onClick = onDelete) {
              Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.settings_api_delete))
            }
          }
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
      )
    }
  }
}

@Composable
private fun AboutCard() {
  val context = LocalContext.current
  val version =
    remember {
      runCatching {
          context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
        .getOrNull() ?: "1.0"
    }

  Card(
    shape = MaterialTheme.shapes.large,
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
          Icons.Outlined.Settings,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onPrimaryContainer,
          modifier = Modifier.padding(8.dp).size(20.dp),
        )
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.settings_version, version),
          style = MaterialTheme.typography.titleSmall,
        )
        Text(
          text = stringResource(R.string.settings_about_summary),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun SettingSwitch(
  title: String,
  summary: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
