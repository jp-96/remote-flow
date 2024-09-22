package com.example.remoteflowlib

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.Service.STOP_FOREGROUND_REMOVE
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

interface ForegroundServiceManager {
    fun start(service: Service, builder: (notification: NotificationCompat.Builder) -> Unit)
    fun stop(service: Service)
}

fun foregroundServiceManager(
    notificationId: Int? = null,
    channelId: String? = null,
    channelName: String? = null,
    channelImportance: Int = NotificationManager.IMPORTANCE_LOW,
): ForegroundServiceManager {
    return ForegroundServiceManagerImpl(notificationId, channelId, channelName, channelImportance)
}

class ForegroundServiceManagerImpl(
    private val notificationId: Int?,
    private val channelId: String?,
    private val channelName: String?,
    private val channelImportance: Int,
) : ForegroundServiceManager {

    companion object {
        const val DEFAULT_NOTIFICATION_ID = 1
        const val DEFAULT_NOTIFICATION_CHANNEL_ID = "in_service_channel"
        const val DEFAULT_NOTIFICATION_CHANNEL_NAME = "in service"
    }

    override fun start(
        service: Service,
        builder: (notification: NotificationCompat.Builder) -> Unit
    ) {
        // notification
        val notification = NotificationCompat.Builder(
            service,
            channelId ?: DEFAULT_NOTIFICATION_CHANNEL_ID
        )
//            .setContentTitle("In Session ...")
//            .setSmallIcon(R.drawable.ic_launcher_foreground)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId ?: DEFAULT_NOTIFICATION_CHANNEL_ID,
                    channelName ?: DEFAULT_NOTIFICATION_CHANNEL_NAME,
                    channelImportance,
                )
            )
        }

        // callback - notification
        builder(notification)

        // start foreground
        service.startForeground(notificationId ?: DEFAULT_NOTIFICATION_ID, notification.build())
    }

    override fun stop(service: Service) {
        val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        notificationManager.cancel(notificationId ?: DEFAULT_NOTIFICATION_ID)
        service.stopForeground(STOP_FOREGROUND_REMOVE)
    }

}