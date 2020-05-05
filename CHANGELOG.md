
# 1.5.0 / 2020-??-??

* [FEATURE] RUM: Add a RUM tracking feature:
    - Track User sessions
    - Track Activities or Fragments as Views
    - Track resources (network requests)
    - Track User interactions
* [FEATURE] RUM: Add helper Interceptor to trace OkHttp requests
* [FEATURE] RUM: Add helper WebView client to trace Webview
* [OTHER] Global: The DatadogConfig needs a valid environment name (`envName`), applied to all features
* [OTHER] Global: The serviceName by default will use your application's package name
* [OTHER] Global: The logs and spans sent from the sdk can be found with the filter `source:android`

# 1.4.1 / 2020-05-06

* [BUGFIX] Trace: Fix spans intake (some spans could be missing)

# 1.4.0 / 2020-05-05

* [FEATURE] Global: Update the SDK initialization code
* [FEATURE] Global: Add a Kotlin extension module with Kotlin specific integrations
* [FEATURE] Trace: Implement OpenTracing specifications
* [FEATURE] Trace: Add helper methods to attach an error to a span
* [FEATURE] Trace: Add helper Interceptor to trace OkHttp requests
* [FEATURE] Logs: Add sampling option in the Logger
* [IMPROVEMENT] Logs: Make the log operations thread safe
* [BUGFIX] Logs: Fix rare crash on upload requests
* [BUGFIX] Prevent OutOfMemory crash on upload. See [#164](https://github.com/DataDog/dd-sdk-android/issues/164) (Thanks @alparp27)


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