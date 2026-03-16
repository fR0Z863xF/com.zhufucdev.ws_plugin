package com.zhufucdev.ws_plugin.hook

import android.content.Context
import com.highcapable.yukihookapi.hook.log.YLog
import com.highcapable.yukihookapi.hook.log.loggerI
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.zhufucdev.me.stub.Method
import com.zhufucdev.me.plugin.AidlTransportClient
import com.zhufucdev.me.plugin.ServerScope
import com.zhufucdev.me.plugin.WsServer
import com.zhufucdev.me.plugin.connect
import com.zhufucdev.me.stub.AgentState
import com.zhufucdev.me.xposed.XposedScheduler
import com.zhufucdev.me.xposed.PREFERENCE_NAME_BRIDGE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Optional
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

class Scheduler : XposedScheduler() {
    companion object {
        private const val TAG = "Scheduler"
    }
    private lateinit var server: WsServer
    private var transport: String = "aidl"
    private var aidlClient: AidlTransportClient? = null

    override fun PackageParam.initialize() {
        val prefs = prefs(PREFERENCE_NAME_BRIDGE)
        server = WsServer(
            port = prefs.getInt("me_server_port", 20230),
            useTls = prefs.getBoolean("me_server_tls", true)
        )
        transport = prefs.getString("me_transport", "aidl") ?: "aidl"

        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            startServer()
        }
    }

    override fun PackageParam.getHookingMethod(): Method =
        prefs(PREFERENCE_NAME_BRIDGE).getString("me_method", "xposed_only").let {
            Method.valueOf(it.uppercase())
        }

    private suspend fun startServer() {
        var warned = false

        while (true) {
            if (transport == "aidl") {
                YLog.info(msg = "Using AIDL transport", tag = TAG)
                tryAidlTransport()
            } else {
                YLog.info(msg = "Using WebSocket transport", tag = TAG)
                server.connect(id) {
                    if (emulation.isPresent)
                        startEmulation(emulation.get())
                }
            }

            if (!warned) {
                YLog.info(
                    msg = "Provider offline. Waiting for data channel to become online",
                    tag = TAG
                )
                warned = true
            }
            delay(1.seconds)
        }
    }

    private suspend fun tryAidlTransport(): Boolean {
        if (transport != "aidl") return false
        val context = resolveContext() ?: return false
        val client = aidlClient ?: AidlTransportClient(context).also { aidlClient = it }
        val connection = client.connect(id) { state ->
            when (state) {
                AgentState.CANCELED -> stopEmulation()
                AgentState.PAUSED -> pauseEmulation()
                AgentState.PENDING -> resumeEmulation()
                AgentState.NOT_JOINED -> stopEmulation()
                else -> Unit
            }
        }
        if (!client.waitUntilConnected()) {
            YLog.warn(msg = "AIDL bind timeout", tag = TAG)
            connection.close()
            return false
        }

        YLog.info(msg = "AIDL connected", tag = TAG)

        if (!client.isVersionCompatible()) {
            YLog.warn(msg = "AIDL version mismatch", tag = TAG)
            connection.close()
            return false
        }

        val emulation = runCatching { client.readEmulation(id) }.getOrNull()
        if (emulation == null) {
            YLog.warn(msg = "AIDL pipe read failed", tag = TAG)
            connection.close()
            return false
        }

        YLog.info(msg = "AIDL emulation received", tag = TAG)

        val ctx: CoroutineContext = coroutineContext
        val scope = object : ServerScope {
            override val coroutineContext: CoroutineContext = ctx
            override val emulation: Optional<com.zhufucdev.me.stub.Emulation> = Optional.of(emulation)

            override suspend fun sendStarted(info: com.zhufucdev.me.stub.EmulationInfo) {
                client.sendEmulationInfo(id, info)
            }

            override suspend fun sendProgress(intermediate: com.zhufucdev.me.stub.Intermediate) {
                client.sendIntermediate(id, intermediate)
            }

            override suspend fun close() {
                connection.close()
            }
        }

        val result = runCatching {
            with(scope) { startEmulation(emulation) }
        }
        result.exceptionOrNull()?.let { error ->
            YLog.error(tag = TAG, msg = "AIDL emulation failed", e = error)
        }
        if (result.isSuccess) {
            client.sendAgentState(id, AgentState.COMPLETED)
        } else {
            client.sendAgentState(id, AgentState.FAILURE)
        }
        connection.close()
        return true
    }

    private fun resolveContext(): Context? {
        return runCatching {
            val cls = Class.forName("android.app.ActivityThread")
            val method = cls.getDeclaredMethod("currentApplication")
            method.invoke(null) as? Context
        }.getOrNull()
    }

    private fun stopEmulation() {
        if (isWorking) {
            onEmulationCompleted(com.zhufucdev.me.stub.Emulation(
                trace = com.zhufucdev.me.stub.EmptyBox(),
                motion = com.zhufucdev.me.stub.EmptyBox(),
                cells = com.zhufucdev.me.stub.EmptyBox(),
                velocity = 0.0,
                repeat = 0,
                satelliteCount = 0
            ))
        }
    }

    private fun pauseEmulation() {
        stopEmulation()
    }

    private fun resumeEmulation() {
        // handled by reconnecting in startServer loop
    }
}