package com.opencode.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class FileShareReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "FileShareReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received action: ${intent.action}")

        when (intent.action) {
            "com.termux.RUN_COMMAND" -> {
                val path = intent.getStringExtra("com.termux.RUN_COMMAND_PATH")
                val args = intent.getStringArrayExtra("com.termux.RUN_COMMAND_ARGUMENTS")
                Log.d(TAG, "Termux command: $path ${args?.joinToString(" ")}")
            }
            Intent.ACTION_SEND -> {
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)

                if (text != null) {
                    val webViewIntent = Intent(context, WebViewActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("shared_text", text)
                        putExtra("shared_subject", subject)
                    }
                    context.startActivity(webViewIntent)
                }
            }
            Intent.ACTION_VIEW -> {
                val data = intent.data
                if (data != null) {
                    val webViewIntent = Intent(context, WebViewActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra("view_uri", data.toString())
                    }
                    context.startActivity(webViewIntent)
                }
            }
        }
    }
}
