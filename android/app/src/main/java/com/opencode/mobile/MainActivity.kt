package com.opencode.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            navigateToWebView()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE)

        if (intent?.data?.scheme == "opencode") {
            handleDeepLink(intent)
            return
        }

        if (prefs.getBoolean("setup_complete", false)) {
            navigateToWebView()
            return
        }

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                SetupWizard(
                    onComplete = { apiKey ->
                        prefs.edit().apply {
                            putBoolean("setup_complete", true)
                            putString("api_key", apiKey)
                            apply()
                        }
                        val provider = prefs.getString("selected_provider", "") ?: ""
                        if (provider == "Ollama (Local)") {
                            prefs.edit().putBoolean("use_termux", true).apply()
                            navigateToTermux()
                        } else if (provider == "OpenCode Zen (Big Pickle)") {
                            prefs.edit().putBoolean("use_cloud", true).apply()
                            navigateToWebView()
                        } else {
                            requestPermissions()
                        }
                    },
                    onTermuxMode = {
                        prefs.edit().putBoolean("use_termux", true).apply()
                        navigateToTermux()
                    },
                    onProviderSelected = { provider ->
                        prefs.edit().putString("selected_provider", provider).apply()
                    }
                )
            }
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data
        when (data?.host) {
            "session" -> {
                val sessionId = data.getQueryParameter("id")
                val i = Intent(this, WebViewActivity::class.java).apply {
                    putExtra("session_id", sessionId)
                }
                startActivity(i)
                finish()
            }
            "settings" -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                finish()
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            navigateToWebView()
        }
    }

    private fun navigateToWebView() {
        val isCloud = prefs.getBoolean("use_cloud", false)
        val serverUrl = if (isCloud) {
            "https://opencode.ai"
        } else {
            prefs.getString("server_url", "http://localhost:3000") ?: "http://localhost:3000"
        }
        val i = Intent(this, WebViewActivity::class.java).apply {
            putExtra("server_url", serverUrl)
            putExtra("api_key", prefs.getString("api_key", ""))
            putExtra("use_cloud", isCloud)
        }
        startActivity(i)
        finish()
    }

    private fun navigateToTermux() {
        val i = Intent(this, WebViewActivity::class.java).apply {
            putExtra("server_url", "http://localhost:3000")
            putExtra("use_termux", true)
        }
        startActivity(i)
        finish()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupWizard(
    onComplete: (String) -> Unit,
    onTermuxMode: () -> Unit,
    onProviderSelected: (String) -> Unit = {}
) {
    var currentPage by remember { mutableIntStateOf(0) }
    var apiKey by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("http://localhost:3000") }
    var selectedProvider by remember { mutableStateOf("OpenCode Zen (Big Pickle)") }
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val providers = listOf("OpenCode Zen (Big Pickle)", "OpenAI", "Anthropic", "Google Gemini", "Ollama (Local)", "Custom")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (currentPage + 1) / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp)),
                color = MaterialTheme.colorScheme.primary,
            )

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> ProviderPage(
                        selectedProvider = selectedProvider,
                        onProviderChange = { 
                            selectedProvider = it
                            onProviderSelected(it)
                        },
                        apiKey = apiKey,
                        onApiKeyChange = { apiKey = it },
                        serverUrl = serverUrl,
                        onServerUrlChange = { serverUrl = it }
                    )
                    2 -> ControlsPage()
                    3 -> TermuxPage()
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    TextButton(
                        onClick = {
                            currentPage--
                            scope.launch { pagerState.animateScrollToPage(currentPage) }
                        }
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row {
                    if (currentPage == 2) {
                        OutlinedButton(
                            onClick = onTermuxMode
                        ) {
                            Text("Use Termux")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            if (currentPage < 3) {
                                currentPage++
                                scope.launch { pagerState.animateScrollToPage(currentPage) }
                            } else {
                                onComplete(apiKey)
                            }
                        },
                        enabled = if (currentPage == 1) (apiKey.isNotBlank() || selectedProvider == "OpenCode Zen (Big Pickle)" || selectedProvider == "Ollama (Local)") else true
                    ) {
                        Text(if (currentPage == 3) "Get Started" else "Next")
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "OpenCode Mobile",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "AI Coding Agent for Android",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FeatureItem(icon = Icons.Default.SmartToy, text = "Multi-provider AI support")
                FeatureItem(icon = Icons.Default.Edit, text = "Code editing & generation")
                FeatureItem(icon = Icons.Default.Build, text = "Shell command execution")
                FeatureItem(icon = Icons.Default.PhoneAndroid, text = "Optimized for mobile")
            }
        }
    }
}

@Composable
fun FeatureItem(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, fontSize = 14.sp)
    }
}

data class ProviderInfo(
    val name: String,
    val isFree: Boolean,
    val freeInfo: String,
    val url: String
)

val providerInfoList = listOf(
    ProviderInfo(
        name = "OpenCode Zen (Big Pickle)",
        isFree = true,
        freeInfo = "Free model by OpenCode team. 200K context, tool calling, reasoning. No API key needed.",
        url = "https://opencode.ai"
    ),
    ProviderInfo(
        name = "Ollama (Local)",
        isFree = true,
        freeInfo = "100% free. Requires Termux + proot Ubuntu (no native Android app).",
        url = "https://ollama.com"
    ),
    ProviderInfo(
        name = "Google Gemini",
        isFree = true,
        freeInfo = "Free tier: 15 RPM, 1M tokens/day. Get key at aistudio.google.com",
        url = "https://aistudio.google.com/apikey"
    ),
    ProviderInfo(
        name = "Groq",
        isFree = true,
        freeInfo = "Free tier: 30 RPM, fast inference. Get key at console.groq.com",
        url = "https://console.groq.com"
    ),
    ProviderInfo(
        name = "GitHub Models",
        isFree = true,
        freeInfo = "Free with GitHub account. models.inference.ai.azure.com",
        url = "https://models.inference.ai.azure.com"
    ),
    ProviderInfo(
        name = "OpenAI",
        isFree = false,
        freeInfo = "Paid. GPT-4, o1, o3 models.",
        url = "https://platform.openai.com"
    ),
    ProviderInfo(
        name = "Anthropic",
        isFree = false,
        freeInfo = "Paid. Claude models.",
        url = "https://console.anthropic.com"
    ),
    ProviderInfo(
        name = "Custom",
        isFree = false,
        freeInfo = "Any OpenAI-compatible API (e.g. local server).",
        url = ""
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderPage(
    selectedProvider: String,
    onProviderChange: (String) -> Unit,
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    serverUrl: String,
    onServerUrlChange: (String) -> Unit
) {
    val providerNames = providerInfoList.map { it.name }
    var expanded by remember { mutableStateOf(false) }
    val selectedInfo = providerInfoList.find { it.name == selectedProvider }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Configure Provider",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Choose your AI provider",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selectedProvider,
                onValueChange = {},
                readOnly = true,
                label = { Text("AI Provider") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                providerNames.forEach { name ->
                    val info = providerInfoList.find { it.name == name }
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, modifier = Modifier.weight(1f))
                                if (info?.isFree == true) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Text(
                                            "FREE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        onClick = {
                            onProviderChange(name)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        selectedInfo?.let { info ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (info.isFree)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        if (info.isFree) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (info.isFree) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = info.freeInfo,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                        if (info.url.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = info.url,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedProvider != "Ollama (Local)" && selectedProvider != "OpenCode Zen (Big Pickle)") {
            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text("API Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) }
            )
        }

        if (selectedProvider == "Custom") {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrlChange,
                label = { Text("Server URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your API key is stored locally on your device only.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedProvider == "OpenCode Zen (Big Pickle)") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Big Pickle", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary) {
                            Text(" FREE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Model provided by OpenCode Zen team", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Features:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text("\u2022 200K context window", fontSize = 12.sp)
                    Text("\u2022 Tool calling support", fontSize = 12.sp)
                    Text("\u2022 Reasoning capabilities", fontSize = 12.sp)
                    Text("\u2022 Based on DeepSeek v4 Flash", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                        Text("No API key required - just select this provider and start coding!", modifier = Modifier.padding(12.dp), fontSize = 11.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (selectedProvider == "Ollama (Local)") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ollama", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary) {
                            Text(" FREE", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("100% free, runs on device via Termux + proot Ubuntu. No Android APK available.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Setup steps:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("1. Install Termux from F-Droid (not Play Store)", fontSize = 12.sp)
                    Text("2. In Termux: pkg install proot-distro", fontSize = 12.sp)
                    Text("3. proot-distro install ubuntu && proot-distro login ubuntu", fontSize = 12.sp)
                    Text("4. In Ubuntu: curl -fsSL https://ollama.com/install.sh | sh", fontSize = 12.sp)
                    Text("5. ollama serve & then ollama pull qwen2.5-coder:7b", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("No Android APK available. Best models: qwen2.5-coder:7b, codellama:7b", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun ControlsPage() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Mobile Controls",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Touch-optimized coding controls",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                ControlItem(
                    title = "Virtual Keyboard Bar",
                    description = "Ctrl, Alt, Tab, Esc, arrows - always above your keyboard"
                )
                ControlItem(
                    title = "Gesture Navigation",
                    description = "Swipe right for sessions, left for files"
                )
                ControlItem(
                    title = "Split View",
                    description = "Chat on top, code on bottom in landscape"
                )
                ControlItem(
                    title = "Quick Actions",
                    description = "FAB button for common actions"
                )
            }
        }
    }
}

@Composable
fun ControlItem(title: String, description: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(text = title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun TermuxPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Build,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Free Options",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Use OpenCode without paying for API keys",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recommended: Big Pickle (Free)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary) {
                    Text(" EASIEST", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Select \"OpenCode Zen (Big Pickle)\" as provider", fontSize = 12.sp)
                Text("2. No API key needed", fontSize = 12.sp)
                Text("3. Start coding immediately!", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text("200K context, tool calling, reasoning - all free", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Option 2: Termux + Ollama", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "Works on Android via Termux",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("1. Install Termux from F-Droid (not Play Store)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text("2. In Termux, install Ubuntu:", fontSize = 12.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "pkg install proot-distro\nproot-distro install ubuntu\nproot-distro login ubuntu",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("3. Inside Ubuntu, install Ollama:", fontSize = 12.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "curl -fsSL https://ollama.com/install.sh | sh\nollama serve &\nollama pull qwen2.5-coder:7b",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("4. Install Node.js in Termux:", fontSize = 12.sp)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "pkg install nodejs\nnpm i -g opencode-ai\nopencode",
                        modifier = Modifier.padding(12.dp),
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Free, runs 100% on your phone", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Option 3: Free Cloud APIs", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "No install needed",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Just get a free API key:", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("\u2022 Google Gemini: aistudio.google.com/apikey", fontSize = 12.sp)
                Text("   Free: 15 RPM, 1M tokens/day", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Text("\u2022 Groq: console.groq.com", fontSize = 12.sp)
                Text("   Free: 30 RPM, fastest inference", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Text("\u2022 GitHub Models: models.inference.ai.azure.com", fontSize = 12.sp)
                Text("   Free with any GitHub account", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("No credit card required", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Option 4: PC + Phone", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Text(
                        "Best performance",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Run Ollama on your PC, connect from phone:", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("1. Install Ollama on PC: ollama.com/download", fontSize = 12.sp)
                Text("2. Start with: ollama serve --host 0.0.0.0", fontSize = 12.sp)
                Text("3. Find your PC IP: ipconfig (Win) / ifconfig (Mac/Linux)", fontSize = 12.sp)
                Text("4. In this app, select Custom provider", fontSize = 12.sp)
                Text("5. URL: http://YOUR-PC-IP:11434/v1", fontSize = 12.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Run any model, full power of your PC", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Easiest option: Big Pickle (Option 1 above)", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Big Pickle is free, no API key needed. For local models, use Ollama via Termux (Option 2) or connect to a PC (Option 4).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
