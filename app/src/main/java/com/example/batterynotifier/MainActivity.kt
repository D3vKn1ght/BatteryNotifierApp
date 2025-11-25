package com.example.batterynotifier

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.media.MediaPlayer
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var edtToken: EditText
    private lateinit var edtChatId: EditText
    private lateinit var txtBattery: TextView
    private lateinit var btnSave: Button
    private lateinit var btnTest: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Ánh xạ view
        txtBattery = findViewById(R.id.txtBattery)
        edtToken = findViewById(R.id.edtToken)
        edtChatId = findViewById(R.id.edtChatId)
        btnSave = findViewById(R.id.btnSave)
        btnTest = findViewById(R.id.btnTest)

        // Load cấu hình đã lưu (nếu có)
        val pref = getSharedPreferences("config", MODE_PRIVATE)
        edtToken.setText(pref.getString("telegram_token", ""))
        edtChatId.setText(pref.getString("telegram_chat_id", ""))

        // Cập nhật % pin lúc mở app
        updateBatteryLabel()

        // Nút Lưu cấu hình
        btnSave.setOnClickListener {
            saveConfig()
            scheduleBatteryCheck()
            Toast.makeText(this, "Đã lưu cấu hình & bật kiểm tra pin nền", Toast.LENGTH_SHORT).show()
        }

        // Nút Test: gửi tin nhắn Telegram + phát âm thanh
        btnTest.setOnClickListener {
            val token = edtToken.text.toString().trim()
            val chatId = edtChatId.text.toString().trim()

            if (token.isEmpty() || chatId.isEmpty()) {
                Toast.makeText(this, "Vui lòng nhập Telegram Bot Token và Chat ID trước.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Lưu lại cấu hình hiện tại
            saveConfig()
            scheduleBatteryCheck()

            // Phát thử âm thanh
            playTestSound()

            // Gửi tin nhắn test trên thread riêng
            Thread {
                sendTelegramTest(token, chatId)
            }.start()
        }

        // Đảm bảo WorkManager đã được schedule
        scheduleBatteryCheck()
    }

    override fun onResume() {
        super.onResume()
        // Mỗi lần quay lại app thì cập nhật lại % pin
        updateBatteryLabel()
    }

    private fun saveConfig() {
        val pref = getSharedPreferences("config", MODE_PRIVATE)
        pref.edit()
            .putString("telegram_token", edtToken.text.toString().trim())
            .putString("telegram_chat_id", edtChatId.text.toString().trim())
            .apply()
    }

    private fun scheduleBatteryCheck() {
        val workRequest =
            PeriodicWorkRequestBuilder<BatteryCheckWorker>(15, TimeUnit.MINUTES).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "battery_check_work",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // Cập nhật TextView hiển thị % pin
    private fun updateBatteryLabel() {
        val level = getBatteryLevel()
        if (level >= 0) {
            txtBattery.text = "$level%"
        } else {
            txtBattery.text = "--%"
        }
    }

    // Đọc % pin hiện tại
    private fun getBatteryLevel(): Int {
        val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = registerReceiver(null, ifilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

        return if (level >= 0 && scale > 0) {
            (level * 100 / scale.toFloat()).toInt()
        } else {
            -1
        }
    }

    // Phát âm thanh test dùng file battery_alert.mp3
    private fun playTestSound() {
        try {
            val mp = MediaPlayer.create(this, R.raw.battery_alert)
            mp.setOnCompletionListener { it.release() }
            mp.start()
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Không phát được âm thanh test", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Gửi tin nhắn test Telegram
    private fun sendTelegramTest(token: String, chatId: String) {
        try {
            val message = "🔋 Tin nhắn test: App nhắc sạc pin đang hoạt động!"
            val text = java.net.URLEncoder.encode(message, "UTF-8")
            val urlString =
                "https://api.telegram.org/bot$token/sendMessage?chat_id=$chatId&text=$text"

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
            } else {
                // Có thể log lại nếu muốn
            }

            conn.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread {
                Toast.makeText(this, "Gửi tin nhắn test thất bại", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
