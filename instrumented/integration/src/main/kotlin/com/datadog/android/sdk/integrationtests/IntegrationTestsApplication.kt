package com.datadog.android.sdk.integrationtests

import android.app.Application
import com.datadog.android.Datadog

internal class IntegrationTestsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // we just make an initialisation here. The endpointUrl will be set to the
        // MockWebServer url in the test rule.
        Datadog.initialize(this, Runtime.DD_TOKEN, "")
    }
}
