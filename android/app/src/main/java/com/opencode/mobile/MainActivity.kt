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
            // permissions granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE)
        requestPermissions()

        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                AppScreen()
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
        if (permissions.isNotEmpty()) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@Composable
fun AppScreen() {
    val context = LocalContext.current
    val termuxBridge = remember { TermuxBridge(context) }
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE) }

    var step by remember { mutableIntStateOf(0) }
    // 0=checking, 1=need_termux, 2=need_permission, 3=installing, 4=starting, 5=ready, 6=error
    var statusText by remember { mutableStateOf("Checking...") }
    var logLines by remember { mutableStateOf(listOf<String>()) }

    fun log(msg: String) {
        logLines = logLines + msg
    }

    fun checkServer() {
        scope.launch {
            try {
                val url = URL("http://localhost:3000")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..399) {
                    step = 5
                    statusText = "Ready!"
                }
            } catch (_: Exception) {}
        }
    }

    fun pollServer() {
        scope.launch {
            for (attempt in 1..40) {
                delay(2000)
                statusText = "Waiting for server... ($attempt/40)"
                try {
                    val url = URL("http://localhost:3000")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.connect()
                    val code = conn.responseCode
                    conn.disconnect()
                    if (code in 200..399) {
                        step = 5
                        statusText = "Ready!"
                        return@launch
                    }
                } catch (_: Exception) {}
            }
            step = 6
            statusText = "Server did not start"
            log("Server failed to respond after 40 attempts")
        }
    }

    fun startServer() {
        step = 4
        statusText = "Starting OpenCode..."
        log("Sending opencode web command to Termux...")

        val started = termuxBridge.startOpenCodeServer()
        if (started) {
            log("Command sent successfully")
            pollServer()
        } else {
            step = 6
            statusText = "Failed to start server"
            log("Could not send command to Termux")
        }
    }

    fun doSetup() {
        step = 3
        statusText = "Installing Node.js + OpenCode..."
        log("Step 1: Installing packages...")

        val installCmd = "pkg install -y nodejs git"
        val npmCmd = "npm i -g opencode-ai"

        val fullCmd = """
            $installCmd 2>&1 | tail -5
            echo "---NODE INSTALLED---"
            $npmCmd 2>&1 | tail -5
            echo "---NPM INSTALLED---"
            echo "SETUP_DONE"
        """.trimIndent()

        termuxBridge.executeCommand(
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf("-c", fullCmd),
            background = true,
            sessionAction = 1
        )

        log("Install commands sent. Waiting ~90 seconds...")
        statusText = "Installing... (this takes ~1 min)"

        scope.launch {
            delay(90000)
            context.getSharedPreferences("opencode_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("setup_complete", true).apply()
            log("Setup marked complete")
            startServer()
        }
    }

    fun begin() {
        if (!termuxBridge.isTermuxInstalled()) {
            step = 1
            statusText = "Install Termux"
            return
        }
        log("Termux detected")

        if (!termuxBridge.hasRunCommandPermission()) {
            step = 2
            statusText = "Enable Termux permission"
            log("Need to enable 'Allow external apps' in Termux")
            return
        }
        log("Termux permission OK")

        if (!prefs.getBoolean("setup_complete", false)) {
            doSetup()
        } else {
            log("OpenCode already installed")
            startServer()
        }
    }

    LaunchedEffect(Unit) {
        begin()
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(50.dp))

            Icon(
                Icons.Default.Code,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text("OpenCode", fontSize = 26.sp, fontWeight = FontWeight.Bold)

            Spacer(modifier = Modifier.height(32.dp))

            // Step indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val steps = listOf("Termux", "Permission", "Install", "Start", "Run")
                val activeIndex = when (step) {
                    1 -> 0; 2 -> 1; 3 -> 2; 4 -> 3; 5 -> 4; else -> -1
                }
                steps.forEachIndexed { i, label ->
                    val isActive = i <= activeIndex
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                    ) {}
                    if (i < steps.lastIndex) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            when (step) {
                0 -> {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp), strokeWidth = 3.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(statusText, fontSize = 14.sp)
                }

                1 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Termux not found", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("OpenCode needs Termux to run on Android.", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("1. Download and install Termux from F-Droid",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth())
                    Text("   (Play Store version is broken)",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("2. Open Termux once, then come back here",
                        fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.fillMaxWidth())

                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://f-droid.org/en/packages/com.termux/")))
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Download Termux") }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            log("Checking for Termux...")
                            begin()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Termux installed - Continue") }
                }

                2 -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Permission needed", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Termux needs permission to receive commands from this app.", fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("In Termux, go to:", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            Text("Settings > Allow external apps > Enable", fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Then restart Termux and come back.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setClassName("com.termux", "com.termux.app.TermuxActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Open Termux") }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { begin() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Permission enabled - Continue") }
                }

                3 -> {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(statusText, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("This only happens once. Takes about 1 minute.",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            if (logLines.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        logLines.takeLast(8).forEach { line ->
                                            Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                4 -> {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(statusText, fontWeight = FontWeight.Medium)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Starting web server on localhost:3000",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setClassName("com.termux", "com.termux.app.TermuxActivity")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Open Termux") }
                }

                5 -> {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("OpenCode is running!", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Tap below to start coding", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val i = Intent(context, WebViewActivity::class.java).apply {
                                putExtra("server_url", "http://localhost:3000")
                            }
                            context.startActivity(i)
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

                6 -> {
                    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Error", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(statusText, fontSize = 13.sp)
                            if (logLines.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        logLines.takeLast(10).forEach { line ->
                                            Text(line, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            logLines = emptyList()
                            step = 0
                            begin()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Retry") }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            prefs.edit().putBoolean("setup_complete", false).apply()
                            logLines = emptyList()
                            step = 0
                            begin()
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Reset everything") }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (logLines.isNotEmpty() && step !in listOf(3, 6)) {
                OutlinedButton(
                    onClick = {
                        logLines = emptyList()
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("Clear log", fontSize = 11.sp) }
            }
        }
    }
}
