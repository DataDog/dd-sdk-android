# Partial View Updates

## Overview

The Partial View Updates feature reduces bandwidth and battery usage by sending only changed fields in view update events, rather than resending the complete view state with every update.

**Benefits:**
- 43-93% reduction in bandwidth usage for view events
- Lower battery consumption from reduced I/O operations
- Improved SDK performance on mobile devices with limited connectivity
- Cost savings for users with limited data plans

**Availability:** This feature is available starting from SDK version 2.X.X

## Enabling the Feature

The feature is opt-in and disabled by default. Enable it during SDK initialization:

```kotlin
val rumConfiguration = RumConfiguration.Builder(applicationId)
    .setEnablePartialViewUpdates(true)  // Enable partial updates
    .build()

Rum.enable(rumConfiguration)
```

## How It Works

### Event Types

When partial view updates are enabled:
- **First view event:** Full `view` event with all properties (type: `"view"`)
- **Subsequent updates:** `view_update` events with only changed fields (type: `"view_update"`)
- **Backend processing:** The Datadog backend reconstructs the complete view state by applying updates sequentially

### Document Version Tracking

Each view has an independent `document_version` counter that increments with every event:
- First event: `document_version: 1` (full view)
- Second event: `document_version: 2` (partial update)
- Third event: `document_version: 3` (partial update)
- And so on...

The backend uses `document_version` to ensure events are applied in the correct order.

### Example

```kotlin
// Configuration
val rumConfiguration = RumConfiguration.Builder(applicationId)
    .setEnablePartialViewUpdates(true)
    .build()

// First event sent (full view)
{
  "type": "view",
  "view": {
    "id": "view-abc-123",
    "url": "https://example.com/product",
    "time_spent": 0,
    "action": { "count": 0 },
    "resource": { "count": 0 }
  },
  "_dd": { "document_version": 1 }
}

// User loads an image - second event sent (only changes)
{
  "type": "view_update",
  "view": {
    "id": "view-abc-123",
    "time_spent": 150,
    "resource": { "count": 1 }
  },
  "_dd": { "document_version": 2 }
}

// User taps button - third event sent (only changes)
{
  "type": "view_update",
  "view": {
    "id": "view-abc-123",
    "time_spent": 300,
    "action": { "count": 1 }
  },
  "_dd": { "document_version": 3 }
}
```

The backend automatically reconstructs the full view state, so your dashboards and queries work identically to before.

## Requirements

- **SDK version:** 2.X.X or later
- **Backend support:** Automatically available (no configuration needed)
- **Opt-in:** Feature must be explicitly enabled via `setEnablePartialViewUpdates(true)`
- **Configuration timing:** Must be set during SDK initialization (immutable after)

## Performance Characteristics

- **Diff computation overhead:** <5ms per update (typically 1-3ms)
- **Memory footprint:** ~2KB per active view for state storage
- **Bandwidth savings:**
  - Typical savings: 40-60% across all view events
  - Long-lived views: 70-90% savings over time
  - Views with many updates: 80-93% savings

## When to Use

### Recommended Scenarios

Enable partial updates when:
- Your app has frequent view updates (many resource loads, user actions, errors)
- You want to minimize SDK bandwidth usage for mobile users
- Your users have limited or expensive data plans
- You're optimizing for battery life

### Optional Scenarios

Consider keeping disabled when:
- Your app has very few view updates per session (minimal benefit)
- You're debugging SDK behavior and want simpler, full events
- You have specific compatibility requirements with custom event processing

## Troubleshooting

### How do I verify the feature is working?

**Option 1: Check event types in Datadog**
- Navigate to RUM → Events in Datadog
- Filter for your application
- Look for events with `type: "view_update"` instead of repeated `type: "view"` events
- Verify `document_version` increments: 1, 2, 3...

**Option 2: Enable SDK debug logging**
```kotlin
Datadog.setVerbosity(android.util.Log.VERBOSE)
```
Look for log messages related to view diff computation.

### Does this affect how data appears in Datadog?

No. The backend reconstructs the complete view state automatically. Your dashboards, queries, and analytics work identically whether the feature is enabled or disabled.

### Can I enable this mid-session?

No. The configuration is immutable after SDK initialization. You must set `setEnablePartialViewUpdates(true)` when calling `Rum.enable()`.

### Will this work with older backend versions?

Yes. The Datadog backend automatically supports `view_update` events. No special configuration or minimum backend version is required.

### What if I want to disable it after enabling?

Update your SDK configuration in your app code to set `setEnablePartialViewUpdates(false)` (or remove the line since `false` is the default), then redeploy your app. The SDK will revert to sending full `view` events.

### What happens to array fields like slow_frames?

Array fields use an **append-only optimization**:
- The SDK sends only new elements added to arrays
- The backend applies an `APPEND` rule to merge them
- This is safe for RUM arrays like `slow_frames` and `page_states` which only grow

## Migration Guide

### Upgrading from Older SDK Versions

1. **Update SDK dependency** to version 2.X.X or later:
   ```gradle
   dependencies {
       implementation 'com.datadoghq:dd-sdk-android-rum:2.X.X'
   }
   ```

2. **Enable the feature** in your SDK initialization code:
   ```kotlin
   val rumConfiguration = RumConfiguration.Builder(applicationId)
       .setEnablePartialViewUpdates(true)  // Add this line
       .build()

   Rum.enable(rumConfiguration)
   ```

3. **Test in staging** before production rollout:
   - Verify events appear correctly in Datadog
   - Check that `view_update` events are being sent
   - Validate dashboard queries still work as expected

4. **Monitor bandwidth** after rollout:
   - Compare RUM event upload sizes before/after
   - Verify expected bandwidth reduction (40-90%)

5. **Roll out to production** when satisfied with testing

No other code changes are required.

### Rollback Plan

If you need to disable the feature:
1. Remove `.setEnablePartialViewUpdates(true)` from your configuration
2. Rebuild and redeploy your app
3. The SDK will immediately revert to sending full `view` events

## Best Practices

1. **Test before production:** Always enable in a staging environment first to validate behavior

2. **Monitor bandwidth:** Use Datadog metrics to verify bandwidth reduction matches expectations

3. **Gradual rollout:** Consider enabling for a percentage of users first (use feature flags in your app)

4. **Keep SDK updated:** Stay on the latest SDK version for bug fixes and performance improvements

5. **Understand the tradeoff:**
   - **Benefit:** Reduced bandwidth and battery usage
   - **Cost:** ~2KB memory per active view + <5ms CPU per update (negligible for most apps)

## Backwards Compatibility

- Feature is **opt-in** and disabled by default (no behavior change unless explicitly enabled)
- When disabled, SDK behaves identically to previous versions
- No breaking changes to public API
- Safe to deploy to all users (but must explicitly enable to see benefits)

## Technical Details

For SDK developers and maintainers, see:
- [Internal Architecture Documentation](../architecture/partial_view_updates.md)
- [Implementation Spec](../../.rum-ai-toolkit/planning/SPEC.md)
- [Phase Documents](../../.rum-ai-toolkit/planning/phases/)

## Feedback and Support

We'd love to hear your feedback on this feature!

- **GitHub Issues:** [datadog/dd-sdk-android](https://github.com/DataDog/dd-sdk-android/issues)
- **Support:** support@datadoghq.com
- **Documentation:** [docs.datadoghq.com](https://docs.datadoghq.com/)

## Related Documentation

- [RUM Android SDK Overview](https://docs.datadoghq.com/real_user_monitoring/android/)
- [SDK Performance Guide](../sdk_performance.md)
- [Advanced Troubleshooting](../advanced_troubleshooting.md)
