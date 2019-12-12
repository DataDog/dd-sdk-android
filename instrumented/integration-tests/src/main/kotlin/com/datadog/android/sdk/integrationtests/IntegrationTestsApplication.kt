package com.datadog.android.sdk.integrationtests

import android.app.Application
import com.datadog.android.Datadog

class IntegrationTestsApplication : Application() {
    init {
        application = this
    }

    override fun onCreate() {
        super.onCreate()
        // we just make an initialisation here. The endpointUrl will be set to the
        // MockWebServer url in the test rule.
        Datadog.initialize(this, Runtime.DD_TOKEN, "")
    }

    companion object {
        lateinit var application: IntegrationTestsApplication
            private set
    }
}
