#Mobile Data Collected

The Datadog Real User Monitoring SDK generates six types of events:

| Event type | Description                                                                                                                                                                                                                                                   |
|------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [Session][1]    | Session represents a real user journey on your mobile application. It begins when the user launches the application, and it is kept live as long as the user stays active. During the user journey, all RUM events generated as part of the session share the same `session.id` attribute.
 |
| [View][2]       | A view represents a unique screen (or screen segment) on your mobile application. Individual activities, fragments, navigation views, etc. are classified as distinct views. While a user stays on a view, RUM event attributes (errors, resources, actions) get attached to the view with a unique `view.id`.                                   |
| [Resource][3]   | A resource represents network requests to first-party hosts, APIs, third-party providers, and libraries in your mobile application. All requests generated during a user session are attached to the view with a unique `resource.id`.                                                      |
| [Error][5]     | An error represents an exception or crash emitted by the mobile application attached to the view it is generated in.                                                                                                                                            |
| [Action][6]     | An action represents one instance of user activity in your mobile application (application launch, tap, swipe, back, etc.) Each action is attached with a unique `action_id` attached to the view it gets generated in.                                                                                                                                                         |
| [Long Task][7] | A long task event is generated for any task in the browser that blocks the main thread for more than the specified duration threshold. |

The following diagram illustrates the RUM event hierarchy:

{{< img src="real_user_monitoring/data_collected/event-hierarchy.png" alt="RUM Event hierarchy" style="width:50%;border:none" >}}

## Default attributes

RUM collects common attributes for all events and attributes specific to each event by default listed below. You can also choose to enrich your user session data with [additional events][1] or by [adding custom attributes][2] to default events specific to your application monitoring and business analytics needs.


### Core

| Attribute name   | Type   | Description                 |
|------------------|--------|-----------------------------|
| `type`     | string | The type of the event (for example, `view` or `resource`).             |
| `application.id` | string | The Datadog application ID. |


## Event specific attributes 


{{% /tab %}}
{{% tab "View" %}}




| Metric                              | Type        | Description                                                                                                                                                                                                                 |
|----------------------------------------|-------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `view.time_spent`                             | number (ns) | Time spent on the current view.                                                                                                                                                                                                  |
| `view.loading_time`                             | number (ns) | Time until the page is ready and no network request or DOM mutation is currently occurring. For more information, refer to the [Data Collected documentation][8].|
| `view.error.count`            | number      | Count of all errors collected for this view.                                                                                                                                                                        |
| `view.long_task.count`        | number      | Count of all long tasks collected for this view.                                                                                                                                                                           |
| `view.resource.count`         | number      | Count of all resources collected for this view.                                                                                                                                                                            |
| `view.action.count`      | number      | Count of all actions collected for this view.                                                                                     


{{% /tab %}}
{{% tab "Resource" %}}


| Metric                              | Type           | Description                                                                                                                               |
|----------------------------------------|----------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `duration`                             | number (ns)        | Entire time spent loading the resource.                                                                                                   |
| `resource.size`                | number (bytes) | Resource size.                                                                                                                            |
| `resource.connect.duration`    | number (ns)    | Time spent establishing a connection to the server. (connectEnd - connectStart)                                                            |
| `resource.ssl.duration`        | number (ns)    | Time spent for the TLS handshake. If the last request is not over HTTPS, this metric does not appear. (connectEnd - secureConnectionStart) |
| `resource.dns.duration`        | number (ns)    | Time spent resolving the DNS name of the last request. (domainLookupEnd - domainLookupStart)                                               |
| `resource.redirect.duration`   | number (ns)    | Time spent on subsequent HTTP requests. (redirectEnd - redirectStart)                                                                      |
| `resource.first_byte.duration` | number (ns)    | Time spent waiting for the first byte of response to be received. (responseStart - RequestStart)                                           |
| `resource.download.duration`   | number (ns)    | Time spent downloading the response. (responseEnd - responseStart)                                                                         |

### Resource attributes

| Attribute                      | Type   | Description                                                                             |
|--------------------------------|--------|-----------------------------------------------------------------------------------------|
| `resource.type`                | string | The type of resource being collected (for example, `css`, `javascript`, `media`, `XHR`, `image`).           |
| `resource.method`                | string | The HTTP method (for example `POST`, `GET`).           |
| `resource.status_code`             | number | The response status code.                                                               |
| `resource.url`                     | string | The resource URL.                                                                       |
| `resource.url_host`        | string | The host part of the URL.                                                          |
| `resource.provider.name`      | string | The resource provider name. Default is `unknown`.                                            |
| `resource.provider.domain`      | string | The resource provider domain.                                            |
| `resource.provider.type`      | string | The resource provider type (for example `first-party`, `cdn`, `ad`, `analytics`).                                            |



{{% /tab %}}
{{% tab "Error" %}}

Front-end errors are collected with Real User Monitoring (RUM). The error message and stack trace are included when available.

### Error origins
Front-end errors are split into four different categories depending on their `error.origin`:

- **network**: XHR or Fetch errors resulting from AJAX requests. Specific attributes to network errors can be found [in the documentation][1].
- **source**: Unhandled exceptions or unhandled promise rejections (source-code related).
- **console**: `console.error()` API calls.
- **custom**: Errors sent with the [RUM `addError` API][2] default to `custom`.

### Error attributes

| Attribute       | Type   | Description                                                       |
|-----------------|--------|-------------------------------------------------------------------|
| `error.source`  | string | Where the error originates from (for example, `console` or `network`).     |
| `error.type`    | string | The error type (or error code in some cases).                   |
| `error.message` | string | A concise, human-readable, one-line message explaining the event. |
| `error.stack`   | string | The stack trace or complementary information about the error.     |

#### Network errors

Network errors include information about failing HTTP requests. The following facets are also collected:

| Attribute                      | Type   | Description                                                                             |
|--------------------------------|--------|-----------------------------------------------------------------------------------------|
| `error.resource.status_code`             | number | The response status code.                                                               |
| `error.resource.method`                | string | The HTTP method (for example `POST`, `GET`).           |
| `error.resource.url`                     | string | The resource URL.                                                                       |
| `error.resource.provider.name`      | string | The resource provider name. Default is `unknown`.                                            |
| `error.resource.provider.domain`      | string | The resource provider domain.                                            |
| `error.resource.provider.type`      | string | The resource provider type (for example `first-party`, `cdn`, `ad`, `analytics`).                                            |


{{% /tab %}}
{{% tab "User Action" %}}



### Action timing metrics

| Metric    | Type   | Description              |
|--------------|--------|--------------------------|
| `action.loading_time` | number (ns) | The loading time of the action. See how it is calculated in the [User Action documentation][4]. |
| `action.long_task.count`        | number      | Count of all long tasks collected for this action. |
| `action.resource.count`         | number      | Count of all resources collected for this action. |
| `action.error.count`      | number      | Count of all errors collected for this action.|

### Action attributes

| Attribute    | Type   | Description              |
|--------------|--------|--------------------------|
| `action.id` | string | UUID of the user action. |
| `action.type` | string | Type of the user action. For [Custom User Actions][5], it is set to `custom`. |
| `action.target.name` | string | Element that the user interacted with. Only for automatically collected actions. |


{{% /tab %}}
{{< /tabs >}}


## Data retention
By default, all data collected is kept at full granularity for 15 days. 

## Further Reading

{{< partial name="whats-next/whats-next.html" >}}
