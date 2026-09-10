package com.lingua.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lingua.app.R
import com.lingua.app.domain.Language
import com.lingua.app.domain.LanguageCatalog

/**
 * Searchable language list. Used both for the source selector (`includeAuto = true`) and the target
 * selector (`includeAuto = false`).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LanguagePickerSheet(
  title: String,
  selected: Language?,
  includeAuto: Boolean,
  onDismiss: () -> Unit,
  onSelect: (Language) -> Unit,
) {
  var query by rememberSaveable { mutableStateOf("") }
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val results = remember(query) {
    val matches = LanguageCatalog.search(query)
    if (includeAuto && query.isBlank()) listOf(Language.Auto) + matches else matches
  }

  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
      )
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        placeholder = { Text(stringResource(R.string.language_picker_search)) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
      )
      HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

      if (results.isEmpty()) {
        Text(
          text = stringResource(R.string.language_picker_empty),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(32.dp),
        )
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
          items(items = results, key = { it.code }) { language ->
            val isSelected = language.code == selected?.code
            Row(
              modifier =
                Modifier.fillMaxWidth()
                  .clickable {
                    onSelect(language)
                    onDismiss()
                  }
                  .padding(horizontal = 24.dp, vertical = 14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (language.code == Language.Auto.code) stringResource(R.string.language_auto) else language.nativeName,
                  style = MaterialTheme.typography.bodyLarge,
                )
                if (language.code != Language.Auto.code) {
                  Text(
                    text = language.englishName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
              if (isSelected) {
                Icon(
                  imageVector = Icons.Outlined.Check,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                )
              }
            }
          }
        }
      }
    }
  }
}
