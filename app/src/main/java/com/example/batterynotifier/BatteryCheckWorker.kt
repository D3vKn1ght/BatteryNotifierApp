package com.example.batterynotifier

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaPlayer
import android.os.BatteryManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class BatteryCheckWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val batteryLevel = getBatteryLevel(appContext)

        if (batteryLevel in 1..19) {
            // 1. Phát âm thanh
            playSound()
            // 2. Notification
            showNotification(batteryLevel)
            // 3. Gửi Telegram
            sendTelegramMessage("Pin chỉ còn $batteryLevel%. Hãy cắm sạc nhé!")
        }

        return Result.success()
    }

    private fun getBatteryLevel(context: Context): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, ifilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
    }

    private fun playSound() {
        try {
            val mp = MediaPlayer.create(appContext, R.raw.battery_alert)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotification(batteryLevel: Int) {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Pin yếu ($batteryLevel%)")
            .setContentText("Đề nghị bạn cắm sạc.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // hiện trên màn khóa
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, notification)
    }

    private suspend fun sendTelegramMessage(message: String) {
        val pref = appContext.getSharedPreferences("config", Context.MODE_PRIVATE)
        val token = pref.getString("telegram_token", null)
        val chatId = pref.getString("telegram_chat_id", null)

        if (token.isNullOrEmpty() || chatId.isNullOrEmpty()) {
            return
        }

        val text = java.net.URLEncoder.encode(message, "UTF-8")
        val urlString =
            "https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$text"

        withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val responseCode = conn.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    reader.readText()
                    reader.close()
                }

                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
