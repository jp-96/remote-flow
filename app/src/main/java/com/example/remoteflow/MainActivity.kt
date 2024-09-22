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

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.remoteflow.MainServiceCompanion.remoteServiceSharedFlow
import com.example.remoteflow.MainServiceCompanion.sendActionStart
import com.example.remoteflow.MainServiceCompanion.sendActionStop
import com.example.remoteflow.theme.RemoterFlowTheme
import com.example.remoteflowlib.RemoteSharedFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : ComponentActivity() {

    init {
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private lateinit var remoteSharedFlow: RemoteSharedFlow<String>
    private lateinit var coroutineScope: CoroutineScope

    private var countUp by mutableIntStateOf(0)
    private var response1 by mutableStateOf("")
    private var response2 by mutableStateOf("")

    private var isServiceForegroundActive by mutableStateOf<Boolean?>(null)

    private var serviceCountUp by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate")
        super.onCreate(savedInstanceState)
        setContent {
            RemoterFlowTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        Spacer(Modifier.size(8.dp))
                        Row {
                            Button(
                                onClick = {
                                    isServiceForegroundActive = null
                                    coroutineScope.launch {
                                        remoteSharedFlow.emit("start")
                                    }
                                },
                                // Uses ButtonDefaults.ContentPadding by default
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    top = 12.dp,
                                    end = 20.dp,
                                    bottom = 12.dp
                                ),
                                enabled = (isServiceForegroundActive == false)
                            ) {
                                // Inner content including an icon and a text label
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "start",
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("START")
                            }
                            Spacer(Modifier.size(8.dp))
                            Button(
                                onClick = {
                                    isServiceForegroundActive = null
                                    coroutineScope.launch {
                                        remoteSharedFlow.emit("stop")
                                    }
                                },
                                // Uses ButtonDefaults.ContentPadding by default
                                contentPadding = PaddingValues(
                                    start = 20.dp,
                                    top = 12.dp,
                                    end = 20.dp,
                                    bottom = 12.dp
                                ),
                                enabled = (isServiceForegroundActive == true)
                            ) {
                                // Inner content including an icon and a text label
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "stop",
                                    modifier = Modifier.size(ButtonDefaults.IconSize)
                                )
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("STOP")
                            }
                        }
                        Greeting("Count", countUp.toString())
                        Greeting("1", response1)
                        Greeting("2", response2)
                        Greeting("Service:", serviceCountUp.toString())
                    }
                }
            }
        }
    }

    override fun onResume() {
        Timber.d("onResume")
        super.onResume()

        // service - start service
        sendActionStart(this)

        // service - bind service
        remoteSharedFlow = remoteServiceSharedFlow(this)

        coroutineScope = CoroutineScope(kotlinx.coroutines.Dispatchers.Default)

        coroutineScope.launch {
            MainServiceCompanion.foregroundServiceStateFlow.collectLatest {
                isServiceForegroundActive = it
            }
        }

        coroutineScope.launch {
            remoteSharedFlow.flow().collect {
                val count = it.toIntOrNull()
                if (count == null) {
                    response1 = it
                    Timber.d("Response1: $it")
                } else {
                    serviceCountUp = count
                    Timber.d("ServiceCountUp: $count")
                }
            }
        }

        coroutineScope.launch {
            val counter = AtomicInteger(0)
            remoteSharedFlow.flow().collect {
                response2 = it
                Timber.d("Response2: $it")
                if (counter.incrementAndGet() == 10)
                    this.cancel()
            }
        }

        coroutineScope.launch {
            for (i in 1..120) {
                countUp = i
                Timber.d("sent: Hello there ($i)")
                remoteSharedFlow.emit("Hello there ($i)")
                delay(1000)
            }
            remoteSharedFlow.emit("end")
            delay(1000)
            remoteSharedFlow.unbindService()
        }

    }

    override fun onPause() {
        Timber.d("onPause")
        super.onPause()

        // request-response
        coroutineScope.cancel()
        remoteSharedFlow.unbindService()

        // service - stop service
        sendActionStop(this)
    }

}

@Composable
fun Greeting(caption: String, text: String, modifier: Modifier = Modifier) {
    Text(
        text = "$caption: $text!",
        modifier = modifier,
        fontSize = 12.sp,
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    RemoterFlowTheme {
        Greeting("Hello", "Android")
    }
}
