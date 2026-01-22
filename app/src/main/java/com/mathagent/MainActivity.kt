package com.mathagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicslayer.radius
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Math Mentor - Socratic Math Tutor
 *
 * A fully offline Android app powered by Qwen2.5-Math running locally via llama.cpp.
 *
 * Features:
 * - 100% offline operation after model download
 * - 8 math tools with SymPy integration
 * - Glassmorphism UI matching web aesthetic
 * - Streaming responses with typewriter effect
 * - Vulkan GPU acceleration
 */
class MainActivity : ComponentActivity() {
    private lateinit var llamaEngine: LlamaEngine
    private lateinit var reactAgent: ReActAgent
    private lateinit var modelManager: ModelManager

    // App state
    private val appState = mutableStateOf(AppState.Loading)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkAndLoadModel()
        } else {
            appState.value = AppState.PermissionDenied
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize components
        llamaEngine = LlamaEngine(applicationContext)
        reactAgent = ReActAgent(llamaEngine, applicationContext)
        modelManager = ModelManager(applicationContext)

        // Check storage permission
        checkPermission()

        setContent {
            MathMentorTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Ambient background glows
                    AmbientBackgroundGlows()

                    // Main content based on state
                    when (val state = appState.value) {
                        is AppState.Loading -> LoadingScreen()
                        is AppState.PermissionDenied -> PermissionDeniedScreen {
                            checkPermission()
                        }
                        is AppState.DownloadRequired -> ModelDownloadScreen(
                            onDownloadModel = { model -> startDownload(model) },
                            onDownloadCustomUrl = { url -> downloadCustomUrl(url) }
                        )
                        is AppState.Downloading -> DownloadingScreen(
                            model = state.model,
                            progress = state.progress,
                            onCancel = { /* TODO */ }
                        )
                        is AppState.Ready -> ChatScreen(
                            agent = reactAgent,
                            isModelLoaded = true,
                            modelManager = modelManager
                        )
                        is AppState.Error -> ErrorScreen(
                            message = (state as AppState.Error).message,
                            onRetry = { checkAndLoadModel() }
                        )
                    }
                }
            }
        }
    }

    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED -> {
                checkAndLoadModel()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun checkAndLoadModel() {
        lifecycleScope.launch {
            appState.value = AppState.Loading

            when {
                modelManager.isAnyModelDownloaded() -> {
                    val success = loadModel(modelManager.activeModelFile.absolutePath)
                    if (success) {
                        appState.value = AppState.Ready
                    } else {
                        appState.value = AppState.Error("Failed to load model")
                    }
                }
                else -> {
                    appState.value = AppState.DownloadRequired
                }
            }
        }
    }

    private fun startDownload(modelOption: ModelOption? = null) {
        lifecycleScope.launch {
            appState.value = AppState.Downloading(
                model = modelOption ?: ModelManager.AVAILABLE_MODELS.first { it.recommended },
                progress = 0f
            )

            val selectedModel = modelOption ?: ModelManager.AVAILABLE_MODELS.first { it.recommended }

            val result = modelManager.downloadFromUrl(
                url = selectedModel.url,
                expectedSize = selectedModel.sizeBytes
            ) { downloaded, total ->
                (appState.value as? AppState.Downloading)?.let { state ->
                    appState.value = state.copy(progress = downloaded.toFloat() / total)
                }
            }

            result.fold(
                onSuccess = { file ->
                    val success = loadModel(file.absolutePath)
                    if (success) {
                        appState.value = AppState.Ready
                    } else {
                        appState.value = AppState.Error("Failed to load model")
                    }
                },
                onFailure = { error ->
                    appState.value = AppState.Error("Download failed: ${error.message}")
                }
            )
        }
    }

    private fun downloadCustomUrl(url: String) {
        lifecycleScope.launch {
            appState.value = AppState.Downloading(
                model = ModelOption(
                    id = "custom",
                    name = "Custom Model",
                    description = "From custom URL",
                    sizeBytes = 0,
                    url = url
                ),
                progress = 0f
            )

            val result = modelManager.downloadFromUrl(url = url) { downloaded, total ->
                (appState.value as? AppState.Downloading)?.let { state ->
                    appState.value = state.copy(progress = downloaded.toFloat() / total)
                }
            }

            result.fold(
                onSuccess = { file ->
                    val success = loadModel(file.absolutePath)
                    if (success) {
                        appState.value = AppState.Ready
                    } else {
                        appState.value = AppState.Error("Failed to load model")
                    }
                },
                onFailure = { error ->
                    appState.value = AppState.Error("Download failed: ${error.message}")
                }
            )
        }
    }

    private suspend fun loadModel(path: String): Boolean {
        return try {
            llamaEngine.loadModel(path)
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        llamaEngine.close()
    }
}

// ==========================================================================
// App States
// ==========================================================================

sealed class AppState {
    object Loading : AppState()
    object PermissionDenied : AppState()
    object DownloadRequired : AppState()
    data class Downloading(val model: ModelOption, val progress: Float) : AppState()
    object Ready : AppState()
    data class Error(val message: String) : AppState()
}

// ==========================================================================
// Theme
// ==========================================================================

@Composable
fun MathMentorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF10B981),           // Emerald 500
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0xFF064E3B),  // Emerald 900
            onPrimaryContainer = Color(0xFF6EE7D7), // Emerald 100
            secondary = Color(0xFF14B8A6),         // Teal 600
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFF0F766E), // Teal 900
            onSecondaryContainer = Color(0xFF5DDAD5), // Teal 100
            tertiary = Color(0xFF8B5CF6),          // Purple 500
            onTertiary = Color(0xFFFFFFFF),
            background = Color(0xFF09090B),         // Zinc 950
            onBackground = Color(0xFFFAFAFA),
            surface = Color(0xFF18181B),           // Zinc 900
            onSurface = Color(0xFFFAFAFA),
            surfaceVariant = Color(0xFF27272A),    // Zinc 800
            onSurfaceVariant = Color(0xFFA1A1AA),
        ),
        typography = Typography(
            displayLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
            titleLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
            bodyLarge = TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
            ),
            labelMedium = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
            ),
        ),
        content = content
    )
}

// ==========================================================================
// Ambient Background Glows
// ==========================================================================

@Composable
fun AmbientBackgroundGlows() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF38B8F8).copy(alpha = 0.1f),  // Sky blue
                        Color.Transparent,
                    ),
                    center = Offset(0f, 0f),
                    radius = 600f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF8B5CF6).copy(alpha = 0.15f), // Purple
                        Color.Transparent,
                    ),
                    center = Offset(2000f, 0f),
                    radius = 600f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEC4899).copy(alpha = 0.1f),  // Pink
                        Color.Transparent,
                    ),
                    center = Offset(2000f, 2000f),
                    radius = 600f
                )
            )
    )
}

// ==========================================================================
// Loading Screen
// ==========================================================================

@Composable
fun LoadingScreen() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")

    val floatOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "float"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo with gradient
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .offset(y = floatOffset.dp)
                    .shadow(
                        elevation = 16.dp,
                        spotColor = Color(0xFF10B981).copy(alpha = 0.4f),
                        ambientColor = Color(0xFF10B981).copy(alpha = 0.2f)
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF10B981), // Emerald 500
                                Color(0xFF0D9488), // Emerald 600
                                Color(0xFF14B8A6), // Teal 600
                            )
                        ),
                        CircleShape
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "π",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "Math Mentor",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "SOCRATIC TUTOR",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,
                color = Color(0xFF34D399).copy(alpha = 0.7f)
            )

            // Loading indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                Color.White.copy(alpha = pulseAlpha),
                                CircleShape
                            )
                    )
                }
            }

            Text(
                text = "Initializing...",
                fontSize = 14.sp,
                color = Color(0xFF71717A)
            )
        }
    }
}

// ==========================================================================
// Permission Denied Screen
// ==========================================================================

@Composable
fun PermissionDeniedScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF18181B).copy(alpha = 0.8f),
                    RoundedCornerShape(24.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 48.sp
            )
            Text(
                text = "Storage Permission Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Math Mentor needs permission to download the AI model (~940MB) to your device.",
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 4.dp,
                        spotColor = Color(0xFF10B981).copy(alpha = 0.3f),
                        ambientColor = Color.Transparent
                    )
                    .border(
                        width = 2.dp,
                        color = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF10B981),
                                Color(0xFF14B8A6),
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF10B981).copy(alpha = 0.2f),
                                Color(0xFF14B8A6).copy(alpha = 0.1f),
                            )
                        )
                    )
            ) {
                Text(
                    "Grant Permission",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// ==========================================================================
// Error Screen
// ==========================================================================

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF18181B).copy(alpha = 0.8f),
                    RoundedCornerShape(24.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Text(
                text = "❌",
                fontSize = 48.sp
            )
            Text(
                text = "Oops!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = message,
                color = Color(0xFFA1A1AA),
                textAlign = TextAlign.Center,
                fontSize = 14.sp
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .shadow(
                        elevation = 4.dp,
                        spotColor = Color(0xFF10B981).copy(alpha = 0.3f),
                        ambientColor = Color.Transparent
                    )
                    .border(
                        width = 2.dp,
                        color = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF10B981),
                                Color(0xFF14B8A6),
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Text(
                    "Retry",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
    }
}

// ==========================================================================
// Model Download Screen
// ==========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelDownloadScreen(
    onDownloadModel: (ModelOption) -> Unit,
    onDownloadCustomUrl: (String) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Browse", "Custom URL")
    var customUrl by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(
                        elevation = 12.dp,
                        spotColor = Color(0xFF10B981).copy(alpha = 0.4f),
                        ambientColor = Color(0xFF10B981).copy(alpha = 0.2f)
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF10B981),
                                Color(0xFF14B8A6),
                            )
                        ),
                        CircleShape
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "π",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = "Download Model",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "Choose a model or paste a HuggingFace URL",
                fontSize = 14.sp,
                color = Color(0xFF71717A)
            )
        }

        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color(0xFF10B981),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier
                        .tabIndicatorOffset(tabPositions[selectedTab])
                        .padding(horizontal = 24.dp),
                    color = Color(0xFF10B981),
                    height = 3.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            title,
                            color = if (selectedTab == index) Color.White else Color(0xFF71717A),
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Content
        when (selectedTab) {
            0 -> ModelBrowserContent(onDownloadModel = onDownloadModel)
            1 -> CustomUrlContent(
                url = customUrl,
                onUrlChange = { customUrl = it },
                onDownload = { if (customUrl.isNotBlank()) onDownloadCustomUrl(customUrl) }
            )
        }
    }
}

@Composable
fun ModelBrowserContent(onDownloadModel: (ModelOption) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "AVAILABLE MODELS",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = Color(0xFF71717A)
        )

        ModelManager.AVAILABLE_MODELS.forEach { model ->
            ModelCard(
                model = model,
                onClick = { onDownloadModel(model) }
            )
        }
    }
}

@Composable
fun ModelCard(model: ModelOption, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { }
            )
            .combinedClick(
                onClick = onClick,
                onPress = { pressed = true },
                onRelease = { pressed = false }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (model.recommended)
                Color(0xFF064E3B).copy(alpha = 0.5f) // Emerald 900
            else
                Color(0xFF18181B).copy(alpha = 0.8f) // Zinc 900
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (model.recommended)
            androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF10B981).copy(alpha = 0.5f)
            )
        else null,
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (model.recommended) {
                            Surface(
                                color = Color(0xFF10B981),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "BEST",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = model.description,
                        fontSize = 13.sp,
                        color = Color(0xFFA1A1AA),
                        lineHeight = 18.sp
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.sizeDisplay,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF71717A)
                )
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .shadow(
                            elevation = 4.dp,
                            spotColor = Color(0xFF10B981).copy(alpha = 0.3f),
                            ambientColor = Color.Transparent
                        )
                        .border(
                            width = 2.dp,
                            color = Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF10B981),
                                    Color(0xFF14B8A6),
                                )
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Text(
                        "Download",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun CustomUrlContent(
    url: String,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Info card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF18181B).copy(alpha = 0.8f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.1f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Input,
                        contentDescription = null,
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Direct HuggingFace URL",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        // URL input
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "https://huggingface.co/.../model.gguf",
                    color = Color(0xFF71717A)
                )
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = Color(0xFF09090B).copy(alpha = 0.5f),
                textColor = Color.White,
                placeholderColor = Color(0xFF71717A),
                focusedBorderColor = Color(0xFF10B981),
                unfocusedBorderColor = Color(0xFF27272A)
            ),
            shape = RoundedCornerShape(16.dp),
            minLines = 4,
            maxLines = 6
        )

        // Tip card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF064E3B).copy(alpha = 0.3f)
            ),
            shape = RoundedCornerShape(12.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF10B981).copy(alpha = 0.3f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "💡 Tip",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34D399)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Paste any direct download link to a .gguf model file from HuggingFace. The model must be in GGUF format.",
                    fontSize = 13.sp,
                    color = Color(0xFFA1A1AA),
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDownload,
            enabled = url.isNotBlank() && url.endsWith(".gguf", ignoreCase = true),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .shadow(
                    elevation = 4.dp,
                    spotColor = Color(0xFF10B981).copy(alpha = 0.3f),
                    ambientColor = Color.Transparent
                )
                .border(
                    width = 2.dp,
                    color = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF10B981),
                            Color(0xFF14B8A6),
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            )
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Download from URL",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (url.isNotBlank() && url.endsWith(".gguf", ignoreCase = true))
                    Color.White
                else
                    Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun DownloadingScreen(
    model: ModelOption,
    progress: Float,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF18181B).copy(alpha = 0.8f),
                    RoundedCornerShape(24.dp)
                )
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(48.dp)
            )

            Text(
                text = "Downloading Model",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = model.name,
                fontSize = 14.sp,
                color = Color(0xFFA1A1AA)
            )

            // Progress card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF09090B).copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    Color.White.copy(alpha = 0.1f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = model.sizeDisplay,
                            fontSize = 14.sp,
                            color = Color(0xFF71717A),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF27272A)
                    )
                }
            }

            Text(
                text = "Please wait while the model downloads...",
                fontSize = 13.sp,
                color = Color(0xFF71717A)
            )
        }
    }
}

// ==========================================================================
// Chat Screen - Main App Interface
// ==========================================================================

@Composable
fun ChatScreen(agent: ReActAgent, isModelLoaded: Boolean, modelManager: ModelManager) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    val inputState = remember { mutableStateOf("") }
    val isGenerating = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (messages.isEmpty()) {
            messages.add(
                ChatMessage(
                    id = "0",
                    role = MessageRole.Assistant,
                    content = "Hi! 👋 I'm your Socratic Math Tutor. I'll help you discover answers through questions, not just give you solutions. That's how we learn! Try asking me about any math problem.",
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF18181B).copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.1f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Logo
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .shadow(
                            elevation = 8.dp,
                            spotColor = Color(0xFF10B981).copy(alpha = 0.4f),
                            ambientColor = Color(0xFF10B981).copy(alpha = 0.2f)
                        )
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF10B981),
                                    Color(0xFF14B8A6),
                                )
                            ),
                            CircleShape
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "π",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Math Mentor",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Socratic Tutor • Offline",
                        fontSize = 11.sp,
                        color = Color(0xFF34D399).copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                }

                // Status indicator
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                Color(0xFF10B981),
                                CircleShape
                            )
                            .pulse()
                    )
                    Text(
                        text = if (isModelLoaded) "Ready" else "Loading",
                        fontSize = 11.sp,
                        color = if (isModelLoaded) Color(0xFF34D399) else Color(0xFFFBBF24)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(
                items = messages,
                key = { it.id }
            ) { message ->
                AnimatedMessageBubble(message)
            }
        }

        // Input Area
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF09090B).copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color.White.copy(alpha = 0.08f)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = inputState.value,
                        onValueChange = { inputState.value = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                "Ask me about your math problem...",
                                color = Color(0xFF71717A),
                                fontSize = 15.sp
                            )
                        },
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            containerColor = Color.Transparent,
                            textColor = Color.White,
                            placeholderColor = Color(0xFF71717A),
                            cursorColor = Color(0xFF10B981),
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    IconButton(
                        onClick = {
                            val userMessage = inputState.value.trim()
                            if (userMessage.isNotEmpty() && !isGenerating.value && isModelLoaded) {
                                messages.add(
                                    ChatMessage(
                                        id = UUID.randomUUID().toString(),
                                        role = MessageRole.User,
                                        content = userMessage,
                                        timestamp = System.currentTimeMillis()
                                    )
                                )

                                isGenerating.value = true
                                inputState.value = ""

                                scope.launch {
                                    val assistantMessageId = UUID.randomUUID().toString()
                                    var fullContent = ""

                                    messages.add(
                                        ChatMessage(
                                            id = assistantMessageId,
                                            role = MessageRole.Assistant,
                                            content = "",
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )

                                    agent.chat(userMessage).collect { event ->
                                        when (event) {
                                            is AgentEvent.Token -> {
                                                fullContent += event.text
                                                updateMessageContent(messages, assistantMessageId, fullContent)
                                            }
                                            is AgentEvent.ToolCall -> {
                                                updateMessageContent(
                                                    messages,
                                                    assistantMessageId,
                                                    fullContent + "\n🔧 ${event.tool}"
                                                )
                                            }
                                            is AgentEvent.FinalAnswer -> {
                                                fullContent = event.text
                                                updateMessageContent(messages, assistantMessageId, fullContent)
                                            }
                                            is AgentEvent.Error -> {
                                                updateMessageContent(
                                                    messages,
                                                    assistantMessageId,
                                                    fullContent + "\n❌ Error: ${event.message}"
                                                )
                                            }
                                            else -> {}
                                        }
                                    }

                                    isGenerating.value = false
                                }
                            }
                        },
                        enabled = !isGenerating.value && isModelLoaded && inputState.value.isNotBlank(),
                        modifier = Modifier
                            .size(52.dp)
                            .then(
                                if (inputState.value.isNotBlank()) {
                                    Modifier.shadow(
                                        elevation = 8.dp,
                                        spotColor = Color(0xFF10B981).copy(alpha = 0.5f),
                                        ambientColor = Color.Transparent
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .background(
                                if (inputState.value.isNotBlank())
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF10B981),
                                            Color(0xFF0D9488),
                                        )
                                    )
                                else
                                    Color(0xFF27272A)
                                ,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (inputState.value.isNotBlank()) Color.White else Color(0xFF71717A)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================================================
// Animated Message Bubble
// ==========================================================================

@Composable
fun AnimatedMessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.User

    val density = LocalDensity.current
    val visibleState = remember { MutableTransitionState(false) }

    LaunchedEffect(message.id) {
        visibleState.targetState = true
    }

    val scale by animateFloatAsState(
        targetValue = if (visibleState.targetState) 1f else 0.95f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 300f
        ),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (visibleState.targetState) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "alpha"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha)
            .scale(scale),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        // Avatar (for assistant)
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .shadow(
                        elevation = 6.dp,
                        spotColor = Color(0xFF8B5CF6).copy(alpha = 0.3f),
                        ambientColor = Color.Transparent
                    )
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF8B5CF6), // Purple 500
                                Color(0xFFA855F7), // Purple 500
                            )
                        ),
                        CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        // Message bubble
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isUser)
                        Color.Transparent
                    else
                        Color.White.copy(alpha = 0.1f)
                ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) {
                        Color.Transparent
                    } else {
                        Color(0xFF18181B).copy(alpha = 0.95f)
                    }
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (isUser) {
                                Modifier.background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF10B981),
                                            Color(0xFF14B8A6),
                                        )
                                    ),
                                    RoundedCornerShape(20.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = message.content,
                        color = if (isUser) Color.White else Color(0xFFFAFAFA),
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                }
            }

            // Timestamp
            Text(
                text = formatTimestamp(message.timestamp),
                fontSize = 11.sp,
                color = Color(0xFF71717A),
                modifier = Modifier.padding(
                    start = if (isUser) 0.dp else 4.dp,
                    end = if (isUser) 4.dp else 0.dp
                )
            )
        }

        // Avatar (for user)
        if (isUser) {
            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        Color(0xFF27272A),
                        CircleShape
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFFA1A1AA),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

fun updateMessageContent(messages: MutableList<ChatMessage>, id: String, newContent: String) {
    val index = messages.indexOfFirst { it.id == id }
    if (index >= 0) {
        messages[index] = messages[index].copy(content = newContent)
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ==========================================================================
// Data Types
// ==========================================================================

enum class MessageRole { User, Assistant }

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long
)

// ==========================================================================
// Helper Extensions
// ==========================================================================

fun Modifier.combinedClick(
    onClick: () -> Unit,
    onPress: () -> Unit,
    onRelease: () -> Unit
) = composed {
    val pressedState = remember { mutableStateOf(false) }

    this
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    pressedState.value = true
                    onPress()
                    tryAwaitRelease()
                    pressedState.value = false
                    onRelease()
                    onClick()
                }
            )
        }
}

fun Modifier.pulse() = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, delayMillis = 200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse"
    )
    this.alpha(alpha)
}
