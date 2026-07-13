package com.opencode.mobile

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
    }

    fun isTermuxInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hasRunCommandPermission(): Boolean {
        return try {
            val perm = "$TERMUX_PACKAGE.permission.RUN_COMMAND"
            context.checkPermission(perm, android.os.Process.myPid(), android.os.Process.myUid()) ==
                PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            // Can't check - assume granted and let it fail later if not
            true
        }
    }

    fun executeCommand(
        command: String,
        arguments: Array<String> = emptyArray(),
        workingDir: String? = null,
        background: Boolean = false,
        sessionAction: Int = 0
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
            context.startService(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: ${e.message}", e)
            false
        }
    }

    fun startOpenCodeServer(port: Int = 3000): Boolean {
        val cmd = "source ~/.bashrc 2>/dev/null; cd ~; opencode web --port $port --hostname 127.0.0.1"
        Log.d(TAG, "Starting server: $cmd")
        return executeCommand(
            command = "/data/data/com.termux/files/usr/bin/bash",
            arguments = arrayOf("-c", cmd),
            workingDir = "/data/data/com.termux/files/home",
            background = true,
            sessionAction = 1
        )
    }

    fun openTermux() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setClassName(TERMUX_PACKAGE, "$TERMUX_PACKAGE.app.TermuxActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Termux", e)
        }
    }
}
