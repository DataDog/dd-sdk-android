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

# Required because we need access to GlobalDatadogTracerHolder getOrNull method to reset it through reflection
-keepclassmembernames class com.datadog.android.trace.GlobalDatadogTracerHolder {
    public com.datadog.android.trace.api.tracer.DatadogTracer getOrNull();
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
