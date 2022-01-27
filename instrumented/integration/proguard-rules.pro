# Needed to make sure we don't remove any test code
-dontshrink
#-dontoptimize
#-keepattributes *Annotation*

# Required for some Kotlin-jvm implementation using reflection
-keepnames class kotlin.jvm.** { *; }

# Required because we need access to Datadog.stop() by reflection
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