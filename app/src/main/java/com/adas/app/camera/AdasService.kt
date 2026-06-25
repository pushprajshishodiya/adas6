package com.adas.app.camera

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class AdasService : Service() {
    companion object {
        const val CHANNEL_ID = "adas_v2"
        const val NOTIF_ID = 1
    }
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "ADAS Running", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        startForeground(NOTIF_ID, NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ADAS Active")
            .setContentText("Monitoring traffic — front & rear")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true).build())
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
}
