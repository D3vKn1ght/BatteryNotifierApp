package com.example.batterynotifier

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var edtToken: EditText
    private lateinit var edtChatId: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        edtToken = findViewById(R.id.edtToken)
        edtChatId = findViewById(R.id.edtChatId)
        val btnSave: Button = findViewById(R.id.btnSave)

        val pref = getSharedPreferences("config", MODE_PRIVATE)
        edtToken.setText(pref.getString("telegram_token", ""))
        edtChatId.setText(pref.getString("telegram_chat_id", ""))

        btnSave.setOnClickListener {
            pref.edit()
                .putString("telegram_token", edtToken.text.toString())
                .putString("telegram_chat_id", edtChatId.text.toString())
                .apply()
            Toast.makeText(this, "Đã lưu cấu hình", Toast.LENGTH_SHORT).show()
            scheduleBatteryCheck()
        }

        scheduleBatteryCheck()
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
}
