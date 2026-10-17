/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.flags.integration

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Looper
import com.datadog.android.api.feature.Feature
import com.datadog.android.api.feature.FeatureEventReceiver
import com.datadog.android.api.feature.FeatureScope
import com.datadog.android.api.storage.datastore.DataStoreHandler
import com.datadog.android.api.storage.datastore.DataStoreReadCallback
import com.datadog.android.api.storage.datastore.DataStoreWriteCallback
import com.datadog.android.core.InternalSdkCore
import com.datadog.android.core.internal.persistence.Deserializer
import com.datadog.android.core.persistence.Serializer
import com.datadog.android.core.persistence.datastore.DataStoreContent
import com.datadog.android.core.stub.StubExecutorService
import com.datadog.android.core.stub.StubSDKCore
import com.datadog.android.flags.Flags
import com.datadog.android.flags.FlagsConfiguration
import com.datadog.android.rum.GlobalRumMonitor
import com.datadog.android.rum.Rum
import com.datadog.android.rum.RumConfiguration
import com.datadog.android.tests.elmyr.useCoreFactories
import com.datadog.tools.unit.forge.BaseConfigurator
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.jvm.useJvmFactories
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

internal class FlagsRuntime(forge: Forge) : AutoCloseable {
    val server = MockWebServer()
    private val app = mock<Application>()
    val stubCore = StubSDKCore(forge, app)
    private val store = MemoryStore()
    private val flushTasks = mutableListOf<Runnable>()
    private val scheduler = mock<ScheduledExecutorService>()
    var pauseAssignments: Boolean = false
    private val pendingAssignments = ArrayDeque<Runnable>()
    val core = object : InternalSdkCore by stubCore {
        override fun createOkHttpCallFactory(block: OkHttpClient.Builder.() -> Unit): Call.Factory =
            OkHttpClient.Builder().apply(block).build()

        override fun createScheduledExecutorService(executorContext: String): ScheduledExecutorService = scheduler

        override fun createSingleThreadExecutorService(executorContext: String): ExecutorService {
            val executor = StubExecutorService(executorContext)
            return object : ExecutorService by executor {
                override fun execute(command: Runnable) {
                    if (executorContext == "flags-network" && pauseAssignments) {
                        pendingAssignments.add(command)
                    } else {
                        executor.execute(command)
                    }
                }
            }
        }

        override fun getFeature(featureName: String): FeatureScope? {
            val scope = stubCore.getFeature(featureName) ?: return null
            return object : FeatureScope by scope {
                override val dataStore: DataStoreHandler = store

                override fun sendEvent(event: Any) {
                    scope.sendEvent(event)
                    (scope.unwrap<Feature>() as? FeatureEventReceiver)?.onReceive(event)
                }
            }
        }
    }

    init {
        if (Looper.getMainLooper() == null) {
            @Suppress("DEPRECATION")
            Looper.prepareMainLooper()
        }
        whenever(app.applicationInfo) doReturn ApplicationInfo()
        whenever(scheduler.scheduleWithFixedDelay(any(), any(), any(), any())) doAnswer {
            flushTasks.add(it.getArgument(0))
            mock<ScheduledFuture<Any>>()
        }
        server.start()
        Rum.enable(RumConfiguration.Builder(forge.anAlphabeticalString()).trackNonFatalAnrs(false).build(), core)
        restartFlags()
        GlobalRumMonitor.get(core).startView("baseline-view", "baseline-view")
    }

    fun restartFlags() {
        flushTasks.clear()
        Flags.enable(
            FlagsConfiguration.Builder()
                .useCustomFlagEndpoint(server.url("/assignments").toString())
                .initializationTimeout(0)
                .build(),
            core
        )
    }

    fun assignments(enabled: Boolean = true) {
        val assignments = JsonParser.parseString(ASSIGNMENTS).asJsonObject
        val flag = assignments.getAsJsonObject("data").getAsJsonObject("attributes")
            .getAsJsonObject("flags").getAsJsonObject("enabled")
        flag.addProperty("variationValue", enabled)
        flag.addProperty("variationKey", if (enabled) "on" else "off")
        server.enqueue(MockResponse().setBody(assignments.toString()))
    }

    fun flush() {
        stubCore.advanceTimeBy(10_000)
        flushTasks.forEach(Runnable::run)
    }

    fun completeAssignments() {
        pauseAssignments = false
        while (pendingAssignments.isNotEmpty()) {
            completeNextAssignment()
        }
    }

    fun completeNextAssignment() {
        pendingAssignments.removeFirst().run()
    }

    fun events(feature: String): List<JsonObject> = stubCore.eventsWritten(feature).map {
        JsonParser.parseString(it.eventData).asJsonObject
    }

    override fun close() {
        listOf(Feature.FLAGS_FEATURE_NAME, Feature.FLAGS_EVALUATIONS_FEATURE_NAME, Feature.RUM_FEATURE_NAME).forEach {
            core.getFeature(it)?.unwrap<Feature>()?.onStop()
        }
        server.shutdown()
    }

    private class MemoryStore : DataStoreHandler {
        private val entries = mutableMapOf<String, Pair<Int, String>>()

        override fun <T : Any> setValue(
            key: String,
            data: T,
            version: Int,
            callback: DataStoreWriteCallback?,
            serializer: Serializer<T>
        ) {
            entries[key] = version to checkNotNull(serializer.serialize(data))
            callback?.onSuccess()
        }

        override fun <T : Any> value(
            key: String,
            version: Int?,
            callback: DataStoreReadCallback<T>,
            deserializer: Deserializer<String, T>
        ) {
            val entry = entries[key]?.takeIf { version == null || version == it.first }
            callback.onSuccess(entry?.let { DataStoreContent(it.first, deserializer.deserialize(it.second)) })
        }

        override fun removeValue(key: String, callback: DataStoreWriteCallback?) {
            entries.remove(key)
            callback?.onSuccess()
        }

        override fun clearAllData() {
            entries.clear()
        }
    }

    companion object {
        const val CONTROL = "dd_evaluation_telemetry_opt_out"
        private val ASSIGNMENTS = """
            {"data":{"attributes":{"flags":{
              "enabled":{"variationType":"boolean","variationValue":true,"doLog":true,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH"},
              "untracked":{"variationType":"boolean","variationValue":true,"doLog":false,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH"},
              "text":{"variationType":"string","variationValue":"assigned","doLog":true,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH"},
              "integer":{"variationType":"integer","variationValue":7,"doLog":true,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH"},
              "number":{"variationType":"number","variationValue":2.5,"doLog":true,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH"},
              "object":{"variationType":"object","variationValue":{"assigned":true},"doLog":true,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH"},
              "future-reason":{"variationType":"boolean","variationValue":true,"doLog":false,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"FUTURE_REASON"},
              "malformed":{"variationType":"boolean","variationValue":"invalid","doLog":false,
                "allocationKey":"allocation","variationKey":"on","extraLogging":{},"reason":"TARGETING_MATCH"}
            }}}}
        """.trimIndent()
    }
}

internal class FlagsIntegrationForgeConfigurator : BaseConfigurator() {
    override fun configure(forge: Forge) {
        super.configure(forge)
        forge.useJvmFactories()
        forge.useCoreFactories()
    }
}
