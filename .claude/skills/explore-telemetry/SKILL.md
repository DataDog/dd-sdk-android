---
name: explore-telemetry
description: Explore SDK telemetry logs and metrics using the Datadog MCP server
disable-model-invocation: false
argument-hint: [query or investigation topic]
---

You are helping the user explore SDK telemetry data using the Datadog MCP server.

## What is SDK Telemetry

The dd-sdk-android SDK reports internal telemetry (logs and metrics) to a special Datadog org. This telemetry helps SDK maintainers understand SDK health, detect anomalies, and debug issues in production.

## Where Telemetry Specs Live

Each module in this project defines its telemetry specs in YAML files:

- **Logs specs**: `<module>/src/main/logs/*.yaml` — define log messages emitted by the SDK
- **Metrics specs**: `<module>/src/main/metrics/*.yaml` — define metrics emitted by the SDK

## Which Logs Are Reported as Telemetry

A log spec is reported to telemetry **only if it has `telemetry` in its `targets` list**. For example:

```yaml
logs:
  - id: error_read_ndk_dir
    message: "Error while trying to read the NDK crash directory"
    level: error
    targets:
      - maintainer
      - telemetry    # <-- this means it's sent to telemetry
    throwable: true
```

Logs that only target `user` or `maintainer` (without `telemetry`) are NOT reported to the telemetry org.

## Telemetry Log Fields

Telemetry logs contain rich contextual data beyond the log message itself. Useful fields for querying include:

- **org_id** — the customer organization ID
- **sdk_version** — the SDK version in use
- **os_version** — the Android OS version
- **device_model** — the device model
- **app_id** — the application ID

## How to Explore Telemetry

Use the Datadog MCP server tools to query logs in the telemetry org. Some tips:

1. **Start broad, then narrow down**: Begin with a query for a specific log `id` from the YAML specs, then refine.

2. **Group by / filter by contextual fields**: Telemetry anomalies are often specific to certain conditions. For example:
   - An error might only appear in a specific `org_id`
   - A regression might only affect a certain `sdk_version`
   - An issue might be OS-version-specific (`os_version`)

3. **Cross-reference with specs**: When you see a telemetry log, look up its `id` in the YAML specs to understand what code path triggers it and what severity it has.

4. **Check metrics too**: The `src/main/metrics/*.yaml` files define structured metrics (like `rum_session_ended`) that provide aggregate health signals.

## Your Task

Help the user investigate telemetry by:
1. Reading relevant YAML specs from the codebase to understand what telemetry exists
2. Using the Datadog MCP server to query actual telemetry data
3. Correlating findings between specs and live data
4. Suggesting useful groupings and filters (by org_id, sdk_version, os_version, etc.) to isolate issues

$ARGUMENTS
