---
name: investigate-sdk-telemetry-errors
description: Investigate SDK telemetry errors using the Datadog MCP server
disable-model-invocation: false
argument-hint: [query or investigation topic]
---

You are helping the user explore SDK telemetry data using the Datadog MCP server.

## What is SDK Telemetry

The SDK reports internal telemetry (logs and metrics) to a special Datadog org. This telemetry helps SDK maintainers understand SDK health, detect anomalies, and debug issues in production.

### Log Types

Telemetry logs have two distinct types, indicated by the `@type` field:

- **`@type:log`** — Regular SDK-internal telemetry logs. These are emitted by the SDK itself to report errors, warnings, diagnostics, and operational events. They originate from SDK code paths.

- **`@type:sdk_crash`** — Crash reports from customer apps that have at least one SDK-related stackframe. These are not emitted by the SDK itself — they are crashes that happened in the customer's process where the SDK was on the call stack. **The presence of an SDK stackframe does not mean the SDK caused the crash** — the SDK may simply be present on the call stack while the root cause lies elsewhere (e.g., in app code, a third-party library, or a platform OS issue). Always assess SDK responsibility explicitly.

**Always filter or split by `@type` when investigating.** Mixing both types in the same query obscures root causes and leads to misleading aggregations.

## Using the Datadog MCP Server

Always use the Datadog MCP server tools to query telemetry. The key tools are:

- **search_datadog_logs** — search and filter telemetry logs
- **analyze_datadog_logs** — aggregate and analyze log patterns
- **search_datadog_metrics** / **get_datadog_metric** — explore telemetry metrics
- **search_datadog_monitors** — find existing monitors on telemetry signals

Before querying, use `ToolSearch` to load the specific Datadog MCP tools you need.

## Key Telemetry Log Fields

Telemetry logs contain rich contextual data. Use these fields for querying, filtering, and grouping:

| Field | Description | Use for |
|-------|-------------|---------|
| `@type` | Log type: `log` or `sdk_crash` | **Always filter first** — separate investigations |
| `@log_id` | Unique identifier of the log type | Filtering to a specific log message |
| `@org_id` | Customer organization ID | Isolating issues to specific customers |
| `@sdk_version` | SDK version in use | Detecting regressions across releases |
| `@os_version` | OS version | Finding OS-specific issues |
| `@device_model` | Device model name | Finding device-specific issues |
| `@app_id` | Application ID | Isolating issues to specific apps |
| `status` | Log level (error, warn, info, debug) | Filtering by severity |
| `@error.kind` | Exception class name | Grouping by error type |
| `@error.message` | Exception message | Understanding error details |
| `@error.stack` | Stack trace | Root cause analysis |

## Investigation Strategy

**Default focus: `status:error` only.** Unless the user explicitly asks to look at warnings, info, or debug logs, always scope queries to `status:error`. Errors are the signal; lower-severity logs are noise for most investigations.

Follow this workflow when exploring telemetry:

### 1. Split by `@type` first

Always separate `@type:log` and `@type:sdk_crash` — they represent fundamentally different signals and must be investigated independently.

**`@type:log` investigation** — SDK-internal errors and diagnostics:
- Begin with a high-level query scoped to `@type:log status:error`
- Look at volume trends — are errors increasing? When did they start?
- Identify the top error types by grouping on `@log_id` or `@error.kind`

**`@type:sdk_crash` investigation** — Customer crashes with SDK stackframes:
- Begin with a high-level query scoped to `@type:sdk_crash status:error`
- These represent crashes in customer apps where the SDK was on the call stack — **not necessarily caused by the SDK**
- Group by `@error.kind` or `@error.stack` to identify crash patterns
- Pay special attention to the number of **unique orgs** affected (`@org_id`), since crashes impact real customers
- Look for SDK stackframes in `@error.stack` to identify which SDK component is present

### 2. Isolate the signal
Once you spot something interesting, narrow using contextual fields:
- **By SDK version**: Is this a regression? Compare `@sdk_version:2.x` vs previous versions
- **By customer**: Is it one org or widespread? Group by `@org_id`
- **By OS version**: Is it platform-specific? Filter on `@os_version`
- **By device**: Is it device-specific? Filter on `@device_model`
- **By app**: Is it one integration or many? Group by `@app_id`

### 3. Understand the impact
- Check the **count** of affected events
- Check the **number of unique orgs** affected (group by `@org_id`)
- Check if there are **existing monitors** covering this signal
- Compare to a previous time window to understand if the issue is growing
- For `@type:sdk_crash`: customer-facing crashes are higher severity — note org count prominently

### 4. Find the root cause
- Look at `@error.stack` for stack traces
- Cross-reference the log ID or error location with the codebase to find the relevant code path
- Check if the issue correlates with a specific SDK release by filtering on `@sdk_version`
- For `@type:sdk_crash`: identify which SDK frames appear in the stack, but then explicitly assess whether the SDK is responsible (see below)

#### Assessing SDK responsibility in `sdk_crash` logs

The SDK being on the call stack is a necessary but not sufficient condition for SDK responsibility. Work through these questions:

1. **Where is the crash frame?** Look at the top of the stack (the frame where the crash originated).
   - If the crashing frame is in SDK code → SDK is likely responsible
   - If the crashing frame is in app code, the platform framework, or a third-party library, but SDK frames appear deeper in the stack → the SDK may just be an innocent bystander (e.g., it called a system API that crashed, or the app called into the SDK with bad state)

2. **What is the exception type?** Some exceptions point away from the SDK:
   - `NullPointerException` or `IllegalArgumentException` in app code → likely app bug
   - `OutOfMemoryError` or `StackOverflowError` → system-level, SDK presence is coincidental
   - Exceptions thrown from within SDK classes → stronger signal of SDK responsibility

3. **Does the crash correlate with an SDK version?** Filter by `@sdk_version` and check if the crash rate changes across versions. A spike starting at a specific SDK version is strong evidence of SDK responsibility.

4. **Is the crash widespread or isolated?** Group by `@org_id`. If many unrelated orgs are affected across different apps, the SDK is more likely the common cause. If it's concentrated in one app, the app may be misusing the SDK or have its own bug.

5. **Can the SDK have triggered the crash indirectly?** Even if the crash frame is not in SDK code, check whether the SDK could have corrupted state, passed invalid arguments to a system API, or called into a context where the crash became inevitable. Cross-reference the stack with the codebase.

Always explicitly state your SDK responsibility assessment when reporting findings on `sdk_crash` logs — don't leave it ambiguous.

### 5. Suggest next steps
Based on findings, suggest:
- Whether this needs a bug fix, a configuration change, or monitoring
- Which code paths to investigate in the codebase
- Additional queries to further refine the investigation
- For `@type:sdk_crash`: a clear verdict on SDK responsibility (likely responsible / likely not responsible / inconclusive) with the reasoning behind it

## Tips

- When unsure what fields are available, start with a broad search and inspect the returned log entries to discover field names
- Use time-based comparisons (e.g., last 24h vs previous 24h) to detect regressions
- High-cardinality fields like `@org_id` are useful for grouping but avoid using them in overly broad queries
- If the user mentions a specific log message or error, search for it directly in the log content first

## Your Task

Help the user investigate telemetry by:
1. Using the Datadog MCP server to query and analyze telemetry data
2. Following the investigation strategy above to systematically narrow down issues
3. Suggesting useful groupings and filters to isolate root causes
4. Cross-referencing findings with the codebase when relevant

$ARGUMENTS
