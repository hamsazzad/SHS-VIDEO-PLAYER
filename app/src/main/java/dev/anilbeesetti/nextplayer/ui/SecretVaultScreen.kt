package dev.anilbeesetti.nextplayer.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.anilbeesetti.nextplayer.core.ui.designsystem.NextIcons
import dev.anilbeesetti.nextplayer.feature.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

// ─── Constants ────────────────────────────────────────────────────────────────
private const val VAULT_DIR_NAME   = "hidden_vault"
private const val PREFS_SV         = "secret_vault_prefs"
private const val KEY_SV_PIN_HASH  = "sv_pin_hash"
private const val KEY_SV_BIO       = "sv_biometric_enabled"
private const val KEY_SV_META      = "sv_file_meta"

// ─── Data ─────────────────────────────────────────────────────────────────────
data class VaultEntry(
    val id: String,
    val displayName: String,
    val originalUri: String,
    val sizeByes: Long,
    val vaultPath: String,
    val addedAt: Long,
)

// ─── Vault logic helpers ──────────────────────────────────────────────────────
private fun svHash(input: String): String {
    val b = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return b.joinToString("") { "%02x".format(it) }
}

private fun svPrefs(ctx: Context) = ctx.getSharedPreferences(PREFS_SV, Context.MODE_PRIVATE)

fun svIsSetUp(ctx: Context) = svPrefs(ctx).contains(KEY_SV_PIN_HASH)

fun svVerifyPin(ctx: Context, pin: String) =
    svPrefs(ctx).getString(KEY_SV_PIN_HASH, "") == svHash(pin)

fun svSetupPin(ctx: Context, pin: String) {
    svPrefs(ctx).edit().putString(KEY_SV_PIN_HASH, svHash(pin)).apply()
    svVaultDir(ctx).mkdirs()
}

fun svBiometricEnabled(ctx: Context) =
    svPrefs(ctx).getBoolean(KEY_SV_BIO, false)

fun svSetBiometricEnabled(ctx: Context, enabled: Boolean) =
    svPrefs(ctx).edit().putBoolean(KEY_SV_BIO, enabled).apply()

fun svVaultDir(ctx: Context) = File(ctx.filesDir, VAULT_DIR_NAME)

fun svLoadEntries(ctx: Context): List<VaultEntry> {
    val raw = svPrefs(ctx).getStringSet(KEY_SV_META, emptySet()) ?: return emptyList()
    return raw.mapNotNull { s ->
        runCatching {
            val p = s.split("|~|")
            VaultEntry(p[0], p[1], p[2], p[3].toLong(), p[4], p[5].toLong())
        }.getOrNull()
    }.sortedByDescending { it.addedAt }
}

private fun svSaveEntries(ctx: Context, entries: List<VaultEntry>) {
    val set = entries.map { e ->
        "${e.id}|~|${e.displayName}|~|${e.originalUri}|~|${e.sizeByes}|~|${e.vaultPath}|~|${e.addedAt}"
    }.toSet()
    svPrefs(ctx).edit().putStringSet(KEY_SV_META, set).apply()
}

/**
 * Moves a file from public storage (via URI) into the hidden vault.
 * Uses ContentResolver → InputStream, then deletes original via ContentResolver.
 * No MANAGE_EXTERNAL_STORAGE required.
 */
suspend fun svHideFile(ctx: Context, uri: Uri): VaultEntry? = withContext(Dispatchers.IO) {
    runCatching {
        val cr = ctx.contentResolver
        val cursor = cr.query(uri, arrayOf(
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
        ), null, null, null)
        var displayName = "video_${System.currentTimeMillis()}.mp4"
        var size = 0L
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (ni >= 0) displayName = c.getString(ni) ?: displayName
                if (si >= 0) size = c.getLong(si)
            }
        }
        svVaultDir(ctx).mkdirs()
        val id = UUID.randomUUID().toString()
        val destFile = File(svVaultDir(ctx), "$id-${displayName.replace("/", "_")}")
        cr.openInputStream(uri)?.use { inp ->
            destFile.outputStream().use { out -> inp.copyTo(out) }
        }
        if (size == 0L) size = destFile.length()
        // Delete original from MediaStore (move semantics)
        cr.delete(uri, null, null)
        val entry = VaultEntry(id, displayName, uri.toString(), size, destFile.absolutePath, System.currentTimeMillis())
        val existing = svLoadEntries(ctx).toMutableList()
        existing.add(0, entry)
        svSaveEntries(ctx, existing)
        entry
    }.getOrNull()
}

/**
 * Restores a vault file back to public MediaStore (Downloads / Movies folder).
 */
suspend fun svRestoreFile(ctx: Context, entry: VaultEntry) = withContext(Dispatchers.IO) {
    runCatching {
        val srcFile = File(entry.vaultPath)
        if (!srcFile.exists()) return@runCatching
        val values = android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, entry.displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Restored")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }
        val destUri = ctx.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
        ) ?: return@runCatching
        ctx.contentResolver.openOutputStream(destUri)?.use { out ->
            srcFile.inputStream().use { inp -> inp.copyTo(out) }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(destUri, values, null, null)
        }
        srcFile.delete()
        val remaining = svLoadEntries(ctx).filter { it.id != entry.id }
        svSaveEntries(ctx, remaining)
    }
}

/**
 * Permanently deletes a vault file — no recovery possible.
 */
fun svDeleteFile(ctx: Context, entry: VaultEntry) {
    runCatching { File(entry.vaultPath).delete() }
    val remaining = svLoadEntries(ctx).filter { it.id != entry.id }
    svSaveEntries(ctx, remaining)
}

/**
 * Plays a vault file using our own PlayerActivity.
 * Uses Uri.fromFile() — no FileProvider, no cross-app exposure.
 * LibVLC reads directly from app-private path in the same process.
 */
fun svPlayFile(ctx: Context, entry: VaultEntry) {
    val file = File(entry.vaultPath)
    if (!file.exists()) return
    val uri = Uri.fromFile(file)
    val intent = Intent(ctx, PlayerActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        setDataAndType(uri, "video/*")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(intent)
}

// ─── Formatting helpers ───────────────────────────────────────────────────────
private fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
    else                    -> "$bytes B"
}

// ─── Screen state ─────────────────────────────────────────────────────────────
private sealed class SVState {
    object Loading                                    : SVState()
    object SetupPin                                   : SVState()
    data class Auth(val bioEnabled: Boolean)          : SVState()
    object Vault                                      : SVState()
}

// ─── Root Screen ──────────────────────────────────────────────────────────────
@Composable
fun SecretVaultScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
) {
    val ctx = LocalContext.current
    var state by remember { mutableStateOf<SVState>(SVState.Loading) }

    LaunchedEffect(Unit) {
        state = if (svIsSetUp(ctx)) SVState.Auth(svBiometricEnabled(ctx)) else SVState.SetupPin
    }

    when (val s = state) {
        is SVState.Loading -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is SVState.SetupPin -> SVSetupPinScreen(
            modifier = modifier,
            onNavigateUp = onNavigateUp,
            onSetupComplete = { bioEnabled ->
                svSetBiometricEnabled(ctx, bioEnabled)
                state = SVState.Vault
            },
        )
        is SVState.Auth -> SVAuthScreen(
            modifier = modifier,
            onNavigateUp = onNavigateUp,
            biometricEnabled = s.bioEnabled,
            onUnlocked = { state = SVState.Vault },
        )
        is SVState.Vault -> SVVaultContent(
            modifier = modifier,
            onNavigateUp = onNavigateUp,
        )
    }
}

// ─── PIN DOT INDICATOR ────────────────────────────────────────────────────────
@Composable
private fun PinDots(pinLength: Int, filled: Int, shake: Boolean) {
    val offsetX by animateDpAsState(
        targetValue = if (shake) 8.dp else 0.dp,
        animationSpec = if (shake) spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessHigh) else spring(),
        label = "shake",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.offset(x = offsetX),
    ) {
        repeat(pinLength) { i ->
            val isFilled = i < filled
            val color by animateColorAsState(
                targetValue = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                animationSpec = tween(120),
                label = "dot_color_$i",
            )
            val scale by animateFloatAsState(
                targetValue = if (isFilled) 1.15f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dot_scale_$i",
            )
            Box(
                modifier = Modifier
                    .scale(scale)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.5.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
            )
        }
    }
}

// ─── NUMERIC KEYPAD ───────────────────────────────────────────────────────────
@Composable
private fun NumKeypad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onBiometric: (() -> Unit)? = null,
) {
    val keys = listOf("1","2","3","4","5","6","7","8","9","bio","0","del")
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        userScrollEnabled = false,
    ) {
        items(keys) { k ->
            when (k) {
                "bio" -> if (onBiometric != null) {
                    KeypadButton(
                        onClick = onBiometric,
                        content = { Icon(Icons.Rounded.LockOpen, "Biometric", modifier = Modifier.size(26.dp)) },
                    )
                } else {
                    Spacer(Modifier.height(52.dp))
                }
                "del" -> KeypadButton(
                    onClick = onBackspace,
                    content = { Icon(NextIcons.ArrowBack, "Delete", modifier = Modifier.size(22.dp)) },
                )
                else  -> KeypadButton(
                    onClick = { onDigit(k) },
                    content = { Text(k, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) },
                )
            }
        }
    }
}

@Composable
private fun KeypadButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f),
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

// ─── SETUP PIN SCREEN ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SVSetupPinScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onSetupComplete: (bioEnabled: Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    var step by remember { mutableIntStateOf(0) } // 0=create, 1=confirm, 2=biometric
    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val canUseBiometric = remember {
        val bm = BiometricManager.from(ctx)
        bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (step < 2) "Set Up Secret Vault" else "Enable Biometric") },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(NextIcons.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (step) {
                0, 1 -> {
                    Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = if (step == 0) "Create a 4-digit PIN" else "Confirm your PIN",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = if (step == 0) "This PIN protects your hidden videos" else "Enter the same PIN again",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Spacer(Modifier.height(32.dp))
                    PinDots(pinLength = 4, filled = pin.length, shake = shake)
                    AnimatedVisibility(visible = error.isNotEmpty()) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(40.dp))
                    NumKeypad(
                        onDigit = { d ->
                            if (pin.length < 4) {
                                pin += d
                                error = ""
                                if (pin.length == 4) {
                                    scope.launch {
                                        delay(120)
                                        when (step) {
                                            0 -> { firstPin = pin; pin = ""; step = 1 }
                                            1 -> {
                                                if (pin == firstPin) {
                                                    svSetupPin(ctx, pin)
                                                    if (canUseBiometric) { step = 2 } else { onSetupComplete(false) }
                                                } else {
                                                    shake = true
                                                    error = "PINs do not match. Try again."
                                                    pin = ""
                                                    step = 0
                                                    firstPin = ""
                                                    delay(400); shake = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1); error = "" },
                    )
                }
                2 -> {
                    Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text("Enable Biometric Unlock?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Use fingerprint or face unlock to quickly open the vault.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(32.dp))
                    Button(onClick = { onSetupComplete(true) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.LockOpen, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Enable Biometric")
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { onSetupComplete(false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Use PIN Only")
                    }
                }
            }
        }
    }
}

// ─── AUTH SCREEN ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SVAuthScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    biometricEnabled: Boolean,
    onUnlocked: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var shake by remember { mutableStateOf(false) }
    var attempts by remember { mutableIntStateOf(0) }

    fun launchBiometric() {
        val activity = ctx as? FragmentActivity ?: return
        val executor = ContextCompat.getMainExecutor(ctx)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onUnlocked() }
            override fun onAuthenticationError(code: Int, msg: CharSequence) {
                if (code != BiometricPrompt.ERROR_NEGATIVE_BUTTON && code != BiometricPrompt.ERROR_USER_CANCELED)
                    error = msg.toString()
            }
            override fun onAuthenticationFailed() { error = "Biometric not recognised. Try again." }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Secret Vault")
            .setSubtitle("Confirm your identity to open the vault")
            .setNegativeButtonText("Use PIN")
            .build()
        prompt.authenticate(info)
    }

    LaunchedEffect(biometricEnabled) { if (biometricEnabled) launchBiometric() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secret Vault") },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(NextIcons.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(20.dp))
            Text("Enter PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (attempts > 0) {
                Text(
                    text = "Wrong PIN ($attempts attempt${if (attempts > 1) "s" else ""})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            PinDots(pinLength = 4, filled = pin.length, shake = shake)
            AnimatedVisibility(visible = error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp), textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(40.dp))
            NumKeypad(
                onDigit = { d ->
                    if (pin.length < 4) {
                        pin += d
                        error = ""
                        if (pin.length == 4) {
                            scope.launch {
                                delay(120)
                                if (svVerifyPin(ctx, pin)) {
                                    onUnlocked()
                                } else {
                                    shake = true
                                    attempts++
                                    error = "Incorrect PIN. Try again."
                                    pin = ""
                                    delay(500); shake = false
                                }
                            }
                        }
                    }
                },
                onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1); error = "" },
                onBiometric = if (biometricEnabled) ({ launchBiometric() }) else null,
            )
        }
    }
}

// ─── VAULT CONTENT ────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SVVaultContent(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(svLoadEntries(ctx)) }
    var isImporting by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf("") }

    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            isImporting = true
            scope.launch {
                val result = svHideFile(ctx, uri)
                withContext(Dispatchers.Main) {
                    isImporting = false
                    if (result != null) {
                        entries = svLoadEntries(ctx)
                    } else {
                        importError = "Could not hide file. Please try again."
                    }
                }
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secret Vault") },
                navigationIcon = { IconButton(onClick = onNavigateUp) { Icon(NextIcons.ArrowBack, "Back") } },
                actions = {
                    Text(
                        text = "${entries.size} file${if (entries.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 16.dp),
                    )
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickMedia.launch(arrayOf("video/*")) },
                icon = { Icon(Icons.Rounded.Add, "Add video") },
                text = { Text("Hide Video") },
            )
        },
    ) { padding ->
        Box(modifier = modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty() && !isImporting) {
                SVEmptyState(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        SVVideoCard(
                            entry = entry,
                            onPlay = { svPlayFile(ctx, entry) },
                            onRestore = {
                                scope.launch {
                                    svRestoreFile(ctx, entry)
                                    withContext(Dispatchers.Main) { entries = svLoadEntries(ctx) }
                                }
                            },
                            onDelete = {
                                svDeleteFile(ctx, entry)
                                entries = svLoadEntries(ctx)
                            },
                        )
                    }
                }
            }

            // Importing overlay
            AnimatedVisibility(
                visible = isImporting,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Card(shape = RoundedCornerShape(16.dp)) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator()
                            Text("Moving to vault…", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "File is being securely moved\nto private storage",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            // Import error snackbar
            if (importError.isNotEmpty()) {
                LaunchedEffect(importError) {
                    delay(3000)
                    importError = ""
                }
                Snackbar(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    action = { TextButton(onClick = { importError = "" }) { Text("Dismiss") } },
                ) { Text(importError) }
            }
        }
    }
}

// ─── EMPTY STATE ──────────────────────────────────────────────────────────────
@Composable
private fun SVEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
        Text("Your vault is empty", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Tap \"Hide Video\" to securely move videos\ninto this private vault. They won't appear\nin your gallery or file manager.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

// ─── VIDEO CARD ───────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SVVideoCard(
    entry: VaultEntry,
    onPlay: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onPlay,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Play",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            // Info row
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatSize(entry.sizeByes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.MoreVert, "Options", modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Unhide (Restore)") },
                            leadingIcon = { Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; onRestore() },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Permanently", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) },
                            onClick = { showMenu = false; showDeleteConfirm = true },
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Permanently?") },
            text = {
                Text(
                    "\"${entry.displayName}\" will be permanently deleted.\n\nThis cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { showDeleteConfirm = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}
