/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rx

import com.datadog.android.Datadog
import com.datadog.android.v2.api.SdkCore
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

/**
 * Returns an [Observable] that will send a RUM Error event
 * if this [Observable] emits an error.
 * Note that the error will also be emitted by the returned [Observable]
 * @param T the type of data in this [Observable]
 * @param sdkCore the SDK instance to forward the errors to. If not provided, default instance
 * will be used.
 * @return the new [Observable] instance
 */
fun <T : Any> Observable<T>.sendErrorToDatadog(sdkCore: SdkCore = Datadog.getInstance()): Observable<T> {
    return this.doOnError(DatadogRumErrorConsumer(sdkCore))
}

/**
 * Returns a [Single] that will send a RUM Error event
 * if this [Single] emits an error.
 * Note that the error will also be emitted by the returned [Single]
 * @param T the type of data in this [Single]
 * @param sdkCore the SDK instance to forward the errors to. If not provided, default instance
 * will be used.
 * @return the new [Single] instance
 */
fun <T : Any> Single<T>.sendErrorToDatadog(sdkCore: SdkCore = Datadog.getInstance()): Single<T> {
    return this.doOnError(DatadogRumErrorConsumer(sdkCore))
}

/**
 * Returns a [Flowable] that will send a RUM Error event
 * if this [Flowable] emits an error.
 * Note that the error will also be emitted by the returned [Flowable]
 * @param T the type of data in this [Flowable]
 * @param sdkCore the SDK instance to forward the errors to. If not provided, default instance
 * will be used.
 * @return the new [Flowable] instance
 */
fun <T : Any> Flowable<T>.sendErrorToDatadog(sdkCore: SdkCore = Datadog.getInstance()): Flowable<T> {
    return this.doOnError(DatadogRumErrorConsumer(sdkCore))
}

/**
 * Returns an [Maybe] that will send a RUM Error event
 * if this [Maybe] emits an error.
 * Note that the error will also be emitted by the returned [Maybe]
 * @param T the type of data in this [Maybe]
 * @param sdkCore the SDK instance to forward the errors to. If not provided, default instance
 * will be used.
 * @return the new [Maybe] instance
 */
fun <T> Maybe<T>.sendErrorToDatadog(sdkCore: SdkCore = Datadog.getInstance()): Maybe<T> {
    return this.doOnError(DatadogRumErrorConsumer(sdkCore))
}

/**
 * Returns a [Completable] that will send a RUM Error event
 * if this [Completable] emits an error.
 * Note that the error will also be emitted by the returned [Completable]
 * @param sdkCore the SDK instance to forward the errors to. If not provided, default instance
 * will be used.
 * @return the new [Completable] instance
 */
fun Completable.sendErrorToDatadog(sdkCore: SdkCore = Datadog.getInstance()): Completable {
    return this.doOnError(DatadogRumErrorConsumer(sdkCore))
}
