package com.example.lab_week_08

import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*

class NotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val channelId = "countdown_channel"
    private val completionChannelId = "completion_channel"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        createCompletionChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(EXTRA_ID)
        if (id != null) {
            serviceScope.launch { startCountdown(id) }
        }
        return START_NOT_STICKY
    }

    private suspend fun startCountdown(id: String) {
        val notificationManager = NotificationManagerCompat.from(this)
        val notifId = getNotifId(id)

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Worker Task $id Running")
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)

        // Foreground only for the first task
        if (id == "001") {
            startForeground(notifId, builder.setContentText("Starting countdown...").build())
        } else {
            notificationManager.notify(notifId, builder.setContentText("Starting countdown...").build())
        }

        // Countdown
        for (i in 5 downTo 0) {
            builder.setContentText("Task $id: $i seconds remaining...")
            if (notificationsAllowed()) notificationManager.notify(notifId, builder.build())
            delay(1000)
        }

        // Completion
        builder.setContentTitle("Worker Task $id Completed")
            .setContentText("Task $id finished successfully ✅")
            .setOngoing(false)

        if (notificationsAllowed()) notificationManager.notify(notifId, builder.build())

        // Show popup
        showCompletionNotification(id)

        // LiveData event
        withContext(Dispatchers.Main) { mutableID.value = id }

        // Stop foreground safely
        if (id == "001") stopForeground(STOP_FOREGROUND_REMOVE)

        delay(500)
        stopSelf()
    }

    private fun getNotifId(id: String): Int = when (id) {
        "001" -> 1001
        "002" -> 1002
        else -> (System.currentTimeMillis() % 100000).toInt()
    }

    private fun notificationsAllowed(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled() ||
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    this.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                            PackageManager.PERMISSION_GRANTED
                else true
    }

    private fun showCompletionNotification(id: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE
        )

        val completionNotification = NotificationCompat.Builder(this, completionChannelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Countdown Complete!")
            .setContentText("Worker Task $id has finished successfully ✅")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = NotificationManagerCompat.from(this)
        if (notificationsAllowed()) manager.notify(getNotifId(id), completionNotification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Worker Countdown",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of worker tasks"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun createCompletionChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                completionChannelId,
                "Worker Completion",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when a worker task completes"
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val EXTRA_ID = "Id"
        private val mutableID = MutableLiveData<String>()
        val trackingCompletion: LiveData<String> = mutableID
    }
}
