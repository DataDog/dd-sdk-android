# Keep the public constructors ML Kit discovers through Android manifest metadata.
-keepclassmembers class com.google.mlkit.common.internal.CommonComponentRegistrar {
    public <init>();
}
-keepclassmembers class com.google.mlkit.vision.common.internal.VisionCommonRegistrar {
    public <init>();
}
-keepclassmembers class com.google.mlkit.vision.text.internal.TextRegistrar {
    public <init>();
}
