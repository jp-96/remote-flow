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

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.remoteflowlib.RemoteSharedFlow
import com.example.remoteflowlib.remoteSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

class MainService : Service() {

    companion object {
        const val SERVICE_PACKAGE = "com.example.remoteflow"
        const val SERVICE_NAME ="$SERVICE_PACKAGE.MainService"
    }

    private lateinit var remoteSharedFlow: RemoteSharedFlow<String>
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override fun onCreate() {
        Timber.d("onCreate")
        super.onCreate()
        remoteSharedFlow = remoteSharedFlow()
        coroutineScope.launch {
            remoteSharedFlow.flow().collect {
                Timber.d("Received: $it")
                remoteSharedFlow.emit("Received: $it")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("onBind")
        return remoteSharedFlow.asBinder()
    }

    override fun onDestroy() {
        Timber.d("onDestroy")
        super.onDestroy()
    }
}
