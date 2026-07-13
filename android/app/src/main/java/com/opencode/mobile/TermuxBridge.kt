package com.opencode.mobile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log

class TermuxBridge(private val context: Context) {

    companion object {
        private const val TAG = "TermuxBridge"
        private const val TERMUX_PACKAGE = "com.termux"
        private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
        private const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun hasRunCommandPermission(): Boolean {
        return context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
    }

    fun executeCommand(
        command: String,
        arguments: Array<String> = emptyArray(),
        workingDir: String? = null,
        background: Boolean = false,
        sessionAction: Int = 0,
        resultReceiver: BroadcastReceiver? = null
    ): Boolean {
        if (!isTermuxInstalled()) {
            Log.w(TAG, "Termux is not installed")
            return false
        }

        return try {
            val intent = Intent(RUN_COMMAND_ACTION).apply {
                setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.RunCommandService")
                putExtra("$TERMUX_PACKAGE.RUN_COMMAND_PATH", command)
                putExtra("$TERMUX_PACKAGE.RUN_COMMAND_ARGUMENTS", arguments)
                workingDir?.let {
                    putExtra("$TERMUX_PACKAGE.RUN_COMMAND_WORKDIR", it)
                }
                putExtra("$TERMUX_PACKAGE.RUN_COMMAND_BACKGROUND", background)
                putExtra("$TERMUX_PACKAGE.RUN_COMMAND_SESSION_ACTION", sessionAction.toString())
            }

            if (resultReceiver != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    System.currentTimeMillis().toInt(),
                    resultReceiver,
                    PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
                )
                intent.putExtra("$TERMUX_PACKAGE.RUN_COMMAND_PENDING_INTENT", pendingIntent)
            }

            context.startService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute Termux command", e)
            false
        }
    }

    fun startOpenCodeServer(port: Int = 3000, workDir: String? = null): Boolean {
        return executeCommand(
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf(
                "-c",
                "source ~/.bashrc && opencode serve --port $port --hostname 127.0.0.1"
            ),
            workingDir = workDir ?: "/data/data/com.termux/files/home",
            background = true,
            sessionAction = 1
        )
    }

    fun stopOpenCodeServer(): Boolean {
        return executeCommand(
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf("-c", "pkill -f 'opencode serve'"),
            background = true
        )
    }

    fun openTermuxSession(command: String? = null) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.TermuxActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            command?.let {
                putExtra("com.termux.RUN_COMMAND_PATH", it)
            }
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Termux", e)
        }
    }

    fun shareFileToTermux(fileUri: Uri, fileName: String): Boolean {
        val destPath = "/data/data/com.termux/files/home/$fileName"
        return executeCommand(
            command = "/data/data/com.termux/files/usr/bin/cp",
            arguments = arrayOf(fileUri.path ?: return false, destPath),
            background = true
        )
    }
}
