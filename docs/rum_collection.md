# Android RUM Collection

Send [Real User Monitoring data][1] to Datadog from your Android applications with [Datadog's `dd-sdk-android` client-side RUM library][2] and leverage the following features:

* get a global idea about your app’s performance and demographics;
* understand which resources are the slowest;
* analyze errors by OS and device type.

## Setup

1. Add the Gradle dependency by declaring the library as a dependency in your `build.gradle` file:

    ```conf
    repositories {
        maven { url "https://dl.bintray.com/datadog/datadog-maven" }
    }

    dependencies {
        implementation "com.datadoghq:dd-sdk-android:x.x.x"
    }
    ```

2. Initialize the library with your application context, [tracking consent][9], and [Datadog client token][4]. For security reasons, you must use a client token: you cannot use [Datadog API keys][5] to configure the `dd-sdk-android` library as they would be exposed client-side in the Android application APK byte code. For more information about setting up a client token, see the [client token documentation][4]. You also need to provide an Application ID (see our [RUM Getting Started page][3]).

    {{< tabs >}}
    {{% tab "US" %}}

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>")
                        .trackInteractions()
                        .useViewTrackingStrategy(strategy)
                        .build()
        Datadog.initialize(this, trackingConsent, config)
    }
}
```

    {{% /tab %}}
    {{% tab "EU" %}}

```kotlin
class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>")
                        .trackInteractions()
                        .useViewTrackingStrategy(strategy)
                        .useEUEndpoints()
                        .build()
        Datadog.initialize(this, trackingConsent, config)
    }
}
```

    {{% /tab %}}
    {{< /tabs >}}

Depending on your application's architecture, you can choose one of several implementations of `ViewTrackingStrategy`:

  - `ActivityViewTrackingStrategy`: Every activity in your application is considered a distinct view.
  - `FragmentViewTrackingStrategy`: Every fragment in your application is considered a distinct view.
  - `NavigationViewTrackingStrategy`: If you use the Android Jetpack Navigation library, this is the recommended strategy. It automatically tracks the navigation destination as a distinct view.
  - `MixedViewTrackingStrategy`: Every activity or fragment in your application is considered a distinct view. This strategy is a mix between the `ActivityViewTrackingStrategy` and `FragmentViewTrackingStrategy`.
  
  **Note**: For `ActivityViewTrackingStrategy`, `FragmentViewTrackingStrategy`, or `MixedViewTrackingStrategy` you can filter which `Fragment` or `Activity` is tracked as a RUM View by providing a `ComponentPredicate` implementation in the constructor.
  
  **Note**: By default, the library won't track any view. If you decide not to provide a view tracking strategy you will have to manually send the views by calling the `startView` and `stopView` methods yourself.

3. Configure and register the RUM Monitor. You only need to do it once, usually in your application's `onCreate()` method:

    ```kotlin
    val monitor = RumMonitor.Builder()
            // Optionally set a sampling between 0.0 and 100.0%
            // Here 75% of the RUM Sessions will be sent to Datadog
            .sampleRumSessions(75.0f)
            .build()
    GlobalRum.registerIfAbsent(monitor)
    ```

4. If you want to track your OkHttp requests as resources, you can add the provided [Interceptor][6] as follows:

    ```kotlin
    val okHttpClient =  OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor())
        .build()
    ```

    This creates RUM Resource data around each request processed by the OkHttpClient, with all the relevant information automatically filled (URL, method, status code, error). Note that only network requests started when a view is active will be tracked. If you want to track requests when your application is in the background, you can create a view manually, as explained below.

    **Note**: If you also use multiple Interceptors, this one must be called first.

5. (Optional) If you want to get timing information in Resources (such as time to first byte, DNS resolution, etc.), you can add the [Event][6] listener factory as follows:

    ```kotlin
    val okHttpClient =  OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor())
        .eventListenerFactory(DatadogEventListener.Factory())
        .build()
    ```

6. (Optional) If you want to manually track RUM events, you can use the `GlobalRum` class.
  
  To track views, call the `RumMonitor#startView` when the view becomes visible and interactive (equivalent with the lifecycle event `onResume`) followed by `RumMonitor#stopView` when the view is no longer visible(equivalent with the lifecycle event `onPause`) as follows:

   ```kotlin
      fun onResume(){
        GlobalRum.get().startView(viewKey, viewName, viewAttributes)        
      }
      
      fun onPause(){
        GlobalRum.get().stopView(viewKey, viewAttributes)        
      }
   ```
  
  To track resources, call the `RumMonitor#startResource` when the resource starts being loaded, and `RumMonitor#stopResource` when it is fully loaded, or `RumMonitor#stopResourceWithError` if an error occurs while loading the resource, as follows:
  
   ```kotlin
      fun loadResource(){
        GlobalRum.get().startResource(resourceKey, method, url, resourceAttributes)
        try {
          // do load the resource
          GlobalRum.get().stopResource(resourceKey, resourceKind, additionalAttributes)
        } catch (e : Exception) {
          GlobalRum.get().stopResourceWithError(resourceKey, message, origin, e)
        }
      }
   ```
  
  To track user actions, call the `RumMonitor#addUserAction`, or for continuous actions, call the `RumMonitor#startUserAction` and `RumMonitor#stopUserAction`, as follows:
  
   ```kotlin
      fun onUserInteraction(){
        GlobalRum.get().addUserAction(resourceKey, method, url, resourceAttributes)
      }
   ```

7. (Optional) If you want to add custom information as attributes to all RUM events, you can use the `GlobalRum` class.

   ```kotlin
      // Adds an attribute to all future RUM events
      GlobalRum.addAttribute(key, value)

      // Removes an attribute to all future RUM events
      GlobalRum.removeAttribute(key)
   ```
8. If you need to modify some attributes in your RUM events or to drop some of the events entirely before batching you can do so by providing an implementation of `EventMapper<T>` when initialzing the SDK:
   ```kotlin
      val config = DatadogConfig.Builder("<CLIENT_TOKEN>", "<ENVIRONMENT_NAME>", "<APPLICATION_ID>")
                        ...
                        .setRumErrorEventMapper(rumErrorEventMapper)
                        .setRumActionEventMapper(rumActionEventMapper)
                        .setRumResourceEventMapper(rumResourceEventMapper)
                        .setRumViewEventMapper(rumViewEventMapper)
                        .build()
   ```
   As you will notice when implementing the `EventMapper<T>` interface, only some of the attributes are modifiable for each event type as follows:
     
   | Event Type    | Attribute key      | Description                                     |
   |---------------|--------------------|-------------------------------------------------|
   | ViewEvent     | `view.referrer`      | URL that linked to the initial view of the page |
   |               | `view.url`           | URL of the view                                 |
   | ActionEvent   |                    |                                                 |
   |               | `action.target.name` | Target name                                     |
   | ErrorEvent    |                    |                                                 |
   |               | `error.message`      | Error message                                   |
   |               | `error.stack`        | Stacktrace of the error                         |
   |               | `error.resource.url` | URL of the resource                             |
   | ResourceEvent |                    |                                                 |
   |               | `resource.url`       | URL of the resource                             |
   
   **Note**: If you return null from the `EventMapper<T>` implementation the event will be dropped.

## Advanced logging

### Library Initialization

The following methods in `DatadogConfig.Builder` can be used when creating the Datadog Configuration to initialize the library:

| Method                           | Description                                                                                                                                                                                                                                                             |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `setServiceName(<SERVICE_NAME>)` | Set `<SERVICE_NAME>` as default value for the `service` [standard attribute][4] attached to all logs sent to Datadog (this can be overriden in each Logger).                                                                                                                                                           |
| `setRumEnabled(true)`     | Set to `true` to enable sending RUM data to Datadog.                                                                                                                                                                                                                                  |
| `trackInteractions(Array<ViewAttributesProvider>)` | Enables tracking User interactions (such as Tap, Scroll or Swipe). The parameter allow you to add custom attributes to the RUM Action events based on the widget with which the user interacted. |
| `useViewTrackingStrategy(strategy)` | Defines the strategy used to track Views. Depending on your application's architecture, you can choose one of several implementations of `ViewTrackingStrategy` (see above) or implement your own. |
| `addPlugin(DatadogPlugin, Feature)`   | Adds a plugin implementation for a specific feature (CRASH, LOG, TRACE, RUM). The plugin will be registered once the feature is initialized and unregistered when the feature is stopped. |

### RumMonitor Initialization

The following methods in `RumMonitor.Builder` can be used when creating the RumMonitor to track RUM data:

| Method                           | Description                                                                                                                                                                                                                                                             |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `sampleRumSessions(float)`   | Sets the sampling rate for RUM Sessions. This method expects a value between 0 and 100, and is used as a percentage of Session for which data will be sent to Datadog. |

### Manual Tracking

If you need to manually track events, you can do so by getting the active `RumMonitor` instance, and call one of the following methods:

| Method                           | Description                                                                                                                                                                                                                                                             |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `startView(<key>, <name>, <attributes>)`   | Notifies the RumMonitor that a new View just started. Most often, this method should be called in the frontmost `Activity` or `Fragment`'s `onResume()` method. |
| `stopView(<key>, <attributes>)`   | Notifies the RumMonitor that the current View just stopped. Most often, this method should be called in the frontmost `Activity` or `Fragment`'s `onPause()` method. |
| `addUserAction(<type>, <name>, <attributes>)`   | Notifies the RumMonitor that a user action just happened. |
| `startUserAction(<type>, <name>, <attributes>)`   | Notifies the RumMonitor that a continuous user action just started (for example a user scrolling a list). |
| `stopUserAction(<type>, <name>, <attributes>)`   | Notifies the RumMonitor that a continuous user action just stopped. |
| `startResource(<key>, <method>, <url>, <attributes>)`   | Notifies the RumMonitor that the application started loading a resource with a given method (e.g.: `GET` or `POST`), at the given url. |
| `stopResource(<key>, <status>, <size>, <kind> <attributes>)`   | Notifies the RumMonitor that a resource finished being loaded, with a given status (usually an HTTP status code), size (in bytes) and kind. |
| `stopResourceWithError(<key>, <status>, <message>, <source>, <throwable>)` | Notifies the RumMonitor that a resource couldn't finished being loaded, because of an exception. |
| `addError(<message>, <source>, <throwable>, <attributes>)` | Notifies the RumMonitor that an error occurred. |



### Tracking widgets

Most of the time, the widgets are displayed in the `AppWidgetHostView` provided by the HomeScreen application, and we are not
able to provide auto-instrumentation for those components. To send UI interaction information from your widgets,  manually call our
API. See one example approach in this sample application: 
[Tracking widgets](https://github.com/DataDog/dd-sdk-android/tree/master/sample/kotlin/src/main/kotlin/com/datadog/android/sample/widget)

## Batch collection

All the RUM events are first stored on the local device in batches. Each batch follows the intake specification. They are sent as soon as the network is available, and the battery is high enough to ensure the Datadog SDK does not impact the end user's experience. If the network is not available while your application is in the foreground, or if an upload of data fails, the batch is kept until it can be sent successfully.

This means that even if users open your application while being offline, no data will be lost.

The data on the disk will automatically be discarded if it gets too old to ensure the SDK doesn't use too much disk space.

## Extensions

### Coil

If you use Coil to load images in your application, take a look at Datadog's [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-coil).

### Fresco

If you use Fresco to load images in your application, take a look at Datadog's [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-fresco).

### Glide

If you use Glide to load images in your application, take a look at our [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-glide).

### Picasso

If you use Picasso, let it use your `OkHttpClient`, and you'll get RUM and APM information about network requests made by Picasso.

```kotlin
        val picasso = Picasso.Builder(context)
                .downloader(OkHttp3Downloader(okHttpClient))
                // …
                .build()
        Picasso.setSingletonInstance(picasso)
```

### Retrofit

If you use Retrofit, let it use your `OkHttpClient`, and you'll get RUM and APM information about network requests made with Retrofit.

```kotlin
        val retrofitClient = Retrofit.Builder()
                .client(okHttpClient)
                // …
                .build()
```

### SQLDelight

If you use SQLDelight, take a look at our [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-sqldelight).

### SQLite

Following SQLiteOpenHelper's [Generated API documentation][8], you only have to provide the implementation of the
DatabaseErrorHandler -> `DatadogDatabaseErrorHandler` in the constructor.

Doing this detects whenever a database is corrupted and sends a relevant
RUM error event for it.

```kotlint
   class <YourOwnSqliteOpenHelper>: SqliteOpenHelper(<Context>, 
                                                     <DATABASE_NAME>, 
                                                     <CursorFactory>, 
                                                     <DATABASE_VERSION>, 
                                                     DatadogDatabaseErrorHandler()) {
                                // …
   
   }
```

### Apollo (GraphQL)

If you use Apollo, let it use your `OkHttpClient`, and you'll get RUM and APM information about all the queries performed through Apollo client.

```kotlin
        val apolloClient =  ApolloClient.builder()
                 .okHttpClient(okHttpClient)
                 .serverUrl(<APOLLO_SERVER_URL>)
                 .build()
```

## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://docs.datadoghq.com/real_user_monitoring/data_collected/
[2]: https://github.com/DataDog/dd-sdk-android
[3]: https://docs.datadoghq.com/real_user_monitoring/installation/?tab=us
[4]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[5]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[6]: https://square.github.io/okhttp/interceptors/
[7]: https://square.github.io/okhttp/events/
[8]: https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper
[9]: gdpr.md
