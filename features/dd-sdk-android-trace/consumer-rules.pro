-keepnames class com.datadog.android.trace.GlobalDatadogTracer {
    public com.datadog.android.trace.api.tracer.DatadogTracer getOrNull();
    public static com.datadog.android.trace.GlobalDatadogTracer INSTANCE;
}
-keepclassmembernames class org.jctools.** { *; }
