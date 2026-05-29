package com.edge.llm.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * BootReceiver: Automatically starts our LlmServerService when the Android device powers on.
 * Highly essential for dedicated server profiles to recover from physical power-offs or reboots.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            ServerConsole.log("System BOOT_COMPLETED received. Initiating auto-launch for server daemon...")
            
            val serviceIntent = Intent(context, LlmServerService::class.java)
            
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
                ServerConsole.log("Auto-launch start command dispatched successfully.")
            } catch (e: Exception) {
                ServerConsole.log("Failed to auto-start service on boot: ${e.message}")
            }
        }
    }
}
