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

package com.example.remoteflowlib

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.net.BindException
import java.util.concurrent.atomic.AtomicReference

interface RemoteSharedFlow<T> {
    fun asBinder(): IBinder
    fun emit(value: T): Job
    fun flow(): SharedFlow<T>
    fun unbindService()
}

fun <T> remoteSharedFlow(
    context: Context? = null,
    servicePackage: String? = null,
    serviceName: String? = null
): RemoteSharedFlowImpl<T> {
    return RemoteSharedFlowImpl(context, servicePackage, serviceName)
}

class RemoteSharedFlowImpl<T>(
    private val context: Context? = null,
    servicePackage: String? = null,
    serviceName: String? = null
) : Handler.Callback, RemoteSharedFlow<T> {

    private val remoteMessengers = AtomicReference<Messenger>()
    private var serviceConnectionBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(component: ComponentName?, binder: IBinder?) {
            binder?.let { messenger ->
                val remoteMessenger = Messenger(messenger)
                remoteMessengers.set(remoteMessenger)
                remoteMessenger.send(
                    Message().apply {
                        obj = localMessenger
                    }
                )
            }
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            TODO("Not yet implemented")
        }
    }
    private val handler = Handler(Looper.getMainLooper(), this)
    private val localMessenger = Messenger(handler)
    private val internalSharedFlow = MutableSharedFlow<T>()
    private val readFlow = internalSharedFlow.asSharedFlow()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    init {
        context?.let {
            if (servicePackage != null &&
                serviceName != null
            ) {
                val intent = Intent().apply {
                    component = ComponentName(servicePackage, serviceName)
                }
                if (!it.bindService(intent, serviceConnection, Service.BIND_AUTO_CREATE)) {
                    it.unbindService(serviceConnection)
                    throw BindException()
                }
                serviceConnectionBound = true
            }
        }
    }

    override fun emit(value: T): Job = coroutineScope.launch {
        remoteMessengers.get()?.let { messenger ->
            val message = Message().apply {
                obj = value
            }
            messenger.send(message)
        }
    }

    override fun asBinder(): IBinder = localMessenger.binder

    override fun flow() = readFlow

    override fun unbindService() {
        if (serviceConnectionBound) {
            serviceConnectionBound = false
            context?.unbindService(serviceConnection)
        }
    }

    override fun handleMessage(msg: Message): Boolean {
        when (msg.obj) {
            is Messenger -> {
                remoteMessengers.set(msg.obj as Messenger)
            }

            else -> {
                @Suppress("UNCHECKED_CAST")
                val remoteSharedFlowValue = msg.obj as? T
                if (remoteSharedFlowValue != null) {
                    coroutineScope.launch {
                        internalSharedFlow.emit(remoteSharedFlowValue)
                    }
                }
            }
        }
        return true
    }

}
