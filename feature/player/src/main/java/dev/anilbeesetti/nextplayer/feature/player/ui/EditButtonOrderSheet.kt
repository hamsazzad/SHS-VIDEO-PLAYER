package dev.anilbeesetti.nextplayer.feature.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButtonId
import dev.anilbeesetti.nextplayer.feature.player.buttons.PlayerButtonLayout
import kotlinx.coroutines.launch

// ─── Edit Button Order — bottom sheet ────────────────────────────────────────
//
// Presents a draggable list of TOP-bar player buttons.
// Each row has:
//   ≡  (drag handle, long-press to drag)   |  Button label  |  👁 / 🚫 (visibility toggle)
//
// On "Save", the new order and hidden-set are persisted via PlayerButtonLayout.
// On "Reset", factory defaults are restored.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditButtonOrderSheet(
    onDismiss: () -> Unit,
    onLayoutSaved: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Working copies of layout state ────────────────────────────────────────
    var buttonOrder by remember {
        mutableStateOf(PlayerButtonLayout.getTopBarOrder(context))
    }
    var hiddenButtons by remember {
        mutableStateOf(PlayerButtonLayout.getHiddenButtons(context))
    }

    // ── Drag state ────────────────────────────────────────────────────────────
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY  by remember { mutableStateOf(0f) }
    val listState    = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Customise Player Buttons",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    onClick = {
                        PlayerButtonLayout.reset(context)
                        buttonOrder    = PlayerButtonLayout.getTopBarOrder(context)
                        hiddenButtons  = emptySet()
                        onLayoutSaved()
                    },
                ) { Text("Reset") }
            }

            Text(
                "Long-press ≡ to drag and reorder. Tap 👁 to hide/show a button.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )

            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // ── Draggable list ─────────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(buttonOrder, key = { it.name }) { buttonId ->
                    val idx = buttonOrder.indexOf(buttonId)
                    val isHidden = buttonId in hiddenButtons
                    val isDragging = draggedIndex == idx

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isDragging) Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    RoundedCornerShape(12.dp),
                                ) else Modifier,
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDragging)
                                MaterialTheme.colorScheme.primaryContainer
                            else if (isHidden)
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(
                            defaultElevation = if (isDragging) 8.dp else 1.dp,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Drag handle — long-press activates drag
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .pointerInput(idx) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { draggedIndex = idx; dragOffsetY = 0f },
                                            onDragEnd = { draggedIndex = null; dragOffsetY = 0f },
                                            onDragCancel = { draggedIndex = null; dragOffsetY = 0f },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragOffsetY += dragAmount.y
                                                // Translate drag offset into a target index
                                                val itemHeightPx = 64.dp.toPx()
                                                val delta = (dragOffsetY / itemHeightPx).toInt()
                                                val target = (idx + delta)
                                                    .coerceIn(0, buttonOrder.lastIndex)
                                                if (target != idx) {
                                                    buttonOrder = buttonOrder.toMutableList().apply {
                                                        val moved = removeAt(idx)
                                                        add(target, moved)
                                                    }
                                                    draggedIndex = target
                                                    dragOffsetY -= delta * itemHeightPx
                                                }
                                            },
                                        )
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    contentDescription = "Drag to reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            // Button label
                            Text(
                                text = buttonId.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (!isHidden) FontWeight.Medium else FontWeight.Normal,
                                color = if (isHidden)
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                else
                                    MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )

                            // Visibility toggle
                            IconButton(
                                onClick = {
                                    hiddenButtons = if (isHidden)
                                        hiddenButtons - buttonId
                                    else
                                        hiddenButtons + buttonId
                                },
                            ) {
                                Icon(
                                    if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = if (isHidden) "Show button" else "Hide button",
                                    tint = if (isHidden)
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    else
                                        MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // ── Save / Cancel ─────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(
                    onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                    modifier = Modifier.weight(1f),
                ) { Text("Cancel") }

                androidx.compose.material3.Button(
                    onClick = {
                        // Persist order
                        PlayerButtonLayout.saveTopBarOrder(context, buttonOrder)
                        // Persist each button's visibility
                        PlayerButtonLayout.DEFAULT_TOP.forEach { id ->
                            PlayerButtonLayout.setButtonVisible(context, id, id !in hiddenButtons)
                        }
                        onLayoutSaved()
                        scope.launch { sheetState.hide(); onDismiss() }
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("Save Layout") }
            }
        }
    }
}
