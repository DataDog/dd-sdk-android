# Tracking Consent

### Tracking consent values

To be compliant with the GDPR regulation, the SDK requires the tracking consent value at initialization.
The tracking consent can be one of the following values:

1. `TrackingConsent.PENDING`: The SDK starts collecting and batching the data but does not send it to the data
   collection endpoint. The SDK waits for the new tracking consent value to decide what to do with the batched data.
2. `TrackingConsent.GRANTED`: The SDK starts collecting the data and sends it to the data collection endpoint.
3. `TrackingConsent.NOT_GRANTED`: The SDK does not collect any data. You will not be able to manually send any logs, traces, or 
   RUM events.
   
### Updating the tracking consent at runtime

If you want to update the tracking consent after the SDK is initialized, call: `Datadog.setTrackingConsent(<NEW CONSENT>)`.
The SDK changes its behavior according to the new consent. For example, if the current tracking consent is `TrackingConsent.PENDING` and you update it to:

1.  `TrackingConsent.GRANTED`: The SDK sends all current batched data and future data directly to the data collection endpoint.
2. `TrackingConsent.NOT_GRANTED`: The SDK wipes all batched data and does not collect any future data.
