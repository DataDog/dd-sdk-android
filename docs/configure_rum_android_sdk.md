# Configure Android SDK

If you have not set up the SDK yet, follow the [in-app setup instructions][1] or refer to the [Android RUM setup documentation][2]. 


## Enrich user sessions

Android RUM automatically tracks attributes such as user activity, screens, errors, and network requests. See the [RUM Data Collection documentation][3] to learn about the RUM events and default attributes. You can further enrich user session information and gain finer control over the attributes collected by tracking custom events.

### Custom Views

In addition to [tracking views automatically][4], you can also track specific distinct views (activities, fragments, etc.) when they become visible and interactive in the `onResume()` lifecycle. Stop tracking when the view is no longer visible. Most often, this method should be called in the frontmost `Activity` or `Fragment`:

 
   ```kotlin
      fun onResume(){
        GlobalRum.get().startView(viewKey, viewName, viewAttributes)        
      }
      
      fun onPause(){
        GlobalRum.get().stopView(viewKey, viewAttributes)        
      }
   ```

### Custom Actions

In addition to [tracking actions automatically][5], you can also track specific custom user actions (taps, clicks, scrolls, etc.) with `RumMonitor#addUserAction`. For continuous action tracking (for example, tracking a user scrolling a list), use `RumMonitor#startUserAction` and `RumMonitor#stopUserAction`.
  
   ```kotlin
      fun onUserInteraction(){
        GlobalRum.get().addUserAction(resourceKey, method, url, resourceAttributes)
      }
   ```

### Custom Resources

In addition to [tracking resources automatically][6], you can also track specific custom resources (network requests, third party provider APIs, etc.) with methods (`GET`, `POST`, etc.) while loading the resource with `RumMonitor#startResource`. Stop tracking with `RumMonitor#stopResource` when it is fully loaded, or `RumMonitor#stopResourceWithError` if an error occurs while loading the resource.

  
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

### Custom Errors

To track specific errors, notify the monitor when an error occurs with the message, source, exception, and additional attributes. Refer to the [Error Attributes documentation][9].


   ```kotlin
      addError(message, source, throwable, attributes)
   ```


## Track custom global attributes

In addition to the [default RUM attributes][3] captured by the mobile SDK automatically, you can choose to add additional contextual information, such as custom attributes, to your RUM events to enrich your observability within Datadog. Custom attributes allow you to slice and dice information about observed user behavior (such as cart value, merchant tier, or ad campaign) with code-level information (such as backend services, session timeline, error logs, and network health).

### Track User Sessions
Adding user information to your RUM sessions makes it easy to:
* Follow the journey of a given user
* Know which users are the most impacted by errors
* Monitor performance for your most important users

{{< img src="real_user_monitoring/browser/advanced_configuration/user-api.png" alt="User API in RUM UI"  >}}

The following attributes are **optional**, you should provide **at least one** of them:

| Attribute  | Type | Description                                                                                              |
|------------|------|----------------------------------------------------------------------------------------------------|
| usr.id    | String | Unique user identifier.                                                                                  |
| usr.name  | String | User friendly name, displayed by default in the RUM UI.                                                  |
| usr.email | String | User email, displayed in the RUM UI if the user name is not present. It is also used to fetch Gravatars. |

To identify user sessions, use the `setUser` API, for example:

```kotlin
Datadog.setUserInfo('1234', 'John Doe', 'john@doe.com')
```

### Track attributes

   ```kotlin
      // Adds an attribute to all future RUM events
      GlobalRum.addAttribute(key, value)
 
      // Removes an attribute to all future RUM events
      GlobalRum.removeAttribute(key)
   ```

## Track widgets
 
Widgets are not automatically tracked with the SDK. To send UI interactions from your widgets manually, call the Datadog API. [See example][7].


## Initialization parameters
 
You can use the following methods in `Configuration.Builder` when creating the Datadog configuration to initialize the library:

`trackInteractions(Array<ViewAttributesProvider>)` 
: Enables tracking user interactions (such as tap, scroll, or swipe). The parameter also allows you to add custom attributes to the RUM Action events based on the widget with which the user interacted.

`useViewTrackingStrategy(strategy)` 
: Defines the strategy used to track views. Depending on your application's architecture, you can choose one of several implementations of [`ViewTrackingStrategy`][4] or implement your own.

`addPlugin(DatadogPlugin, Feature)`
: Adds a plugin implementation for a specific feature (`CRASH`, `LOG`, `TRACE`, `RUM`). The plugin is registered once the feature is initialized and unregistered when the feature is stopped.

`trackLongTasks(durationThreshold)` 
: Enables tracking tasks taking longer than `durationThreshold` on the main thread as long tasks in Datadog.

`setFirstPartyHosts()` 
: Defines hosts that have tracing enabled and have RUM resources categorized as `first-party`.

`useEUEndpoints()` 
: Switches target data to EU endpoints.

`useUSEndpoints()` 
: Switches target data to US endpoints.

`useGovEndpoints()` 
: Switches target data to US1-FED endpoints.

`setBatchSize([SMALL|MEDIUM|LARGE])` 
: Defines the individual batch size for requests sent to Datadog.

`setUploadFrequency([FREQUENT|AVERAGE|RARE])` 
: Defines the frequency for requests made to Datadog endpoints (if requests are available).

`sampleRumSessions(<samplingRate>)` 
: Sets the RUM sessions sampling rate. (A value of 0 means no RUM events are sent. A value of 100 means all sessions are kept.)

`setRumXxxEventMapper()` 
: Sets the data scrubbing callbacks for views, actions, resources, and errors.

 
### Automatically track views

To automatically track your views (activities, fragments, etc.), provide a tracking strategy at initialization. Depending on your application's architecture, you can choose one of the following strategies:

`ActivityViewTrackingStrategy`
: Every activity in your application is considered a distinct view.

`FragmentViewTrackingStrategy`
: Every fragment in your application is considered a distinct view.

`MixedViewTrackingStrategy` 
: Every activity or fragment in your application is considered a distinct view.

`NavigationViewTrackingStrategy`
: Recommended for Android Jetpack Navigation library users. Each Navigation destination is considered a distinct view.


For instance, to set each fragment as a distinct view, use the following configuration in your [setup][1]:
   
```kotlin
val configuration = Configuration.Builder()
                 .useViewTrackingStrategy(FragmentViewTrackingStrategy)
                 .build()
```
   
**Tip**: For `ActivityViewTrackingStrategy`, `FragmentViewTrackingStrategy`, or `MixedViewTrackingStrategy` you can filter which `Fragment` or `Activity` is tracked as a RUM View by providing a `ComponentPredicate` implementation in the constructor.
   
**Note**: By default, the library does not track any views. If you decide not to provide a view tracking strategy, you must manually send the views by calling the `startView` and `stopView` methods yourself.


### Automatically track network requests

To get timing information in resources (third-party providers, network requests) such as time to first byte or DNS resolution, customize the `okHttpClient` to add the [EventListener][8] factory:

```kotlin
val okHttpClient =  OkHttpClient.Builder()
    .addInterceptor(DatadogInterceptor())
    .eventListenerFactory(DatadogEventListener.Factory())
    .build()
```

### Automatically track long tasks

Long running operations performed on the main thread can impact the visual performance and reactivity of your application. To track these operations, define the duration threshold above which a task is considered too long.


```kotlin
val config = Configuration.Builder(rumEnabled=true)
                    .trackLongTasks(durationThreshold)
                    .build()
```

## Modify or drop RUM events

To modify some attributes in your RUM events, or to drop some of the events entirely before batching, provide an implementation of `EventMapper<T>` when initializing the SDK:
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
     
   | Event type    | Attribute key      | Description                                     |
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
   
   **Note**: If you return null from the `EventMapper<T>` implementation, the event is dropped.

   ## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://app.datadoghq.com/rum/application/create
[2]: /real_user_monitoring/android
[3]: /real_user_monitoring/android/data_collected
[4]: /real_user_monitoring/android/advanced_configuration/#automatically-track-views
[5]: /real_user_monitoring/android/advanced_configuration/#initialization-parameters
[6]: /real_user_monitoring/android/advanced_configuration/#automatically-track-network-requests
[7]: https://github.com/DataDog/dd-sdk-android/tree/master/sample/kotlin/src/main/kotlin/com/datadog/android/sample/widget
[8]: https://square.github.io/okhttp/events/
[9]: /real_user_monitoring/android/data_collected/?tab=error#event-specific-attributes
 
