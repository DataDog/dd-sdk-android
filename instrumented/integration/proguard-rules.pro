# Needed to make sure we don't remove any test code
-dontshrink
#-dontoptimize
#-keepattributes *Annotation*

# Required for some Kotlin-jvm implementation using reflection
-keepnames class kotlin.jvm.** { *; }

# Required because we need access to Datadog.stop() by reflection
-keepnames class com.datadog.android.Datadog {
    *;
}

# Required because we need access to GlobalDatadogTracer getOrNull method to reset it through reflection
-keepnames class com.datadog.android.trace.GlobalDatadogTracer {
    public com.datadog.android.trace.api.tracer.DatadogTracer getOrNull();
    public static com.datadog.android.trace.GlobalDatadogTracer INSTANCE;
}

# Required because we need access to GlobalRumMonitor reset method to reset it through reflection
-keepnames class com.datadog.android.rum.GlobalRumMonitor {
    private void reset();
}

# Required because we need access to RumContext fields by reflection
-keepnames class com.datadog.android.rum.internal.domain.RumContext {
    *;
}

# Required because we need access to telemetry methods by reflection
-keepnames class com.datadog.android.rum.internal.monitor.DatadogRumMonitor {
    *;
}

-dontwarn kotlin.Experimental$Level
-dontwarn kotlin.Experimental
