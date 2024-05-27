# Keep the optional selector class name. We need this in the SR recorder.
-keepnames class * extends android.view.View
-keepnames class * extends android.graphics.drawable.Drawable
-keepnames class * extends android.graphics.ColorFilter

# Kept for our internal telemetry
-keepnames class com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener
-keepnames class * extends com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
-keepnames class * extends com.datadog.android.sessionreplay.internal.async.RecordedDataQueueItem
