# Needed to make sure we don't remove any test code
-dontshrink
#-dontoptimize
#-keepattributes *Annotation*

# Required for some Kotlin-jvm implementation using reflection
-keepnames class kotlin.jvm.** { *; }

-keepnames class com.datadog.android.rum.internal.monitor.DatadogRumMonitor {
    public void resetSession();
 }

# Required because we need access to Datadog by reflection
-keepnames class com.datadog.android.Datadog {
    private void stop();
    private void flushAndShutdownExecutors();
}
# Required because we need access to GlobalRum.activeContext and GlobalRum.isRegistered by reflection
-keepnames class com.datadog.android.rum.GlobalRum {
    private java.util.concurrent.atomic.AtomicReference activeContext;
    private java.util.concurrent.atomic.AtomicBoolean isRegistered;
}

# Required because we need access to GlobalTracer isRegistered property to reset it through reflection
-keepnames class io.opentracing.util.GlobalTracer {
    private boolean isRegistered;
}

# Required because we need access to RumContext fields by reflection
-keepnames class com.datadog.android.rum.internal.domain.RumContext {
    *;
}

# Required to be able to assert the intercepted exception types in the monitor
-keepnames class com.datadog.android.nightly.exceptions.** {}
-keepnames class com.datadog.android.nightly.services.** {}