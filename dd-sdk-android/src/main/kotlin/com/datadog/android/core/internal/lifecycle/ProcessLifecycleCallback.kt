package com.datadog.android

import com.datadog.android.core.internal.lifecycle.ProcessLifecycleMonitor
import com.datadog.android.log.internal.net.BroadcastReceiverNetworkInfoProvider
import com.datadog.android.log.internal.net.NetworkInfo
import com.datadog.android.log.internal.net.NetworkInfoProvider

class ProcessLifecycleCallback(networkInfoProvider: NetworkInfoProvider) :
    ProcessLifecycleMonitor.Callback {

    override fun onStarted() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onResumed() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onStopped() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onPaused() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}