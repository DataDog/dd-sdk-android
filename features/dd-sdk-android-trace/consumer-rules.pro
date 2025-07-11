-keepclassmembernames class com.datadog.android.trace.GlobalDatadogTracerHolder {
    public com.datadog.android.trace.api.tracer.DatadogTracer getOrNull();
}
-keepclassmembernames class org.jctools.** { *; }
