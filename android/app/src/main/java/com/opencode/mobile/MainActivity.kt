package com.opencode.mobile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {

    private lateinit var prefs: SharedPreferences
    private var termuxBridge: TermuxBridge? = null

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
        termuxBridge = TermuxBridge(this)

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                AppScreen()
            }
        }
    }

    private fun navigateToWebView() {
        val i = Intent(this, WebViewActivity::class.java).apply {
            putExtra("server_url", "http://localhost:3000")
        }
        startActivity(i)
        finish()
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val termuxBridge = remember { TermuxBridge(context) }
    val scope = rememberCoroutineScope()
    val handler = remember { Handler(Looper.getMainLooper()) }

    var phase by remember { mutableStateOf("checking") }
    // checking, need_termux, installing, starting, connected, error
    var statusText by remember { mutableStateOf("Checking setup...") }
    var logOutput by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var retryCount by remember { mutableIntStateOf(0) }

    fun appendLog(line: String) {
        logOutput = logOutput + "\n" + line
    }

    fun checkConnection() {
        scope.launch {
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
                    phase = "connected"
                    statusText = "OpenCode is running!"
                } else {
                    isConnected = false
                }
            } catch (e: Exception) {
                isConnected = false
            }
        }
    }

    fun startServer() {
        phase = "starting"
        statusText = "Starting OpenCode server..."
        appendLog("Starting opencode web server...")

        termuxBridge.startOpenCodeServer()
        appendLog("Server command sent to Termux")

        retryCount = 0
        handler.postDelayed(object : Runnable {
            override fun run() {
                checkConnection()
                if (!isConnected && retryCount < 30) {
                    retryCount++
                    statusText = "Waiting for server... ($retryCount/30)"
                    handler.postDelayed(this, 2000)
                } else if (!isConnected) {
                    phase = "error"
                    statusText = "Server did not start. Open Termux to check."
                }
            }
        }, 3000)
    }

    fun runSetup() {
        phase = "installing"
        statusText = "Installing OpenCode in Termux..."
        appendLog("Running setup script in Termux...")

        val setupScript = """
            pkg install -y nodejs git 2>&1
            npm i -g opencode-ai 2>&1
            echo "SETUP_COMPLETE"
        """.trimIndent()

        termuxBridge.executeCommand(
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf("-c", setupScript),
            background = true,
            sessionAction = 1
        )

        appendLog("Setup commands sent. Waiting 60 seconds for install...")
        statusText = "Installing packages (may take a minute)..."

        handler.postDelayed({
            val prefs = context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("setup_complete", true).apply()
            appendLog("Setup marked as complete")
            startServer()
        }, 60000)
    }

    fun checkAndStart() {
        val setupDone = context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_complete", false)

        if (!termuxBridge.isTermuxInstalled()) {
            phase = "need_termux"
            statusText = "Termux is required"
            return
        }

        if (!setupDone) {
            runSetup()
        } else {
            startServer()
        }
    }

    LaunchedEffect(Unit) {
        checkAndStart()
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
            Spacer(modifier = Modifier.height(12.dp))
            Text("OpenCode", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("AI Coding Agent", fontSize = 14.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))

            Spacer(modifier = Modifier.height(32.dp))

            when (phase) {
                "connected" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Ready!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("OpenCode is running", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val intent = Intent(context, WebViewActivity::class.java).apply {
                                putExtra("server_url", "http://localhost:3000")
                            }
                            context.startActivity(intent)
                            (context as? ComponentActivity)?.finish()
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Coding", fontSize = 16.sp)
                    }
                }

                "need_termux" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Termux Required", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("OpenCode needs Termux to run on Android.", fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Steps:", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("1. Install Termux from F-Droid", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("   (NOT Play Store - that version is broken)", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("2. Come back to this app", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("   It will install everything automatically", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://f-droid.org/en/packages/com.termux/"))
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Download Termux from F-Droid")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            phase = "checking"
                            statusText = "Checking again..."
                            checkAndStart()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("I installed Termux - Check again")
                    }
                }

                "installing", "starting" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(statusText, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                            if (logOutput.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text(
                                        text = logOutput.trim(),
                                        modifier = Modifier.padding(8.dp),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 15
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (phase == "installing") {
                        Text(
                            "This takes about 1 minute. Don't close the app.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                "error" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Server didn't start", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(statusText, fontSize = 13.sp)
                            if (logOutput.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text(
                                        text = logOutput.trim(),
                                        modifier = Modifier.padding(8.dp),
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 15
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            phase = "checking"
                            logOutput = ""
                            checkAndStart()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
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
                                // Termux not installed
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Open Termux")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val prefs = context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("setup_complete", false).apply()
                            phase = "checking"
                            logOutput = ""
                            checkAndStart()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset & Reinstall")
                    }
                }

                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(statusText, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (phase == "connected") {
                Text(
                    "OpenCode runs locally on your device",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        }
    }
}
