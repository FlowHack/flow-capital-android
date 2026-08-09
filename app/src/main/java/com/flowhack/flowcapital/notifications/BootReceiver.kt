package com.flowhack.flowcapital.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            AppLogger.d("BootReceiver", "Устройство загружено или приложение обновлено — восстановление напоминаний")
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settingsManager = SettingsManager(context.applicationContext)
                    rescheduleSavedReminders(context.applicationContext, settingsManager)
                    AppLogger.d("BootReceiver", "Напоминания восстановлены")
                } catch (e: Exception) {
                    AppLogger.e("BootReceiver", "Ошибка восстановления: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
