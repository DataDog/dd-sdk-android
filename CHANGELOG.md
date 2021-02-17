# 1.8.0 / 2021-02-??

* [BUGFIX] Global: handle correctly incorrect domain names in Interceptors' known hosts
* [BUGFIX] RUM: RUM Context was bundled in spans even when RUM was not enabled
* [FEATURE] Global: Allow user to configure the Upload Frequency (see `Configuration.Builder().setUploadFrequency(…).build()`)
* [FEATURE] Global: Allow user to configure the Batch Size (see `Configuration.Builder().setBatchSize(…).build()`)
* [FEATURE] RUM: Customize Views' name
* [FEATURE] RUM: Send NDK Crash related RUM Error
* [FEATURE] RUM: Track custom timings in RUM Views (see `GlobalRum.get().addTiming("<timing_name>")`)
* [FEATURE] RUM: Provide a PII Data Scrubbing feature (see `Configuration.Builder().setRum***EventMapper(…).build()`)
* [FEATURE] RUM: Send NDK Crash related RUM Error
* [IMPROVEMENT] APM: Stop duplicating APM errors as RUM errors
* [IMPROVEMENT] Logs Align the 'error.kind' attribute value with RUM Error 'error.type'
* [IMPROVEMENT] RUM: Get a more accurate Application loading time
* [IMPROVEMENT] RUM: Duplicate RUM Resource with failures as RUM Error
* [IMPROVEMENT] RUM: Add a variant tag on RUM events

# 1.7.0 / 2021-01-04

* [BUGFIX] RUM: fix RUM Error timestamps
* [BUGFIX] RUM: calling `GlobalRum.addAttribute()` with a `null` value would make the application crash
* [BUGFIX] RUM: Actions created with type Custom where sometimes dropped
* [FEATURE] Global: Add support for GDPR compliance feature (see `Datadog.setTrackingConsent()`)
* [FEATURE] Global: Allow setting custom user specific attributes (see `Datadog.setUserInfo()`)
* [IMPROVEMENT] Crash Report: Handle SIGABRT signal in the NDKCrashReporter
* [OTHER] Global: Remove deprecated APIs and warn about future deprecations
* [OTHER] Global: Remove all flavors from sample (allowing to get faster build times)

# 1.6.1 / 2020-11-13

* [BUGFIX] Global: Ensure the network status is properly retrieved on startup

# 1.6.0 / 2020-10-31

* [BUGFIX] RUM: Extend continuous RUM action scope if Resources are still active
* [BUGFIX] RUM: Resources are linked with the wrong Action
* [BUGFIX] Global: Validate the env value passed in the DatadogConfig.Builder
* [BUGFIX] RUM: prevent `trackInterations()` from messing with the Application's theme
* [BUGFIX] Global: Remove unnecessary transitive dependencies from library [#396](https://github.com/DataDog/dd-sdk-android/issues/396) (Thanks @rashadsookram)
* [BUGFIX] Global: Prevent a crash in CallbackNetworkProvider
* [FEATURE] Global: Provide an RxJava integration (`dd-sdk-android-rx`)
* [FEATURE] Global: Provide a Coil integration (`dd-sdk-android-coil`)
* [FEATURE] Global: Provide a Fresco integration (`dd-sdk-android-coil`)
* [FEATURE] Global: Provide a SQLDelight integration (`dd-sdk-android-sqldelight`)
* [FEATURE] Global: Provide a Kotlin Coroutines/Flow integration (`dd-sdk-android-ktx`)
* [FEATURE] Global: Provide an extension for SQLiteDatabase integration (`dd-sdk-android-ktx`)
* [FEATURE] RUM: Add a utility to track various resource loading (`RumResourceInputStream`)
* [FEATURE] RUM: Add an extensions to track Android resources and assets as RUM Resources (`dd-sdk-android-ktx`)
* [FEATURE] RUM: Add the APM trace information in the RUM Resource
* [FEATURE] RUM: Track spans with error as RUM Error
* [IMPROVEMENT] Global: Delay the upload of data in case of network exceptions
* [IMPROVEMENT] CrashReport: Add application information in crashes

# 1.5.2 / 2020-09-18

* [BUGFIX] Global: Prevent a crash when sending data. See [#377](https://github.com/DataDog/dd-sdk-android/issues/377) (Thanks @ronak-earnin)

# 1.5.1 / 2020-09-03

* [BUGFIX] RUM: Make sure the RUM data is sent in applications obfuscated or shrunk with Proguard/R8

# 1.5.0 / 2020-08-12

* [FEATURE] RUM: Add a RUM tracking feature:
    - Track User sessions
    - Track Activities or Fragments as Views (or use manual tracing)
    - Track Resources (network requests)
    - Track User interactions (Tap, Scroll, Swipe)
    - Track Errors and crashes
* [FEATURE] RUM: Add helper Interceptor to track OkHttp requests
* [FEATURE] RUM: Add helper WebViewClient and WebChromeClient implementations to track WebView resources
* [FEATURE] RUM: Add helper library to track Glide requests and errors
* [FEATURE] CrashReport: Add a helper library to detect C/C++ crashes in Android NDK
* [FEATURE] Global: add a method to clear all local unsent data
* [BUGFIX] APM: Fix clock skew issue in traced requests
* [BUGFIX] Logs: Prevent Logcat noise from our SDK when running Robolectric tests
* [IMPROVEMENT] Global: Enhance the SDK performance and ensure it works properly in a multi-process application
* [OTHER] Global: The DatadogConfig needs a valid environment name (`envName`), applied to all features
* [OTHER] Global: The serviceName by default will use your application's package name
* [OTHER] Global: The logs and spans sent from the sdk can be found with the filter `source:android`

# 1.4.3 / 2020-06-25

* [IMPROVEMENT] Global: The `source` tag on logs and trace now uses `android` instead of ~~`mobile`~~

# 1.4.2 / 2020-06-12

* [BUGFIX] Global: Fix data upload (some payloads could rarely be malformed)

# 1.4.1 / 2020-05-06

* [BUGFIX] Trace: Fix spans intake (some spans could be missing)

# 1.4.0 / 2020-05-05

* [FEATURE] Global: Update the SDK initialization code
* [FEATURE] Global: Add a Kotlin extension module with Kotlin specific integrations
* [FEATURE] APM: Implement OpenTracing specifications
* [FEATURE] APM: Add helper methods to attach an error to a span
* [FEATURE] APM: Add helper Interceptor to trace OkHttp requests
* [FEATURE] Logs: Add sampling option in the Logger
* [IMPROVEMENT] Logs: Make the log operations thread safe
* [BUGFIX] Logs: Fix rare crash on upload requests
* [BUGFIX] Global: Prevent OutOfMemory crash on upload. See [#164](https://github.com/DataDog/dd-sdk-android/issues/164) (Thanks @alparp27)


# 1.3.1 / 2020-04-30

### Changes

* [BUGFIX] Fix ConcurrentModificationException crash in the FileReader class. See [#234](https://github.com/DataDog/dd-sdk-android/issues/234) (Thanks @alparp27)

# 1.3.0 / 2020-03-02

### Changes

* [FEATURE] Logs: Add the caller class name as a tag in the LogcatLogHandler (only when app is in Debug)
* [FEATURE] Logs: Allow adding a `JsonElement` as Attribute
* [FEATURE] CrashReport: Let Crash logs use the EMERGENCY log level
* [FEATURE] Global: Warn developers on SDK errors in the Logcat (cf `Datadog.setVerbosity()`)
* [FEATURE] Global: Expose the `Datadog.isInitialized()` method to the public API
* [OTHER] Deprecate the `Datadog.switchEndpoint()` method
* [OTHER] Fail silently when the SDK is not initialized

# 1.2.2 / 2020-02-26

### Changes

* [BUGFIX] Fix invalid dependency group in `dd-sdk-android-timber`. See [#147](https://github.com/DataDog/dd-sdk-android/issues/147) (Thanks @mduong, @alparp27, @rafaela-stockx)

# 1.2.1 / 2020-02-19

### Changes

* [BUGFIX] Fix invalid dependency version in `dd-sdk-android-timber`. See [#138](https://github.com/DataDog/dd-sdk-android/issues/138) (Thanks @mduong)

# 1.2.0 / 2020-01-20

### Changes

* [BUGFIX] Fail silently when trying to initialize the SDK twice. See #86 (Thanks @Vavassor)
* [BUGFIX] Publish the Timber artifact automatically. See #90 (Thanks @Macarse)
* [FEATURE] Create a Crash Handler : App crashes will be automatically logged.
* [FEATURE] Downgrade OkHttp4 to OkHttp3
* [FEATURE] Make Library compatible with API 19+
* [FEATURE] Trigger background upload when the app is used offline
* [FEATURE] Use DownloadSpeed and signal strength to add info on connectivity
* [FEATURE] Use Gzip for log upload requests
* [OTHER] Analyse Benchmark reports in the CI
* [OTHER] Fix the flaky test in DataDogTimeProviderTest
* [OTHER] Generate a report on the SDK API changes ([dd-sdk-android/apiSurface](dd-sdk-android/apiSurface))

# 1.1.1 / 2020-01-07

### Changes

* [BUGFIX] Fix crash on Android Lollipop and Nougat

# 1.1.0 / 2020-01-06

### Changes

* [BUGFIX] Make the packageVersion field optional in the SDK initialisation
* [BUGFIX] Fix timestamp formatting in logs
* [FEATURE] Add a developer targeted logger 
* [FEATURE] Add user info in logs
* [FEATURE] Create automatic Tags / Attribute (app / sdk version)
* [FEATURE] Integrate SDK with Timber
* [IMPROVEMENT] Remove the obfuscation in the logs (faster local processing)
* [IMPROVEMENT] Implement a modern NetworkInfoProvider

# 1.0.0 / 2019-12-17

### Changes

* [BUGFIX] Make sure no logs are lost
* [FEATURE] Allow adding a throwable to logged messages
* [FEATURE] Automatically add the Logger name and thread to the logged messages
* [FEATURE] Allow Changing the endpoint between US and EU servers
* [FEATURE] Allow logger to add attributes for a single log
* [IMPROVEMENT] Remove the usage of the Android WorkManager
* [IMPROVEMENT] Improved overall performances
* [IMPROVEMENT] Disable background job when network or battery are low
* [OTHER] Secure the project with lints, tests and benchmarks
