package com.datadog.android.compose

/**
 * Indicates that the annotated composable function should be instrumented for
 * the RUM view tracking, actions tracking and enhancement of Session Replay recordings.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class ComposeInstrumentation
