/*
 * Copyright (c) 2024.
 * This file is part of RemoteFlow.
 *
 * RemoteFlow is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * RemoteFlow is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with RemoteFlow. If not, see <https://www.gnu.org/licenses/>.
 */

package com.example.remoteflow

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.example.remoteflow.R
import com.example.remoteflow.MainServiceCompanion.ACTION_START
import com.example.remoteflow.MainServiceCompanion.ACTION_STOP
import com.example.remoteflow.MainServiceCompanion.foregroundServiceStateFlow
import com.example.remoteflowlib.RemoteSharedFlow
import com.example.remoteflowlib.foregroundServiceManager
import com.example.remoteflowlib.remoteSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

object MainServiceCompanion {

    private const val SERVICE_PACKAGE = "com.example.remoteflow"
    private const val SERVICE_NAME = "$SERVICE_PACKAGE.MainService"

    fun remoteServiceSharedFlow(context: Context): RemoteSharedFlow<String> {
        return remoteSharedFlow(context, SERVICE_PACKAGE, SERVICE_NAME)
    }

    const val ACTION_START = "ACTION_START"
    const val ACTION_STOP = "ACTION_STOP"

    /**
     * AndroidManifest.xml <service/>
     */
    private fun sendAction(context: Context, action: String) {
        Intent(context, MainService::class.java).also {
            it.action = action
            context.startService(it)
        }
    }

    fun sendActionStart(context: Context) {
        sendAction(context, ACTION_START)
    }

    fun sendActionStop(context: Context) {
        sendAction(context, ACTION_STOP)
    }

    val foregroundServiceStateFlow = MutableStateFlow(false)

}

class MainService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "in_foreground_channel"
        private const val NOTIFICATION_CHANNEL_NAME = "in foreground"
    }

    private lateinit var remoteSharedFlow: RemoteSharedFlow<String>
    private var remoteSharedFlowJob: Job? = null

    private val isActiveRemoteSharedFlow: Boolean
        get() = remoteSharedFlowJob?.isActive == true

    private fun startRemoteSharedFlow() {
        if (isActiveRemoteSharedFlow) return
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        remoteSharedFlowJob = coroutineScope.launch {
            remoteSharedFlow.flow().collect {
                Timber.d("Received: $it")
                when (it) {
                    "start" -> startForegroundService()
                    "stop" -> stopForegroundService()
                    else -> remoteSharedFlow.emit("Received: $it")
                }
            }
        }
    }

    private fun stopRemoteSharedFlow() {
        remoteSharedFlowJob?.cancel()
        remoteSharedFlowJob = null
    }

    private var foregroundJob: Job? = null

    private val foregroundServiceManager = foregroundServiceManager(
        NOTIFICATION_ID,
        NOTIFICATION_CHANNEL_ID,
        NOTIFICATION_CHANNEL_NAME
    )

    private val isActiveForegroundService: Boolean
        get() = foregroundJob?.isActive == true

    private fun startForegroundService() {
        foregroundServiceStateFlow.value = true
        if (isActiveForegroundService) return
        foregroundServiceManager.start(this) { notification ->
            // modify notification
            val mainActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                // note: AndroidManifest.xml - android:launchMode="singleTop"
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT
            )
            notification
                .setContentTitle("In Service ...")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(mainActivityPendingIntent)
                .setOngoing(true)
        }
        val coroutineScope = CoroutineScope(Dispatchers.Default)
        foregroundJob = coroutineScope.launch {
            var counter = 0
            while (true) {
                remoteSharedFlow.emit((++counter).toString())
                Timber.d("foregroundJob: $counter")
                delay(1000)
            }
        }
    }

    private fun stopForegroundService() {
        foregroundJob?.cancel()
        foregroundJob = null
        foregroundServiceManager.stop(this)
        foregroundServiceStateFlow.value = false
    }

    override fun onCreate() {
        Timber.d("onCreate")
        super.onCreate()
        remoteSharedFlow = remoteSharedFlow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand $intent")
        intent?.let {
            when (it.action) {
                ACTION_START -> {
                    startRemoteSharedFlow()
                }
                ACTION_STOP -> {
                    stopRemoteSharedFlow()
                    if (!isActiveForegroundService) {
                        stopSelf()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("onBind")
        return remoteSharedFlow.asBinder()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Timber.d("onTaskRemoved")
        super.onTaskRemoved(rootIntent)
        // stop
        stopForegroundService()
        stopRemoteSharedFlow()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
        // stop
        stopForegroundService()
        stopRemoteSharedFlow()
    }
}
