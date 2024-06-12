-keepnames class java.util.function.Function { *; }

# these are just annotations coming from the OpenTelemetry API/Context usage. AutoValue is coming
# from annotation processor (doesn't need to be kept in runtime), MustBeClosed is from compileOnly
# error-prone compiler dependency
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.errorprone.annotations.MustBeClosed
