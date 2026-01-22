package com.mathagent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main Activity for Math Mentor Android App
 *
 * Features:
 * - Model download from GitHub/HuggingFace
 * - Streaming chat interface with typewriter effect
 * - Tool call visualization
 * - Socratic math tutoring
 * - Qwen2.5-Math-1.5B running locally via llama.cpp
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

        // Initialize components
        llamaEngine = LlamaEngine(applicationContext)
        reactAgent = ReActAgent(llamaEngine, applicationContext)
        modelManager = ModelManager(applicationContext)

        // Check storage permission
        checkPermission()

        setContent {
            MathMentorTheme {
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
                        onCancel = { /* TODO: Implement cancel */ }
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
                    // Model exists, load it
                    val success = loadModel(modelManager.activeModelFile.absolutePath)
                    if (success) {
                        appState.value = AppState.Ready
                    } else {
                        appState.value = AppState.Error("Failed to load model")
                    }
                }
                else -> {
                    // Need to download
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
// Screen Composables
// ==========================================================================

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "🧮",
                fontSize = 64.sp
            )
            Text(
                text = "Math Mentor",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            CircularProgressIndicator(
                color = Color(0xFF3B82F6)
            )
            Text(
                text = "Initializing...",
                color = Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
fun PermissionDeniedScreen(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "⚠️",
                fontSize = 64.sp
            )
            Text(
                text = "Storage Permission Required",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Math Mentor needs to download the AI model (~940MB) to your device.",
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRequest,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                )
            ) {
                Text("Grant Permission")
            }
        }
    }
}

@Composable
fun DownloadScreen(
    progress: Float,
    message: String,
    onStartDownload: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Download Math Model",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Qwen2.5-Math-1.5B (~940MB)",
                color = Color(0xFF94A3B8)
            )

            if (progress > 0) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF3B82F6)
                )
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = message,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            } else {
                Button(
                    onClick = onStartDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    )
                ) {
                    Text("Download Model")
                }
            }
        }
    }
}

// ==========================================================================
// Model Download Screen (NEW)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0F172A),
                shadowElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = Color(0xFF3B82F6),
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = "Download Model",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Choose a model or paste a HuggingFace URL",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Tab Row
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF3B82F6),
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF3B82F6)
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
                                        color = if (selectedTab == index) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            )
                        }
                    }
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
}

@Composable
fun ModelBrowserContent(onDownloadModel: (ModelOption) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Available Models",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (model.recommended) Color(0xFF1E3A5F) else Color(0xFF1E293B)
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (model.recommended)
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6))
        else null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                color = Color(0xFF3B82F6),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "RECOMMENDED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = model.description,
                        fontSize = 13.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Download",
                    tint = Color(0xFF3B82F6)
                )
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.sizeDisplay,
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6)
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Download", fontSize = 13.sp)
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
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Input,
                        contentDescription = null,
                        tint = Color(0xFF3B82F6)
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

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    "https://huggingface.co/.../model.gguf",
                    color = Color(0xFF64748B)
                )
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = Color(0xFF1E293B),
                textColor = Color.White,
                placeholderColor = Color(0xFF64748B),
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF475569)
            ),
            shape = RoundedCornerShape(12.dp),
            minLines = 3,
            maxLines = 5
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A5F)),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "💡 Tip",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF60A5FA)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste any direct download link to a .gguf model file from HuggingFace. The model must be in GGUF format for llama.cpp.",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onDownload,
            enabled = url.isNotBlank() && url.endsWith(".gguf", ignoreCase = true),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF3B82F6),
                disabledContainerColor = Color(0xFF334155)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Download from URL")
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
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                tint = Color(0xFF3B82F6),
                modifier = Modifier.size(64.dp)
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
                color = Color(0xFF94A3B8)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = model.sizeDisplay,
                            fontSize = 14.sp,
                            color = Color(0xFF94A3B8)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF3B82F6)
                    )
                }
            }

            Text(
                text = "Please wait while the model downloads...",
                color = Color(0xFF64748B),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun ErrorScreen(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "❌",
                fontSize = 64.sp
            )
            Text(
                text = "Oops!",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = message,
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3B82F6)
                )
            ) {
                Text("Retry")
            }
        }
    }
}

// ==========================================================================
// Chat Screen
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
                    content = "Hi! 👋 I'm your Socratic Math Tutor. I'm running locally on your device using Qwen2.5-Math. Ask me anything about math!",
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF1E1B4B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Header(isModelLoaded = true)

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                items(
                    items = messages,
                    key = { it.id }
                ) { message ->
                    MessageBubble(message)
                }
            }

            InputBar(
                value = inputState.value,
                onValueChange = { inputState.value = it },
                onSend = {
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
                                            fullContent + "\n🔧 Using: ${event.tool}"
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
                enabled = !isGenerating.value && isModelLoaded
            )
        }
    }
}

fun updateMessageContent(messages: MutableList<ChatMessage>, id: String, newContent: String) {
    val index = messages.indexOfFirst { it.id == id }
    if (index >= 0) {
        messages[index] = messages[index].copy(content = newContent)
    }
}

@Composable
fun Header(isModelLoaded: Boolean) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        color = Color(0xFF0F172A),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧮 Math Mentor",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (isModelLoaded) "Qwen2.5-Math • Ready" else "Loading...",
                fontSize = 12.sp,
                color = if (isModelLoaded) Color(0xFF4ADE80) else Color(0xFFFBBF24)
            )
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.User
    val backgroundColor = if (isUser) {
        Color(0xFF3B82F6)
    } else {
        Color(0xFF1E293B)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 15.sp
                )
                Text(
                    text = formatTimestamp(message.timestamp),
                    color = if (isUser) Color(0xFFDBEAFE) else Color(0xFF64748B),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF0F172A),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask me a math question...", color = Color(0xFF64748B)) },
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = Color(0xFF1E293B),
                    textColor = Color.White,
                    placeholderColor = Color(0xFF64748B),
                    focusedBorderColor = Color(0xFF3B82F6),
                    unfocusedBorderColor = Color.Transparent
                ),
                shape = RoundedCornerShape(24.dp),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = enabled && value.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (enabled && value.isNotBlank())
                            Color(0xFF3B82F6)
                        else
                            Color(0xFF334155),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

// ==========================================================================
// Data types
// ==========================================================================

enum class MessageRole { User, Assistant }

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Long
)

fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun MathMentorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF3B82F6),
            secondary = Color(0xFF8B5CF6),
            background = Color(0xFF0F172A),
            surface = Color(0xFF1E293B)
        ),
        content = content
    )
}
