package com.lingua.app.ui.history

import android.content.ClipData
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lingua.app.R
import com.lingua.app.data.history.TranslationRecord
import com.lingua.app.ui.common.EmptyState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch

private const val CLIP_LABEL = "Lingua"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
  state: HistoryUiState,
  onQueryChange: (String) -> Unit,
  onFavoritesOnlyChange: (Boolean) -> Unit,
  onLoadMore: () -> Unit,
  onToggleFavorite: (TranslationRecord) -> Unit,
  onDelete: (TranslationRecord) -> Unit,
  onUndoDelete: () -> Unit,
  onUndoDismissed: () -> Unit,
  onClearAll: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  var detailRecord by remember { mutableStateOf<TranslationRecord?>(null) }
  var menuExpanded by remember { mutableStateOf(false) }
  var confirmClear by remember { mutableStateOf(false) }

  val deletedMessage = stringResource(R.string.history_deleted)
  val undoLabel = stringResource(R.string.history_undo)

  LaunchedEffect(state.lastDeleted?.id) {
    if (state.lastDeleted != null) {
      val result = snackbarHostState.showSnackbar(message = deletedMessage, actionLabel = undoLabel)
      if (result == SnackbarResult.ActionPerformed) onUndoDelete() else onUndoDismissed()
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
        title = { Text(stringResource(R.string.history_title)) },
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        actions = {
          IconButton(onClick = { menuExpanded = true }, enabled = state.records.isNotEmpty()) {
            Icon(Icons.Outlined.MoreVert, contentDescription = null)
          }
          DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
              text = { Text(stringResource(R.string.history_clear)) },
              onClick = {
                menuExpanded = false
                confirmClear = true
              },
              leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            )
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.history_search_hint)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
      )

      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        FilterChip(
          selected = !state.favoritesOnly,
          onClick = { onFavoritesOnlyChange(false) },
          label = { Text(stringResource(R.string.history_filter_all)) },
        )
        FilterChip(
          selected = state.favoritesOnly,
          onClick = { onFavoritesOnlyChange(true) },
          label = { Text(stringResource(R.string.history_filter_favorites)) },
          leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(16.dp)) },
        )
      }

      if (state.records.isEmpty()) {
        val isSearching = state.query.isNotBlank() || state.favoritesOnly
        EmptyState(
          icon = Icons.Outlined.History,
          title =
            stringResource(
              if (isSearching) R.string.history_empty_search_title else R.string.history_empty_title
            ),
          body =
            stringResource(if (isSearching) R.string.history_empty_search_body else R.string.history_empty_body),
        )
      } else {
        HistoryList(
          records = state.records,
          canLoadMore = state.canLoadMore,
          onOpen = { detailRecord = it },
          onDelete = onDelete,
          onToggleFavorite = onToggleFavorite,
          onLoadMore = onLoadMore,
        )
      }
    }
  }

  detailRecord?.let { record ->
    HistoryDetailSheet(
      record = record,
      onDismiss = { detailRecord = null },
      onCopy = { text ->
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, text))) }
      },
      onShare = { share(record.translatedText) },
      onToggleFavorite = { onToggleFavorite(record) },
      onDelete = {
        onDelete(record)
        detailRecord = null
      },
    )
  }

  if (confirmClear) {
    AlertDialog(
      onDismissRequest = { confirmClear = false },
      title = { Text(stringResource(R.string.history_clear_confirm_title)) },
      text = { Text(stringResource(R.string.history_clear_confirm_body)) },
      confirmButton = {
        TextButton(
          onClick = {
            confirmClear = false
            onClearAll()
          }
        ) {
          Text(stringResource(R.string.action_delete))
        }
      },
      dismissButton = {
        TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) }
      },
    )
  }
}


@Composable
private fun HistoryList(
  records: List<TranslationRecord>,
  canLoadMore: Boolean,
  onOpen: (TranslationRecord) -> Unit,
  onDelete: (TranslationRecord) -> Unit,
  onToggleFavorite: (TranslationRecord) -> Unit,
  onLoadMore: () -> Unit,
) {
  val today = remember { LocalDate.now() }
  val grouped = remember(records, today) { groupByDay(records, today) }

  LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    grouped.forEach { (labelRes, dayRecords) ->
      item(key = "header-$labelRes") {
        Text(
          text = stringResource(labelRes),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
      }
      items(items = dayRecords, key = { it.id }) { record ->
        SwipeableHistoryItem(
          record = record,
          onOpen = { onOpen(record) },
          onDelete = { onDelete(record) },
          onToggleFavorite = { onToggleFavorite(record) },
        )
      }
    }

    if (canLoadMore) {
      item(key = "load-more") {
        TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
          Text(stringResource(R.string.history_load_more))
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableHistoryItem(
  record: TranslationRecord,
  onOpen: () -> Unit,
  onDelete: () -> Unit,
  onToggleFavorite: () -> Unit,
) {
  val dismissState = rememberSwipeToDismissBoxState()

  LaunchedEffect(dismissState.currentValue) {
    if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) onDelete()
  }

  SwipeToDismissBox(
    state = dismissState,
    enableDismissFromStartToEnd = false,
    backgroundContent = {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.large)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Icon(
          Icons.Outlined.Delete,
          contentDescription = stringResource(R.string.history_delete),
          tint = MaterialTheme.colorScheme.onErrorContainer,
        )
      }
    },
  ) {
    HistoryItem(record = record, onOpen = onOpen, onToggleFavorite = onToggleFavorite)
  }
}

@Composable
private fun HistoryItem(
  record: TranslationRecord,
  onOpen: () -> Unit,
  onToggleFavorite: () -> Unit,
) {
  Card(
    shape = MaterialTheme.shapes.large,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, top = 12.dp, bottom = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = record.sourceText,
          style = MaterialTheme.typography.bodyMedium,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
          text = record.translatedText,
          style = MaterialTheme.typography.bodyLarge,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "${languageLabel(record.sourceLangName)} → ${record.targetLangName} · ${formatTime(record.createdAt)}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      IconButton(onClick = onToggleFavorite) {
        Icon(
          imageVector = if (record.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
          contentDescription =
            stringResource(if (record.isFavorite) R.string.translate_unfavorite else R.string.translate_favorite),
          tint = if (record.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetailSheet(
  record: TranslationRecord,
  onDismiss: () -> Unit,
  onCopy: (String) -> Unit,
  onShare: (String) -> Unit,
  onToggleFavorite: () -> Unit,
  onDelete: () -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(text = stringResource(R.string.history_detail_title), style = MaterialTheme.typography.titleMedium)
      Text(
        text = "${languageLabel(record.sourceLangName)} → ${record.targetLangName} · ${record.providerName} · ${record.model}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )

      CopyableBlock(
        label = stringResource(R.string.history_source_label),
        text = record.sourceText,
        copyLabel = stringResource(R.string.history_copy_source),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        onCopy = onCopy,
      )

      CopyableBlock(
        label = stringResource(R.string.history_target_label),
        text = record.translatedText,
        copyLabel = stringResource(R.string.history_copy_translation),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onCopy = onCopy,
      )

      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconButton(onClick = { onShare(record.translatedText) }) {
          Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.translate_share))
        }
        IconButton(onClick = onToggleFavorite) {
          Icon(
            imageVector = if (record.isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
            contentDescription = stringResource(R.string.translate_favorite),
          )
        }
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = onDelete) {
          Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
          Text(stringResource(R.string.action_delete), modifier = Modifier.padding(start = 6.dp))
        }
      }
    }
  }
}

@Composable
private fun CopyableBlock(
  label: String,
  text: String,
  copyLabel: String,
  containerColor: androidx.compose.ui.graphics.Color,
  onCopy: (String) -> Unit,
) {
  Surface(color = containerColor, shape = MaterialTheme.shapes.large) {
    Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
      SelectionContainer { Text(text = text, style = MaterialTheme.typography.bodyLarge) }
      TextButton(onClick = { onCopy(text) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
        Text(copyLabel, modifier = Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
      }
    }
  }
}

private fun groupByDay(
  records: List<TranslationRecord>,
  today: LocalDate,
): List<Pair<Int, List<TranslationRecord>>> {
  val zone = ZoneId.systemDefault()
  val yesterday = today.minusDays(1)
  val buckets = linkedMapOf<Int, MutableList<TranslationRecord>>()

  records.forEach { record ->
    val day = Instant.ofEpochMilli(record.createdAt).atZone(zone).toLocalDate()
    val label =
      when {
        day == today -> R.string.history_group_today
        day == yesterday -> R.string.history_group_yesterday
        else -> R.string.history_group_earlier
      }
    buckets.getOrPut(label) { mutableListOf() }.add(record)
  }
  return buckets.map { it.key to it.value }
}

private fun languageLabel(name: String): String = name.ifBlank { "?" }

private fun formatTime(epochMillis: Long): String =
  DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    .withLocale(Locale.getDefault())
    .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
