# see https://github.com/DataDog/dd-sdk-android/issues/3491 for motivation
# we are using Work Manager version which is not compatible with R8 full mode
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }
