-keepnames class io.opentracing.util.GlobalTracer {
    public boolean isRegistered();
}
-keepclassmembernames class org.jctools.** { *; }