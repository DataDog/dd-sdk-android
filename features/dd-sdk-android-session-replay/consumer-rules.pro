# Keep the optional selector class name. We need this in the SR recorder.
-keepnames class * extends android.view.View
-keepnames class * extends android.graphics.drawable.Drawable
-keepnames class * extends android.graphics.ColorFilter

# Kept for our internal telemetry
-keepnames class com.datadog.android.sessionreplay.internal.recorder.listener.WindowsOnDrawListener
-keepnames class com.datadog.android.sessionreplay.internal.recorder.TreeViewTraversal
-keepnames class * extends com.datadog.android.sessionreplay.recorder.mapper.WireframeMapper
-keepnames class * extends com.datadog.android.sessionreplay.internal.async.RecordedDataQueueItem

# Keep the fine grained masking level enums
-keepnames enum * extends com.datadog.android.sessionreplay.PrivacyLevel { *; }

# Identify optional AndroidX system-bar protection children without capturing their empty pixels.
# View class names are already preserved above; keep only the ownership marker we inspect.
-if class androidx.core.view.insets.ProtectionLayout
-keepclassmembers class androidx.core.view.insets.ProtectionLayout {
    static java.lang.Object PROTECTION_VIEW;
}
