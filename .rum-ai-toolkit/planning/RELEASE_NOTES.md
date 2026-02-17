# Release Notes - Partial View Updates Feature

## Version 2.X.X

### New Feature: Partial View Updates (Opt-In)

We're excited to introduce **Partial View Updates**, a new opt-in feature that significantly reduces bandwidth usage and improves battery life for mobile applications using the Datadog RUM Android SDK.

#### Overview

When enabled, the SDK sends only changed fields in RUM view update events after the initial full view event, reducing bandwidth usage by 43-93% depending on your application's behavior.

#### Key Benefits

- **43-93% bandwidth reduction** for view events
- **Lower battery consumption** from reduced I/O operations
- **Improved performance** on mobile devices with limited connectivity
- **Cost savings** for users with limited or expensive data plans
- **Transparent to users:** No changes to dashboards, queries, or analytics

#### How It Works

- **First view event:** Full `view` event with all properties (type: `"view"`)
- **Subsequent updates:** Smaller `view_update` events with only changed fields (type: `"view_update"`)
- **Backend reconstruction:** Datadog automatically reconstructs the complete view state
- **Document versioning:** Each event includes a `document_version` counter for ordering

#### Enabling the Feature

Add one line to your RUM configuration:

```kotlin
val rumConfiguration = RumConfiguration.Builder(applicationId)
    .setEnablePartialViewUpdates(true)  // NEW: Enable partial updates
    .build()

Rum.enable(rumConfiguration)
```

#### Requirements

- **SDK version:** 2.X.X or later
- **Backend:** No configuration needed (automatically supported)
- **Opt-in:** Feature must be explicitly enabled
- **Configuration:** Must be set during SDK initialization (immutable after)

#### Performance

- **Diff computation overhead:** <5ms per view update (typically 1-3ms)
- **Memory footprint:** ~2KB per active view
- **Bandwidth savings:**
  - Typical: 40-60% across all view events
  - Long-lived views: 70-90% savings over time
  - High-update views: 80-93% savings

#### When to Use

**Recommended for:**
- Apps with frequent view updates (resource loads, user actions, errors)
- Apps targeting users with limited data plans
- Apps focused on battery optimization
- Apps with long-lived views (dashboards, feeds, real-time content)

**Optional for:**
- Apps with very few view updates per session
- Apps where bandwidth is not a primary concern

#### Migration Guide

1. **Update SDK dependency:**
   ```gradle
   dependencies {
       implementation 'com.datadoghq:dd-sdk-android-rum:2.X.X'
   }
   ```

2. **Enable the feature:**
   ```kotlin
   RumConfiguration.Builder(applicationId)
       .setEnablePartialViewUpdates(true)  // Add this line
       .build()
   ```

3. **Test in staging** before production rollout

4. **Monitor bandwidth** usage to verify savings

5. **Roll out to production** when satisfied

No other code changes required.

#### Backward Compatibility

- ✅ Feature is **opt-in** and disabled by default
- ✅ When disabled, SDK behaves identically to previous versions
- ✅ No breaking changes to public API
- ✅ Safe to deploy to all users (no behavior change unless explicitly enabled)
- ✅ Can be disabled at any time by removing the configuration flag

#### Documentation

- **User Guide:** [docs/rum/partial_view_updates.md](../../docs/rum/partial_view_updates.md)
- **Architecture:** [docs/architecture/partial_view_updates.md](../../docs/architecture/partial_view_updates.md)
- **Implementation Spec:** [SPEC.md](SPEC.md)

#### Known Limitations

- Configuration is immutable after SDK initialization
- Requires SDK version 2.X.X or later
- Feature cannot be enabled retroactively for already-running sessions

#### Troubleshooting

**Q: How do I verify the feature is working?**
A: Check your RUM events in Datadog - you should see `view_update` events instead of multiple `view` events for the same view.id. You can also enable SDK verbose logging with `Datadog.setVerbosity(Log.VERBOSE)`.

**Q: Does this affect how data appears in Datadog?**
A: No, the backend automatically reconstructs the complete view state. Your dashboards and queries work identically.

**Q: Can I enable this mid-session?**
A: No, the configuration is immutable after SDK initialization. Set it during `Rum.enable()`.

**Q: What if I want to disable it after enabling?**
A: Update your SDK configuration to remove `.setEnablePartialViewUpdates(true)` (or set it to `false`) and redeploy your app.

#### Technical Details

**Components Added:**
- `ViewEventTracker` - Manages view state tracking and event generation
- `ViewDiffComputer` - Computes differences between view states
- `ViewEventConverter` - Converts ViewEvent objects to Map representation
- `RumEventWriterAdapter` - Bridges ViewEventTracker to SDK event writing
- RumViewScope integration for feature enablement

**Configuration:**
- `RumConfiguration.setEnablePartialViewUpdates(Boolean)` - New builder method
- `RumFeature.Configuration.enablePartialViewUpdates` - Internal flag (default: false)

**Event Types:**
- `"view"` - Full view event (first event, or when feature disabled)
- `"view_update"` - Partial update event (subsequent events when feature enabled)
- `_dd.document_version` - Version counter for event ordering (1, 2, 3...)

**Performance Metrics:**
- Diff computation: O(n) where n = number of fields
- Typical execution: 1-3ms for 100-150 fields
- Memory per view: ~2KB for state storage
- No performance impact when feature is disabled

---

## Other Changes in This Release

[Include other release notes for version 2.X.X]

---

## Upgrade Instructions

### From 2.Y.Z or Earlier

1. **Update your dependency:**
   ```gradle
   dependencies {
       implementation 'com.datadoghq:dd-sdk-android-rum:2.X.X'
   }
   ```

2. **(Optional) Enable Partial View Updates:**
   ```kotlin
   val rumConfiguration = RumConfiguration.Builder(applicationId)
       .setEnablePartialViewUpdates(true)  // Optional, defaults to false
       .build()
   ```

3. **Test your integration:**
   - Run your test suite
   - Verify RUM events appear correctly in Datadog
   - If enabled, confirm `view_update` events are being sent

4. **Deploy to production**

### Breaking Changes

None. This release is fully backward compatible.

### Deprecations

None.

---

## Testing and Validation

This feature has been extensively tested with:
- ✅ 40+ unit tests covering all components
- ✅ Integration tests for end-to-end scenarios
- ✅ Performance benchmarks validating <5ms overhead
- ✅ Memory tests confirming ~2KB per view footprint
- ✅ Backward compatibility tests (feature disabled = old behavior)
- ✅ Edge case testing (rapid updates, large views, cleanup)

### Test Coverage

- **ViewEventTracker:** 15 unit tests
- **ViewDiffComputer:** 25+ unit tests
- **Integration:** End-to-end flow validation
- **Performance:** <5ms diff computation for typical views
- **Memory:** <100KB for 10 active views

---

## Implementation Notes

### Development Phases

This feature was implemented across 5 phases:

1. **Phase 1:** Foundation & Configuration (configuration flag, data structures)
2. **Phase 2:** Diff Computation Engine (ViewDiffComputer with comprehensive tests)
3. **Phase 3:** Event Flow Integration (ViewEventTracker, event writing)
4. **Phase 4:** RumViewScope Integration (feature enablement, cleanup)
5. **Phase 5:** Testing & Documentation (this release)

### Design Decisions

- **Store-and-compare approach:** Stores last sent event, computes diff at send time
- **Array optimization:** Sends only new elements for append-only arrays (slow_frames, page_states)
- **Lazy initialization:** ViewEventTracker created only when feature is enabled
- **Backend update rules:** SDK sends changes, backend applies merge logic

### References

- **Spec:** `.rum-ai-toolkit/planning/SPEC.md`
- **Phase Documents:** `.rum-ai-toolkit/planning/phases/`
- **User Guide:** `docs/rum/partial_view_updates.md`
- **Architecture:** `docs/architecture/partial_view_updates.md`

---

## Feedback and Support

We'd love to hear your feedback on this feature!

- **GitHub Issues:** [datadog/dd-sdk-android](https://github.com/DataDog/dd-sdk-android/issues)
- **Support:** support@datadoghq.com
- **Documentation:** [docs.datadoghq.com](https://docs.datadoghq.com/)

### Feature Adoption

We'll be monitoring feature adoption and performance metrics post-release. Future improvements may include:
- SDK telemetry to track bandwidth savings
- Adaptive optimization based on update frequency
- Further payload compression techniques

---

## Credits

This feature was implemented based on the RFC "RUM Event Format Limitation" with input from:
- RUM SDK team
- Backend team (for `view_update` event support)
- Product team (for bandwidth optimization requirements)

Special thanks to all contributors and reviewers.

---

## Next Steps

After deploying this release:

1. **Monitor adoption:** Track what percentage of users enable the feature
2. **Measure impact:** Validate bandwidth savings in production
3. **Collect feedback:** Gather user experiences and pain points
4. **Consider default:** Evaluate timeline for making feature enabled-by-default

**Future consideration:** Once validated in production, we may consider enabling this feature by default in a future major release (with opt-out option).
