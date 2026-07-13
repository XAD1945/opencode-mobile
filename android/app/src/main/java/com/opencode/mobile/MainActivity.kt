package com.opencode.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
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

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                ConnectScreen()
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
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            navigateToWebView()
        }
    }

    private fun navigateToWebView() {
        val i = Intent(this, WebViewActivity::class.java).apply {
            putExtra("server_url", "http://localhost:3000")
            putExtra("api_key", prefs.getString("api_key", ""))
        }
        startActivity(i)
        finish()
    }
}

@Composable
fun ConnectScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isConnecting by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    var attemptCount by remember { mutableIntStateOf(0) }

    fun checkConnection() {
        scope.launch {
            isConnecting = true
            showError = false
            try {
                val url = URL("http://localhost:3000")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()
                if (code == 200 || code == 304) {
                    isConnected = true
                    showError = false
                } else {
                    showError = true
                }
            } catch (e: Exception) {
                showError = true
            }
            isConnecting = false
        }
    }

    LaunchedEffect(Unit) {
        checkConnection()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Icon(
                imageVector = Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "OpenCode Mobile",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "AI Coding Agent for Android",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (isConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Server Connected!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("OpenCode is running on localhost:3000", fontSize = 12.sp)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        ContextCompat.startActivities(context, arrayOf(Intent(context, WebViewActivity::class.java).apply {
                            putExtra("server_url", "http://localhost:3000")
                        }))
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open OpenCode", fontSize = 16.sp)
                }
            } else if (isConnecting) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text("Checking for OpenCode server...", fontSize = 14.sp)
                if (attemptCount > 0) {
                    Text("Attempt $attemptCount", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                }
            } else if (showError) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Server not found", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "OpenCode server is not running on localhost:3000. You need to install and start it in Termux first.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text("Setup Instructions", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))

                SetupStep("1", "Install Termux", "Download from F-Droid (NOT Play Store)")
                SetupStep("2", "Open Termux and install Node.js", "pkg install nodejs git")
                SetupStep("3", "Install OpenCode", "npm i -g opencode-ai")
                SetupStep("4", "Start OpenCode web server", "opencode web --port 3000 --hostname 127.0.0.1")
                SetupStep("5", "Come back here and tap Retry", "The app will connect automatically")

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Full Termux Commands:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                            Text(
                                text = "pkg install nodejs git\nnpm i -g opencode-ai\nopencode web --port 3000 --hostname 127.0.0.1",
                                modifier = Modifier.padding(12.dp),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("After OpenCode starts:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("\u2022 OpenCode will ask you to select a provider", fontSize = 12.sp)
                        Text("\u2022 Choose \"OpenCode Zen\" for Big Pickle (free)", fontSize = 12.sp)
                        Text("\u2022 Or configure your own API key", fontSize = 12.sp)
                        Text("\u2022 The web UI will work exactly like on desktop", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        attemptCount++
                        checkConnection()
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Connection", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setClassName("com.termux", "com.termux.app.TermuxActivity")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent2 = Intent(Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse("https://f-droid.org/en/packages/com.termux/")
                            }
                            context.startActivity(intent2)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Termux")
                }
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun SetupStep(number: String, title: String, command: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(number, color = MaterialTheme.colorScheme.onPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(command, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
        }
    }
}
