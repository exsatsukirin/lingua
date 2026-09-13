package com.lingua.app.ui.screenocr

import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lingua.app.R
import com.lingua.app.data.ocr.OcrParagraph
import com.lingua.app.domain.Language
import com.lingua.app.ui.common.ErrorCard
import com.lingua.app.ui.common.EmptyState
import com.lingua.app.ui.common.LanguagePickerSheet
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

private const val CLIP_LABEL = "Lingua"
private const val MAX_ZOOM = 8f

/**
 * Screen text recognition: an image with tappable text blocks on top, plus the translation of
 * whatever is selected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenOcrScreen(
  state: ScreenOcrUiState,
  onBack: () -> Unit,
  onCaptureRequest: () -> Unit,
  onPickImage: () -> Unit,
  onRetake: () -> Unit,
  onToggleParagraph: (Int) -> Unit,
  onSelectAll: () -> Unit,
  onClearSelection: () -> Unit,
  onEditSource: (String) -> Unit,
  onTargetLanguage: (Language) -> Unit,
  onSourceOverride: (Language?) -> Unit,
  onTranslate: () -> Unit,
  onRetry: () -> Unit,
  onToggleFavorite: () -> Unit,
  onDismissError: () -> Unit,
) {
  val context = LocalContext.current
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()

  var showTargetPicker by remember { mutableStateOf(false) }
  var showSourcePicker by remember { mutableStateOf(false) }
  var showEditDialog by remember { mutableStateOf(false) }
  // Closing the result sheet must not throw the captured image away.
  var dismissedResult by remember { mutableStateOf<ScreenOcrResult?>(null) }

  Scaffold(
    topBar = {
      // This is a standalone navigation destination, not a tab inside the home shell's Scaffold,
      // so the default window insets are the ones that keep the bar clear of the status bar.
      TopAppBar(
        title = { Text(stringResource(R.string.screen_ocr_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.cd_back))
          }
        },
        actions = {
          if (state.image != null) {
            IconButton(onClick = onRetake) {
              Icon(
                Icons.Outlined.Refresh,
                contentDescription = stringResource(R.string.screen_ocr_retake),
              )
            }
          }
          IconButton(onClick = onPickImage) {
            Icon(
              Icons.Outlined.Image,
              contentDescription = stringResource(R.string.screen_ocr_pick_image),
            )
          }
        },
      )
    },
    bottomBar = {
      if (state.phase == ScreenOcrPhase.Ready) {
        SelectionBar(
          state = state,
          onSelectAll = onSelectAll,
          onClearSelection = onClearSelection,
          onEditSource = { showEditDialog = true },
          onTranslate = onTranslate,
        )
      }
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      TargetLanguageRow(
        state = state,
        onPickTarget = { showTargetPicker = true },
        onPickSource = { showSourcePicker = true },
      )

      state.error?.let { error ->
        ErrorCard(
          message = error,
          detail = state.errorDetail,
          onRetry = if (state.image != null) onRetry else null,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
      }

      state.unsupportedSource?.let { language ->
        Surface(
          color = MaterialTheme.colorScheme.tertiaryContainer,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
          Text(
            text = stringResource(R.string.screen_ocr_script_unsupported, language.nativeName),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(12.dp),
          )
        }
      }

      when {
        state.phase == ScreenOcrPhase.Capturing ->
          ProgressBody(stringResource(R.string.screen_ocr_capture))
        state.phase == ScreenOcrPhase.Recognizing ->
          ProgressBody(stringResource(R.string.screen_ocr_recognizing))
        state.image != null ->
          OcrImageCanvas(
            bitmap = state.image,
            paragraphs = state.paragraphs,
            selected = state.selected,
            onToggleParagraph = onToggleParagraph,
            modifier = Modifier.fillMaxSize(),
          )
        state.phase == ScreenOcrPhase.Idle ->
          IdleBody(onCaptureRequest = onCaptureRequest, onPickImage = onPickImage)
      }
    }
  }

  if (showTargetPicker) {
    LanguagePickerSheet(
      title = stringResource(R.string.language_picker_target_title),
      selected = state.targetLanguage,
      includeAuto = false,
      onDismiss = { showTargetPicker = false },
      onSelect = {
        onTargetLanguage(it)
        showTargetPicker = false
      },
    )
  }

  if (showSourcePicker) {
    LanguagePickerSheet(
      title = stringResource(R.string.language_picker_source_title),
      selected = state.sourceOverride,
      includeAuto = true,
      onDismiss = { showSourcePicker = false },
      onSelect = {
        onSourceOverride(if (it.code == Language.Auto.code) null else it)
        showSourcePicker = false
      },
    )
  }

  if (showEditDialog) {
    EditSourceDialog(
      initialText = state.sourceText,
      onDismiss = { showEditDialog = false },
      onConfirm = {
        onEditSource(it)
        showEditDialog = false
      },
    )
  }

  state.result?.takeIf { it != dismissedResult }?.let { result ->
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { dismissedResult = result },
      sheetState = sheetState,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = stringResource(R.string.history_target_label),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
          )
          result.sourceLanguage?.let { detected ->
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = MaterialTheme.shapes.small,
            ) {
              Text(
                text = stringResource(R.string.translate_detected, detected.nativeName),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              )
            }
          }
        }

        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerLow,
          shape = MaterialTheme.shapes.large,
        ) {
          SelectionContainer {
            Text(
              text = result.text,
              style = MaterialTheme.typography.bodyLarge,
              modifier =
                Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).padding(14.dp),
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = {
              scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(CLIP_LABEL, result.text)))
              }
            }
          ) {
            Icon(Icons.Outlined.ContentCopy, contentDescription = stringResource(R.string.translate_copy))
          }
          IconButton(
            onClick = {
              val send =
                Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_TEXT, result.text)
                }
              context.startActivity(Intent.createChooser(send, null))
            }
          ) {
            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.translate_share))
          }
          if (result.savedRecordId != null) {
            IconButton(onClick = onToggleFavorite) {
              Icon(
                imageVector =
                  if (result.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription =
                  stringResource(
                    if (result.favorite) R.string.translate_unfavorite else R.string.translate_favorite
                  ),
                tint =
                  if (result.favorite) MaterialTheme.colorScheme.primary
                  else MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
          Box(modifier = Modifier.weight(1f))
          TextButton(onClick = { dismissedResult = result }) {
            Text(stringResource(R.string.action_close))
          }
        }
      }
    }
  }
}

@Composable
private fun TargetLanguageRow(
  state: ScreenOcrUiState,
  onPickTarget: () -> Unit,
  onPickSource: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = stringResource(R.string.translate_source_label),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onPickSource) {
      Text(state.sourceOverride?.nativeName ?: stringResource(R.string.language_auto))
    }
    Text(
      text = "→",
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    TextButton(onClick = onPickTarget) {
      Icon(
        Icons.Outlined.Language,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
      )
      Text(
        text = state.targetLanguage.nativeName,
        modifier = Modifier.padding(start = 6.dp),
      )
    }
  }
}

@Composable
private fun ProgressBody(label: String) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      CircularProgressIndicator()
      Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
  }
}

@Composable
private fun IdleBody(onCaptureRequest: () -> Unit, onPickImage: () -> Unit) {
  Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      EmptyState(
        icon = Icons.Outlined.TextFields,
        title = stringResource(R.string.screen_ocr_idle_title),
        body = stringResource(R.string.screen_ocr_idle_body),
      )
      Button(onClick = onCaptureRequest, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screen_ocr_capture))
      }
      OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.screen_ocr_pick_image))
      }
    }
  }
}

@Composable
private fun SelectionBar(
  state: ScreenOcrUiState,
  onSelectAll: () -> Unit,
  onClearSelection: () -> Unit,
  onEditSource: () -> Unit,
  onTranslate: () -> Unit,
) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 3.dp,
  ) {
    Column(modifier = Modifier.navigationBarsPadding()) {
      HorizontalDivider()
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = stringResource(R.string.screen_ocr_selected_count, state.selected.size),
          style = MaterialTheme.typography.labelLarge,
          modifier = Modifier.padding(start = 8.dp),
        )
        Box(modifier = Modifier.weight(1f))
        TextButton(onClick = if (state.selected.size == state.paragraphs.size) onClearSelection else onSelectAll) {
          Text(
            stringResource(
              if (state.selected.size == state.paragraphs.size) R.string.screen_ocr_clear_selection
              else R.string.screen_ocr_select_all
            )
          )
        }
        IconButton(onClick = onEditSource, enabled = state.sourceText.isNotBlank()) {
          Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.screen_ocr_edit_source))
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text =
            if (state.sourceText.isBlank()) stringResource(R.string.screen_ocr_hint_tap)
            else state.sourceText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).padding(end = 12.dp),
        )
        Button(onClick = onTranslate, enabled = state.canTranslate) {
          if (state.isTranslating) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
          } else {
            Text(stringResource(R.string.screen_ocr_translate))
          }
        }
      }
    }
  }
}

@Composable
private fun EditSourceDialog(
  initialText: String,
  onDismiss: () -> Unit,
  onConfirm: (String) -> Unit,
) {
  var text by remember { mutableStateOf(initialText) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.screen_ocr_edit_title)) },
    text = {
      OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 280.dp),
        minLines = 4,
      )
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) {
        Text(stringResource(R.string.action_confirm))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
    },
  )
}

/**
 * Draws the captured image with its text blocks highlighted.
 *
 * Everything is drawn by hand so the tap coordinates and the drawn rectangles share one transform;
 * a pinch zooms and a drag pans.
 */
@Composable
private fun OcrImageCanvas(
  bitmap: Bitmap,
  paragraphs: List<OcrParagraph>,
  selected: Set<Int>,
  onToggleParagraph: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val image = remember(bitmap) { bitmap.asImageBitmap() }
  val density = LocalDensity.current
  var userScale by remember(bitmap) { mutableFloatStateOf(1f) }
  var pan by remember(bitmap) { mutableStateOf(Offset.Zero) }

  BoxWithConstraints(modifier = modifier.background(Color.Black)) {
    val containerWidth = with(density) { maxWidth.toPx() }
    val containerHeight = with(density) { maxHeight.toPx() }
    val fit = min(containerWidth / bitmap.width, containerHeight / bitmap.height)
    val drawScale = fit * userScale
    val drawWidth = bitmap.width * drawScale
    val drawHeight = bitmap.height * drawScale
    val originX = (containerWidth - drawWidth) / 2f + pan.x
    val originY = (containerHeight - drawHeight) / 2f + pan.y

    // The gesture handlers outlive a single composition, so they read the latest transform.
    val geometry by rememberUpdatedState(Geometry(originX, originY, drawScale))
    val blocks by rememberUpdatedState(paragraphs)
    val toggle by rememberUpdatedState(onToggleParagraph)

    val outline = MaterialTheme.colorScheme.primary

    Canvas(
      modifier =
        Modifier.fillMaxSize()
          .pointerInput(bitmap) {
            detectTapGestures { position ->
              val current = geometry
              if (current.scale <= 0f) return@detectTapGestures
              val imageX = (position.x - current.originX) / current.scale
              val imageY = (position.y - current.originY) / current.scale
              val hit =
                blocks
                  .indices
                  .filter { index ->
                    val block = blocks[index]
                    imageX >= block.left &&
                      imageX <= block.right &&
                      imageY >= block.top &&
                      imageY <= block.bottom
                  }
                  // Overlapping boxes: prefer the tightest one.
                  .minByOrNull { index ->
                    val block = blocks[index]
                    (block.right - block.left) * (block.bottom - block.top)
                  }
              hit?.let(toggle)
            }
          }
          .pointerInput(bitmap) {
            detectTransformGestures { _, panChange, zoomChange, _ ->
              userScale = (userScale * zoomChange).coerceIn(1f, MAX_ZOOM)
              pan = pan + panChange
            }
          }
    ) {
      drawImage(
        image = image,
        dstOffset = IntOffset(originX.roundToInt(), originY.roundToInt()),
        dstSize = IntSize(drawWidth.roundToInt(), drawHeight.roundToInt()),
        filterQuality = FilterQuality.Medium,
      )

      paragraphs.forEachIndexed { index, block ->
        val isSelected = index in selected
        val left = originX + block.left * drawScale
        val top = originY + block.top * drawScale
        val right = originX + block.right * drawScale
        val bottom = originY + block.bottom * drawScale
        val path =
          Path().apply {
            moveTo(left, top)
            lineTo(right, top)
            lineTo(right, bottom)
            lineTo(left, bottom)
            close()
          }
        drawPath(
          path = path,
          color = outline.copy(alpha = if (isSelected) 0.30f else 0.08f),
        )
        drawPath(
          path = path,
          color = outline.copy(alpha = if (isSelected) 1f else 0.45f),
          style = Stroke(width = (if (isSelected) 2.5f else 1.5f) * density.density),
        )
      }
    }
  }
}

private data class Geometry(val originX: Float, val originY: Float, val scale: Float)
