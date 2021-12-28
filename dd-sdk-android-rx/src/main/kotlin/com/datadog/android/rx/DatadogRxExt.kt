/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rx

import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

/**
 *  Returns an [Observable<T>] that will send a RUM Error event
 *  if this [Observable<T>] emits an error.
 *  Note that the error will also be emitted by the returned [Observable<T>]
 */
fun <T : Any> Observable<T>.sendErrorToDatadog(): Observable<T> {
    return this.doOnError(DatadogRumErrorConsumer())
}

/**
 *  Returns a [Single<T>] that will send a RUM Error event
 *  if this [Single<T>] emits an error.
 *  Note that the error will also be emitted by the returned [Single<T>]
 */
fun <T : Any> Single<T>.sendErrorToDatadog(): Single<T> {
    return this.doOnError(DatadogRumErrorConsumer())
}

/**
 *  Returns a [Flowable<T>] that will send a RUM Error event
 *  if this [Flowable<T>] emits an error.
 *  Note that the error will also be emitted by the returned [Flowable<T>]
 */
fun <T : Any> Flowable<T>.sendErrorToDatadog(): Flowable<T> {
    return this.doOnError(DatadogRumErrorConsumer())
}

/**
 *  Returns an [Maybe<T>] that will send a RUM Error event
 *  if this [Maybe<T>] emits an error.
 *  Note that the error will also be emitted by the returned [Maybe<T>]
 */
fun <T> Maybe<T>.sendErrorToDatadog(): Maybe<T> {
    return this.doOnError(DatadogRumErrorConsumer())
}

/**
 *  Returns a [Completable] that will send a RUM Error event
 *  if this [Completable] emits an error.
 *  Note that the error will also be emitted by the returned [Completable]
 */
fun Completable.sendErrorToDatadog(): Completable {
    return this.doOnError(DatadogRumErrorConsumer())
}
