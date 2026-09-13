package com.lingua.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.lingua.app.ui.nav.ScreenOcrKey
import com.lingua.app.ui.screenocr.ScreenOcrScreen
import com.lingua.app.ui.screenocr.ScreenOcrViewModel
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
fun LinguaApp(
  container: AppContainer,
  sharedImageUri: String? = null,
  onSharedImageHandled: () -> Unit = {},
) {
  val backStack = rememberNavBackStack(HomeKey)

  // A screenshot shared into the app opens screen text recognition directly.
  LaunchedEffect(sharedImageUri) {
    if (sharedImageUri != null) {
      backStack.add(ScreenOcrKey(sharedImageUri))
      onSharedImageHandled()
    }
  }

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
            onOpenScreenOcr = { backStack.add(ScreenOcrKey()) },
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
        entry<ScreenOcrKey> { key ->
          val screenOcrViewModel: ScreenOcrViewModel =
            viewModel(
              key = "screen-ocr-${key.imageUri ?: "new"}",
              factory = ScreenOcrViewModel.factory(container, key.imageUri),
            )
          val state by screenOcrViewModel.state.collectAsStateWithLifecycle()

          // The ViewModel outlives this entry, so re-entering starts from a clean result.
          LaunchedEffect(Unit) { screenOcrViewModel.onEnter() }

          val captureLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
              screenOcrViewModel.onCaptureResult(result.resultCode, result.data)
            }
          val imagePicker =
            rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
              uri?.let(screenOcrViewModel::importImage)
            }

          ScreenOcrScreen(
            state = state,
            onBack = { backStack.removeLastOrNull() },
            onCaptureRequest = { captureLauncher.launch(screenOcrViewModel.consentIntent()) },
            onPickImage = {
              imagePicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
              )
            },
            onRetake = screenOcrViewModel::retake,
            onToggleParagraph = screenOcrViewModel::toggleParagraph,
            onSelectAll = screenOcrViewModel::selectAll,
            onClearSelection = screenOcrViewModel::clearSelection,
            onEditSource = screenOcrViewModel::editSource,
            onTargetLanguage = screenOcrViewModel::setTargetLanguage,
            onSourceOverride = screenOcrViewModel::setSourceOverride,
            onTranslate = screenOcrViewModel::translate,
            onRetry = screenOcrViewModel::retry,
            onToggleFavorite = screenOcrViewModel::toggleFavorite,
            onDismissError = screenOcrViewModel::dismissError,
            // Capturing from inside the app could only ever grab Lingua's own window; the resident
            // notification is the way to read another app.
            allowCapture = false,
          )
        }
      },
  )
}

@Composable
private fun HomeShell(
  container: AppContainer,
  onOpenApiEditor: (String?) -> Unit,
  onOpenScreenOcr: () -> Unit,
) {
  var selectedTabName by rememberSaveable { mutableStateOf(HomeTab.Translate.name) }
  val selectedTab = HomeTab.entries.firstOrNull { it.name == selectedTabName } ?: HomeTab.Translate

  val translateViewModel: TranslateViewModel = viewModel(factory = TranslateViewModel.factory(container))
  val historyViewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.factory(container))
  val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))

  val context = LocalContext.current
  var notificationsAllowed by remember { mutableStateOf(canPostNotifications(context)) }
  var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
  /** Set when the user asked for the shortcut, so each missing grant can be requested in turn. */
  var enableRequested by remember { mutableStateOf(false) }

  val notificationPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notificationsAllowed = granted
      if (!granted) enableRequested = false
    }
  val overlayPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
      overlayAllowed = Settings.canDrawOverlays(context)
    }

  // Walks the two grants the shortcut needs — notifications for the resident entry, overlay for the
  // ball — and switches it on once both are in place.
  LaunchedEffect(enableRequested, notificationsAllowed, overlayAllowed) {
    if (!enableRequested) return@LaunchedEffect
    when {
      !notificationsAllowed ->
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
      !overlayAllowed -> overlayPermission.launch(overlayGrantIntent(context))
      else -> {
        enableRequested = false
        translateViewModel.setScreenTranslateEnabled(true)
      }
    }
  }

  // Dismissing the notification from the shade must not leave the switch claiming it is on, and the
  // overlay grant may have been given (or withdrawn) in system settings while we were away.
  LifecycleResumeEffect(Unit) {
    overlayAllowed = Settings.canDrawOverlays(context)
    notificationsAllowed = canPostNotifications(context)
    translateViewModel.syncScreenTranslateState()
    onPauseOrDispose {}
  }

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
                onOpenScreenOcr = onOpenScreenOcr,
                onScreenTranslateToggle = { enabled ->
                  if (enabled) {
                    enableRequested = true
                  } else {
                    enableRequested = false
                    translateViewModel.setScreenTranslateEnabled(false)
                  }
                },
                notificationsBlocked = !notificationsAllowed,
                overlayBlocked = !overlayAllowed,
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
                onCustomPrompt = settingsViewModel::setCustomPrompt,
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

private fun canPostNotifications(context: Context): Boolean =
  Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED

/** The system page where "display over other apps" is granted; there is no runtime dialog. */
private fun overlayGrantIntent(context: Context): Intent =
  Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
