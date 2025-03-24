package com.datadog.android.compose

/**
 * Indicates that the annotated composable function should be instrumented for
 * RUM view tracking.
 *
 * When this annotation is applied, the Datadog compiler plugin will automatically
 * apply [NavigationViewTrackingEffect] to NavHost within the function, ensuring they
 * the compose navigation can be tracked by RUM.
 */
@Retention(AnnotationRetention.SOURCE)
annotation class TrackViews
