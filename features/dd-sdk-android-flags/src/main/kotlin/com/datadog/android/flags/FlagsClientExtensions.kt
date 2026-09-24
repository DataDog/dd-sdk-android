/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

@file:JvmName("FlagsClientExtensions")

package com.datadog.android.flags

/**
 * Observes initial disk-read completion without changing readiness. Completed reads are replayed
 * on registration. Does nothing for custom clients without [ConfigurationChangeObservable] support.
 * Java callers can use `FlagsClientExtensions.addConfigurationChangeListener(client, listener)`.
 */
fun FlagsClient.addConfigurationChangeListener(listener: FlagsConfigurationChangeListener) {
    (this as? ConfigurationChangeObservable)?.addConfigurationChangeListener(listener)
}

/** Removes a configuration listener; does nothing for clients without observation support. */
fun FlagsClient.removeConfigurationChangeListener(listener: FlagsConfigurationChangeListener) {
    (this as? ConfigurationChangeObservable)?.removeConfigurationChangeListener(listener)
}
