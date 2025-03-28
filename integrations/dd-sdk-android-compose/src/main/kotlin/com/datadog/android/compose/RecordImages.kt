package com.datadog.android.compose

/**
 * Indicates that the annotated composable function should be instrumented for
 * the Session Replay image recording.
 *
 * When this annotation is applied, the Datadog compiler plugin will automatically
 * apply the [datadog] modifier to all components within the function, ensuring they
 * are included in the semantics tree and can be recorded appropriately.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class RecordImages
