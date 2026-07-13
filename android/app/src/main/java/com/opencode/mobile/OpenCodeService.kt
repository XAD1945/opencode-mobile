package com.opencode.mobile

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class OpenCodeService : Service() {

    companion object {
        private const val TAG = "OpenCodeService"
        private const val DEFAULT_PORT = 3000
        private const val MAX_RETRIES = 30
        private const val RETRY_DELAY_MS = 1000L

        fun start(context: Context, port: Int = DEFAULT_PORT) {
            val intent = Intent(context, OpenCodeService::class.java).apply {
                putExtra("port", port)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OpenCodeService::class.java))
        }
    }

    private val binder = LocalBinder()
    private var serverProcess: Process? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverPort = DEFAULT_PORT
    private var isRunning = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    inner class LocalBinder : Binder() {
        fun getService(): OpenCodeService = this@OpenCodeService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        serverPort = intent?.getIntExtra("port", DEFAULT_PORT) ?: DEFAULT_PORT
        startForeground(1, createNotification())
        startServer()
        return START_STICKY
    }

    private fun startServer() {
        scope.launch {
            try {
                val processBuilder = ProcessBuilder(
                    "node",
                    "${filesDir.absolutePath}/opencode-server.js",
                    "--port", serverPort.toString()
                )
                processBuilder.environment()["NODE_ENV"] = "production"
                processBuilder.directory(filesDir)
                processBuilder.redirectErrorStream(true)

                serverProcess = processBuilder.start()

                val reader = BufferedReader(InputStreamReader(serverProcess!!.inputStream))
                scope.launch {
                    reader.lineSequence().forEach { line ->
                        Log.d(TAG, "Server: $line")
                        if (line.contains("listening") || line.contains("started")) {
                            isRunning = true
                        }
                    }
                }

                waitForServer()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start server", e)
                stopSelf()
            }
        }
    }

    private suspend fun waitForServer() {
        repeat(MAX_RETRIES) {
            delay(RETRY_DELAY_MS)
            if (isServerReachable()) {
                Log.i(TAG, "Server started on port $serverPort")
                return
            }
        }
        Log.w(TAG, "Server may not be ready, continuing anyway")
    }

    fun isServerReachable(): Boolean {
        return try {
            val request = Request.Builder()
                .url("http://127.0.0.1:$serverPort/health")
                .get()
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    fun getServerUrl(): String = "http://127.0.0.1:$serverPort"
    fun getPort(): Int = serverPort

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, OpenCodeService::class.java).apply {
            action = "STOP"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, OpenCodeApp.CHANNEL_ID)
            .setContentTitle("OpenCode Server")
            .setContentText("Running on port $serverPort")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "Stop", stopPendingIntent
                ).build()
            )
            .build()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "OpenCode::ServerWakeLock"
        ).apply {
            acquire(60 * 60 * 1000L) // 1 hour max
        }
    }

    override fun onDestroy() {
        serverProcess?.destroy()
        scope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        super.onDestroy()
    }
}
