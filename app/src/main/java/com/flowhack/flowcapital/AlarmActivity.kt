@file:Suppress("SpellCheckingInspection")

package com.flowhack.flowcapital

import android.app.NotificationManager
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowhack.flowcapital.data.logging.AppLogger
import com.flowhack.flowcapital.data.settings.SettingsManager
import com.flowhack.flowcapital.notifications.AlarmReceiver
import com.flowhack.flowcapital.ui.theme.FlowCapitalTheme

/**
 * Activity будильника, открывается поверх всех окон (включая заблокированный экран).
 * Воспроизводит циклический звуковой сигнал до нажатия кнопки "ВЫКЛЮЧИТЬ".
 *
 * Текст напоминания передаётся через Intent с ключом "alarm_text".
 * Звуковой файл: res/raw/flow_alarm.mp3
 */
class AlarmActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        settingsManager = SettingsManager(this)

        val alarmText = intent.getStringExtra(EXTRA_ALARM_TEXT) ?: "Время напоминания!"
        AppLogger.d("AlarmActivity", "Открытие экрана будильника: $alarmText")

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            setDataSource(applicationContext, android.net.Uri.parse("android.resource://${packageName}/${R.raw.flow_alarm}"))
            isLooping = true
            prepareAsync()
            setOnPreparedListener {
                AppLogger.d("AlarmActivity", "Медиаплеер готов, запуск звука")
                it.start()
            }
        }

        setContent {
            val darkTheme by settingsManager.darkThemeFlow.collectAsState(initial = true)
            FlowCapitalTheme(darkTheme = darkTheme) {
                AlarmScreen(
                    alarmText = alarmText,
                    onDismiss = { dismissAlarm() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AppLogger.d("AlarmActivity", "Получен новый Intent будильника")
        val alarmText = intent.getStringExtra(EXTRA_ALARM_TEXT) ?: return
        if (mediaPlayer?.isPlaying != true) {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(applicationContext, android.net.Uri.parse("android.resource://${packageName}/${R.raw.flow_alarm}"))
                isLooping = true
                prepareAsync()
                setOnPreparedListener { it.start() }
            }
        }
    }

    override fun onDestroy() {
        AppLogger.d("AlarmActivity", "AlarmActivity уничтожена")
        releasePlayer()
        super.onDestroy()
    }

    private fun dismissAlarm() {
        AppLogger.d("AlarmActivity", "Пользователь нажал ВЫКЛЮЧИТЬ")
        cancelNotification()
        releasePlayer()
        finish()
    }

    private fun cancelNotification() {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(AlarmReceiver.ALARM_NOTIFICATION_ID)
        } catch (_: Exception) { }
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    AppLogger.d("AlarmActivity", "Остановка медиаплеера")
                    stop()
                }
                release()
            }
        } catch (_: Exception) {
            // Suppress — плеер уже мог быть освобождён
        }
        mediaPlayer = null
    }

    companion object {
        const val EXTRA_ALARM_TEXT = "alarm_text"
    }
}

/**
 * Экран будильника на весь экран.
 */
@Composable
private fun AlarmScreen(
    alarmText: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = alarmText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text(
                    text = "ВЫКЛЮЧИТЬ",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }
    }
}
