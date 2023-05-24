package com.datadog.android.sample

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.startup.Initializer
import com.datadog.android.Datadog
import com.datadog.android.DatadogSite
import com.datadog.android.core.configuration.Configuration
import com.datadog.android.core.configuration.Credentials
import com.datadog.android.event.ViewEventMapper
import com.datadog.android.privacy.TrackingConsent
import com.datadog.android.rum.GlobalRum
import com.datadog.android.rum.RumMonitor
import com.datadog.android.rum.model.ViewEvent
import com.datadog.android.rum.tracking.ActivityViewTrackingStrategy
import com.datadog.android.rum.tracking.ComponentPredicate

class DatadogInitializer : Initializer<Unit> {

    @SuppressLint("LogNotTimber")
    override fun create(context: Context) {
        val configuration = Configuration.Builder(
            rumEnabled = true,
            logsEnabled = true,
            tracesEnabled = true,
            crashReportsEnabled = true
        )
            .trackInteractions()
            .trackLongTasks(longTaskThresholdMs = 100L)
            .useViewTrackingStrategy(
                ActivityViewTrackingStrategy(
                    trackExtras = true,
                    componentPredicate = object : ComponentPredicate<Activity> {
                        override fun accept(component: Activity) =
                            !Class.forName("com.datadog.android.sample.MainActivity").kotlin.isInstance(component)

                        override fun getViewName(component: Activity): String? = null
                    }
                )
            )
            .useSite(DatadogSite.US1)
            .setUseDeveloperModeWhenDebuggable(true)
            .setRumViewEventMapper(object : ViewEventMapper {
                override fun map(event: ViewEvent): ViewEvent {
                    Log.d("Datadog:debug", "ViewEvent! ${event.view.url}: ${event.view.customTimings}")
                    return event
                }
            })
            .build()
        val credentials = Credentials(
            clientToken = BuildConfig.DD_CLIENT_TOKEN,
            envName = BuildConfig.BUILD_TYPE,
            variant = BuildConfig.FLAVOR,
            rumApplicationId = "BuildConfig.DD_RUM_APPLICATION_ID"
        )

        Datadog.initialize(context, credentials, configuration, TrackingConsent.GRANTED)
        GlobalRum.registerIfAbsent(RumMonitor.Builder().build())
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return listOf()
    }
}
