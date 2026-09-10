package com.lingua.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.lingua.app.AppContainer
import com.lingua.app.R
import com.lingua.app.ui.history.HistoryScreen
import com.lingua.app.ui.history.HistoryViewModel
import com.lingua.app.ui.nav.ApiEditorKey
import com.lingua.app.ui.nav.HomeKey
import com.lingua.app.ui.settings.ApiProfileEditorScreen
import com.lingua.app.ui.settings.ApiProfileEditorViewModel
import com.lingua.app.ui.settings.SettingsScreen
import com.lingua.app.ui.settings.SettingsViewModel
import com.lingua.app.ui.translate.TranslateScreen
import com.lingua.app.ui.translate.TranslateViewModel

private enum class HomeTab(val labelRes: Int, val icon: ImageVector) {
  Translate(R.string.tab_translate, Icons.Outlined.Translate),
  History(R.string.tab_history, Icons.Outlined.History),
  Settings(R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun LinguaApp(container: AppContainer) {
  val backStack = rememberNavBackStack(HomeKey)

  NavDisplay(
    backStack = backStack,
    modifier = Modifier.fillMaxSize(),
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<HomeKey> {
          HomeShell(
            container = container,
            onOpenApiEditor = { profileId -> backStack.add(ApiEditorKey(profileId)) },
          )
        }
        entry<ApiEditorKey> { key ->
          val editorViewModel: ApiProfileEditorViewModel =
            viewModel(
              key = "api-editor-${key.profileId ?: "new"}",
              factory = ApiProfileEditorViewModel.factory(container),
            )
          val state by editorViewModel.state.collectAsStateWithLifecycle()
          val onDone: () -> Unit = { backStack.removeLastOrNull() }

          ApiProfileEditorScreen(
            targetProfileId = key.profileId,
            state = state,
            onBind = editorViewModel::bind,
            onName = editorViewModel::onName,
            onBaseUrl = editorViewModel::onBaseUrl,
            onApiKey = editorViewModel::onApiKey,
            onModel = editorViewModel::onModel,
            onTemperature = editorViewModel::onTemperature,
            onTimeout = editorViewModel::onTimeout,
            onJsonMode = editorViewModel::onJsonMode,
            onHeaders = editorViewModel::onHeaders,
            onPreset = editorViewModel::applyPreset,
            onFetchModels = editorViewModel::fetchModels,
            onTestConnection = editorViewModel::testConnection,
            onTestTranslation = editorViewModel::testTranslation,
            onSave = { editorViewModel.save(onDone) },
            onDelete = { editorViewModel.delete(onDone) },
            onBack = onDone,
          )
        }
      },
  )
}

@Composable
private fun HomeShell(container: AppContainer, onOpenApiEditor: (String?) -> Unit) {
  var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.Translate.name) }
  val selectedTab = HomeTab.entries.firstOrNull { it.name == selectedTabName } ?: HomeTab.Translate

  val translateViewModel: TranslateViewModel = viewModel(factory = TranslateViewModel.factory(container))
  val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
  val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val wide = maxWidth >= 600.dp

    Row(modifier = Modifier.fillMaxSize()) {
      if (wide) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
          HomeTab.entries.forEach { tab ->
            val label = stringResource(tab.labelRes)
            NavigationRailItem(
              selected = tab == selectedTab,
              onClick = { selectedTabName = tab.name },
              icon = { Icon(tab.icon, contentDescription = label) },
              label = { Text(label) },
            )
          }
        }
      }

      // The outer Scaffold owns window insets; each screen's TopAppBar opts out of them so the
      // status bar is never padded twice.
      Scaffold(
        bottomBar = {
          if (!wide) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
              HomeTab.entries.forEach { tab ->
                val label = stringResource(tab.labelRes)
                NavigationBarItem(
                  selected = tab == selectedTab,
                  onClick = { selectedTabName = tab.name },
                  icon = { Icon(tab.icon, contentDescription = label) },
                  label = { Text(label) },
                )
              }
            }
          }
        },
      ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
          when (selectedTab) {
            HomeTab.Translate -> {
              val state by translateViewModel.state.collectAsStateWithLifecycle()
              TranslateScreen(
                state = state,
                onSourceChange = translateViewModel::onSourceTextChange,
                onClear = translateViewModel::clearSourceText,
                onTranslate = translateViewModel::translate,
                onTargetLanguage = translateViewModel::setTargetLanguage,
                onSourceOverride = translateViewModel::setSourceOverride,
                onSwap = translateViewModel::swapLanguages,
                onFavorite = translateViewModel::toggleFavorite,
                onSave = translateViewModel::saveToHistory,
                onExample = translateViewModel::setExample,
                onOpenSettings = { selectedTabName = HomeTab.Settings.name },
              )
            }
            HomeTab.History -> {
              val state by historyViewModel.state.collectAsStateWithLifecycle()
              HistoryScreen(
                state = state,
                onQueryChange = historyViewModel::setQuery,
                onFavoritesOnlyChange = historyViewModel::setFavoritesOnly,
                onLoadMore = historyViewModel::loadMore,
                onToggleFavorite = historyViewModel::toggleFavorite,
                onDelete = historyViewModel::delete,
                onUndoDelete = historyViewModel::undoDelete,
                onUndoDismissed = historyViewModel::dismissUndo,
                onClearAll = historyViewModel::clearAll,
              )
            }
            HomeTab.Settings -> {
              val state by settingsViewModel.state.collectAsStateWithLifecycle()
              SettingsScreen(
                state = state,
                onThemeMode = settingsViewModel::setThemeMode,
                onDynamicColor = settingsViewModel::setDynamicColor,
                onAutoSave = settingsViewModel::setAutoSaveHistory,
                onTargetLanguage = settingsViewModel::setTargetLanguage,
                onSetActiveProfile = settingsViewModel::setActiveProfile,
                onDeleteProfile = settingsViewModel::deleteProfile,
                onEditProfile = { profile -> onOpenApiEditor(profile.id) },
                onAddProfile = { onOpenApiEditor(null) },
              )
            }
          }
        }
      }
    }
  }
}
