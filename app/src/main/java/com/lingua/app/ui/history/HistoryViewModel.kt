package com.lingua.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lingua.app.AppContainer
import com.lingua.app.data.history.TranslationRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val HISTORY_PAGE_SIZE = 50

data class HistoryUiState(
  val records: List<TranslationRecord> = emptyList(),
  val query: String = "",
  val favoritesOnly: Boolean = false,
  val canLoadMore: Boolean = false,
  val lastDeleted: TranslationRecord? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val container: AppContainer) : ViewModel() {

  private val query = MutableStateFlow("")
  private val favoritesOnly = MutableStateFlow(false)
  private val limit = MutableStateFlow(HISTORY_PAGE_SIZE)
  private val lastDeleted = MutableStateFlow<TranslationRecord?>(null)

  private val request =
    combine(query, favoritesOnly, limit) { query, favoritesOnly, limit -> Triple(query, favoritesOnly, limit) }

  val state: StateFlow<HistoryUiState> =
    combine(
        request.flatMapLatest { (query, favoritesOnly, limit) ->
          container.historyRepository.observe(query, favoritesOnly, limit)
        },
        lastDeleted,
      ) { records, deleted ->
        HistoryUiState(
          records = records,
          query = query.value,
          favoritesOnly = favoritesOnly.value,
          canLoadMore = records.size >= limit.value,
          lastDeleted = deleted,
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

  fun setQuery(value: String) {
    query.value = value
  }

  fun setFavoritesOnly(value: Boolean) {
    favoritesOnly.value = value
  }

  fun loadMore() {
    limit.value += HISTORY_PAGE_SIZE
  }

  fun toggleFavorite(record: TranslationRecord) {
    viewModelScope.launch { container.historyRepository.setFavorite(record.id, !record.isFavorite) }
  }

  fun delete(record: TranslationRecord) {
    viewModelScope.launch {
      container.historyRepository.delete(record.id)
      lastDeleted.value = record
    }
  }

  fun undoDelete() {
    val record = lastDeleted.value ?: return
    viewModelScope.launch {
      container.historyRepository.insert(record.copy(id = 0L))
      lastDeleted.value = null
    }
  }

  fun dismissUndo() {
    lastDeleted.value = null
  }

  fun clearAll() {
    viewModelScope.launch {
      container.historyRepository.deleteAll()
      lastDeleted.value = null
    }
  }

  companion object {
    fun factory(container: AppContainer) = viewModelFactory {
      initializer { HistoryViewModel(container) }
    }
  }
}
