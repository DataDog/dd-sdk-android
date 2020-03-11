/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-2019 Datadog, Inc.
 */

package com.datadog.android.utils.forge

import com.google.common.util.concurrent.ListenableFuture
import fr.xgouchet.elmyr.Forge
import fr.xgouchet.elmyr.ForgeryFactory
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

internal class ListenableFutureFactory : ForgeryFactory<ListenableFuture<Void>> {
    override fun getForgery(forge: Forge): ListenableFuture<Void> {
        return object : ListenableFuture<Void> {
            override fun addListener(listener: Runnable, executor: Executor) {
            }

            override fun isDone(): Boolean {
                return false
            }

            override fun get(): Void? {
                return null
            }

            override fun get(timeout: Long, unit: TimeUnit): Void? {
                return null
            }

            override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
                return false
            }

            override fun isCancelled(): Boolean {
                return false
            }
        }
    }
}
