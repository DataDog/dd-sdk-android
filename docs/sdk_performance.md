# SDK Performance

## SDK Size

**The SDK is 723 KB**, measured as the `.aar` package size 
without the transitive dependencies. The SDK uses the following transitive dependencies: 

     -   androidx.core:core
     -   androidx.navigation:navigation-fragment-ktx
     -   androidx.navigation:navigation-ui-ktx
     -   androidx.navigation:navigation-runtime-ktx
     -   androidx.recyclerview:recyclerview
     -   androidx.work:work-runtime
     -   com.google.code.gson:gson
     -   com.lyft.kronos:kronos-android
     -   com.squareup.okhttp3:okhttp
     -   org.jetbrains.kotlin:kotlin-stdlib

## Background behavior 
When the application goes in background:
    -   the auto-instrumentation stops and
detaches itself from any UI callbacks (lifecycle events, gesture detection). No RUM event will be automatically tracked
but you can still manually send them with the `RumMonitor`.
   -    The endpoint accepts logs and traces as usual.
   -    The endpoints collect and send new batches of data.

## How batches are created and sent
When a new event is ready to be serialized and batched, the persistence layer asks for 
the last known batch file to store the serialized event. A batch file is 
valid for appending new data when all the following conditions are met:
   
   -   last time the file was accessed was less than 5 seconds ago 
   -   the file size is less than or equal to 4 MB
   -   the number of events in the file is less than or equal to 500   
    
   Once one of those criteria is not met, the batch is marked as `full` and is sent to the
   endpoint in one of the next upload cycles. The frequency at which the batches are sent starts at 5 seconds and goes up 
   linearly with every batch sent. The maximum frequency is one batch per second. If there's no batch or network available or the 
   battery level is too low, the upload frequency is linearly decreased to the minimum default value of one batch every 20 seconds.

## Battery consumption
The SDK does not perform network activity if the device battery level is less than 10% or if the 
device is in power saving mode.

## Lifespan of the persisted data
The SDK stores data in batches and tries to send those whenever the network is 
available. A batch will not be stored more than 18 hours in an application. Every time the SDK
reads a new batch for sending, will first remove batches that are older than 18 hours.

## Low available storage
The SDK checks the storage space used every time it creates a new batch. If this value is greater than
512 MB (the maximum amount of storage space that the SDK will use), it first tries to make more space available 
by removing the older files. 
