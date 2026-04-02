package dev.anilbeesetti.nextplayer.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.ui.theme.ThemeManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current

    // ── State ─────────────────────────────────────────────────────────────────
    var selectedHex by rememberSaveable {
        mutableStateOf(ThemeManager.getAccentHex(context) ?: "")
    }
    var customHexInput by rememberSaveable { mutableStateOf(selectedHex) }
    var hexError by remember { mutableStateOf<String?>(null) }
    var bgImageUri by remember { mutableStateOf(ThemeManager.getBackgroundImageUri(context)) }
    var dimAlpha by remember { mutableStateOf(ThemeManager.getBgDimAlpha(context)) }

    // ── Image picker ──────────────────────────────────────────────────────────
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            // Persist a persistent read permission for this URI so it remains
            // accessible after the app is restarted
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            bgImageUri = uri
            ThemeManager.setBackgroundImageUri(context, uri)
        }
    }

    // ── UI ────────────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Theme Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(NextIcons.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Predefined color themes ───────────────────────────────────────
            SectionHeader(icon = Icons.Rounded.Palette, title = "Accent Color")
            Text(
                "Choose a predefined color or enter a custom hex value below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ThemeManager.predefinedThemes) { theme ->
                    val color = ThemeManager.parseHex(theme.hexColor)
                        ?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                    val isSelected = selectedHex.equals(theme.hexColor, ignoreCase = true)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            selectedHex = theme.hexColor
                            customHexInput = theme.hexColor
                            hexError = null
                            ThemeManager.setAccentHex(context, theme.hexColor)
                        },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (isSelected) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier,
                                ),
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = theme.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }

            HorizontalDivider()

            // ── Custom hex input ──────────────────────────────────────────────
            Text(
                "Custom Hex Color",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = customHexInput,
                    onValueChange = {
                        customHexInput = it
                        hexError = null
                    },
                    label = { Text("e.g. #FF6200EE") },
                    singleLine = true,
                    isError = hexError != null,
                    supportingText = hexError?.let { { Text(it) } },
                    modifier = Modifier.weight(1f),
                    prefix = {
                        val previewColor = ThemeManager.parseHex(customHexInput)
                            ?.let { Color(it) }
                        if (previewColor != null) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(previewColor),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    },
                )
                Button(
                    onClick = {
                        val hex = customHexInput.trim()
                        if (ThemeManager.parseHex(hex) == null) {
                            hexError = "Enter a valid 6-digit hex, e.g. #1976D2"
                        } else {
                            selectedHex = hex
                            hexError = null
                            ThemeManager.setAccentHex(context, hex)
                        }
                    },
                ) { Text("Apply") }
            }
            if (selectedHex.isNotBlank()) {
                TextButton(
                    onClick = {
                        selectedHex = ""
                        customHexInput = ""
                        ThemeManager.setAccentHex(context, null)
                    },
                ) { Text("Reset to default") }
            }

            HorizontalDivider()

            // ── Background image ──────────────────────────────────────────────
            SectionHeader(icon = Icons.Rounded.Image, title = "Background Image")
            Text(
                "Set a custom image as the global app background.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (bgImageUri != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Box {
                        AsyncImage(
                            model = bgImageUri,
                            contentDescription = "Background preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .background(Color.Black.copy(alpha = dimAlpha)),
                        )
                        IconButton(
                            onClick = {
                                bgImageUri = null
                                ThemeManager.setBackgroundImageUri(context, null)
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(
                                Icons.Rounded.Clear,
                                contentDescription = "Remove background",
                                tint = Color.White,
                            )
                        }
                    }
                }

                // ── Dim slider ────────────────────────────────────────────────
                Text(
                    "Background Dim: ${(dimAlpha * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                )
                Slider(
                    value = dimAlpha,
                    onValueChange = {
                        dimAlpha = it
                        ThemeManager.setBgDimAlpha(context, it)
                    },
                    valueRange = 0f..0.9f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (bgImageUri == null) "Pick Image" else "Change Image")
                }
                if (bgImageUri != null) {
                    OutlinedButton(
                        onClick = {
                            bgImageUri = null
                            ThemeManager.setBackgroundImageUri(context, null)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Remove")
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// ─── Shared section header ────────────────────────────────────────────────────

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
