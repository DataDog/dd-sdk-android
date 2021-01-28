#Configure Android SDK

If you haven't setup the SDK yet, follow the [in-app setup instructions][1] or find instructions about [Android RUM setup][2]. 


## Enrich User Sessions

Android RUM automatically tracks attributes about user activity, screens, errors and network requests etc. Refer [RUM Data Collection][3] to learn about the RUM Event datatypes and default attributes. You can further enrich user session information and gain finer control over the attributes collected by tracking custom events.

 {{< tabs >}}
    {{% tab "Custom Views" %}}

In addition to [tracking Views automatically][4], you can track specify distinct views (activities, fragments etc.), when it becomes visible and interactive in the onResume() lifecycle. Stop tracking when the view is no longer visible. Most often, this method should be called in the frontmost `Activity` or `Fragment`:

 
   ```kotlin
      fun onResume(){
        GlobalRum.get().startView(viewKey, viewName, viewAttributes)        
      }
      
      fun onPause(){
        GlobalRum.get().stopView(viewKey, viewAttributes)        
      }
   ```


    {{% /tab %}}
    {{% tab "Custom Actions" %}}

In addition to [tracking actions automatically][5], you can track specify custom user actions (taps, clicks, scrolls etc.) with the `RumMonitor#addUserAction`. For continuous action tracking (eg. user scrolling a list), use the `RumMonitor#startUserAction` and `RumMonitor#stopUserAction`.
  
   ```kotlin
      fun onUserInteraction(){
        GlobalRum.get().addUserAction(resourceKey, method, url, resourceAttributes)
      }
   ```

    {{% /tab %}}
    {{% tab "Custom Resource" %}}

In addition to [tracking resources automatically][6], you can track specific custom resources (network requests, 3rd party provider APIs etc.) with methods (`GET`, `POST` etc) while loading the resource with the `RumMonitor#startResource`. Stop tracking with `RumMonitor#stopResource` when it is fully loaded, or `RumMonitor#stopResourceWithError` if an error occurs while loading the resource.

  
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

    {{% /tab %}}
    {{% tab "Custom Errors" %}}

To track specific specific errors, notify the monitor when an error occurs with the message, source, exception and additional attributes. Refer to default [Error Attributes][3].


   ```kotlin
      `addError(<message>, <source>, <throwable>, <attributes>)`
   ```
    {{% /tab %}}
    {{< /tabs >}}

## Track Custom Attribues

In addition to the [default RUM attributes][3] captured by the Mobile SDK automatically, you can choose to add additional contextual information as custom attributes to your RUM events to enrich your observability within Datadog. Custom attributes allow you to slice and dice infomation about observed user behavior (cart value, merchant-tier, ad campaign) with code-level infomation (backend services, session timeline, error logs, network health etc).
 
   ```kotlin
      // Adds an attribute to all future RUM events
      GlobalRum.addAttribute(key, value)
 
      // Removes an attribute to all future RUM events
      GlobalRum.removeAttribute(key)
   ```

### Track widgets
 
Widgets are not automatically tracked with the SDK. To send UI interactions from your widgets manually call Datadog API. [See example][7].


## Configure Library Initialization
 
The following methods in `DatadogConfig.Builder` can be used when creating the Datadog Configuration to initialize the library:
 
| Method                           | Description                                                                                                                                                                                                                                                             |
|----------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `setServiceName(<SERVICE_NAME>)` | Set `<SERVICE_NAME>` as default value for the `service` [standard attribute][4] attached to all logs sent to Datadog (this can be overriden in each Logger).                                                                                                                                                           |
| `setRumEnabled(true)`     | Set to `true` to enable sending RUM data to Datadog.                                                                                                                                                                                                                                  |
| `trackInteractions(Array<ViewAttributesProvider>)` | Enables tracking User interactions (such as Tap, Scroll or Swipe). The parameter also allow you to add custom attributes to the RUM Action events based on the widget with which the user interacted. |
| `useViewTrackingStrategy(strategy)` | Defines the strategy used to track Views. Depending on your application's architecture, you can choose one of several implementations of [`ViewTrackingStrategy`][3] or implement your own. |
| `addPlugin(DatadogPlugin, Feature)`   | Adds a plugin implementation for a specific feature (CRASH, LOG, TRACE, RUM). The plugin will be registered once the feature is initialized and unregistered when the feature is stopped. |
 
### Automatically Track Views

To automatically track your views (activities, fragments etc), provide a tracking strategy at initialization. Depending on your application's architecture, you can choose one of the strategies:

| Strategy | Description                                                                                                                                                                                                                                                   |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ActivityViewTrackingStrategy`   | Every activity in your application is considered a distinct view. |
| `FragmentViewTrackingStrategy`     | Every fragment in your application is considered a distinct view.    |
| `MixedViewTrackingStrategy` | Every activity or fragment in your application is considered a distinct view.  |
| `NavigationViewTrackingStrategy`| Recommended for Android Jetpack Navigation library users. Each Navigation destination is considered a distinct view.  |

For instance to set each Fragment as a distinct view, use the following configuration in your [setup][1]:
   
```kotlin
           
          val configuration = Configuration.Builder()
                           .useViewTrackingStrategy(FragmentViewTrackingStrategy)
                           .build()
          
       }
   }
```
   
**Tip**: For `ActivityViewTrackingStrategy`, `FragmentViewTrackingStrategy`, or `MixedViewTrackingStrategy` you can filter which `Fragment` or `Activity` is tracked as a RUM View by providing a `ComponentPredicate` implementation in the constructor.
   
**Note**: By default, the library won't track any view. If you decide not to provide a view tracking strategy you will have to manually send the views by calling the `startView` and `stopView` methods yourself.


### Automatically Track Network requests

To get timing information in Resources (3rd party providers, network requests) such as time to first byte, DNS resolution, etc., customize the okHttpClient to add the [Event listener][8] factory:

    ```kotlin
    val okHttpClient =  OkHttpClient.Builder()
        .addInterceptor(DatadogInterceptor())
        .eventListenerFactory(DatadogEventListener.Factory())
        .build()
    ```


## Modify or Drop RUM Events

If you need to modify some attributes in your RUM events or to drop some of the events entirely before batching you can do so by providing an implementation of `EventMapper<T>` when initialzing the SDK:
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

   ## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://app.datadoghq.com/rum/create
[2]: <Link to RUM Android Setup>
[3]: <Link to RUM Data Collection>
[4]: <Link to View Tracking Strategy>
[5]: <Link to Configuration Page>
[6]: <Link to automatically track network requests>
[7]: https://github.com/DataDog/dd-sdk-android/tree/master/sample/kotlin/src/main/kotlin/com/datadog/android/sample/widget
[8]: https://square.github.io/okhttp/events/
 

