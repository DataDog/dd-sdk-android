---
name: explore-telemetry
description: Explore SDK telemetry logs and metrics using the Datadog MCP server
disable-model-invocation: false
argument-hint: [query or investigation topic]
---

You are helping the user explore SDK telemetry data using the Datadog MCP server.

## What is SDK Telemetry

The dd-sdk-android SDK reports internal telemetry (logs and metrics) to a special Datadog org. This telemetry helps SDK maintainers understand SDK health, detect anomalies, and debug issues in production.

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
| `@log_id` | Unique identifier of the log type | Filtering to a specific log message |
| `@org_id` | Customer organization ID | Isolating issues to specific customers |
| `@sdk_version` | SDK version in use | Detecting regressions across releases |
| `@os_version` | Android OS version | Finding OS-specific issues |
| `@device_model` | Device model name | Finding device-specific issues |
| `@app_id` | Application ID | Isolating issues to specific apps |
| `status` | Log level (error, warn, info, debug) | Filtering by severity |
| `@error.kind` | Exception class name | Grouping by error type |
| `@error.message` | Exception message | Understanding error details |
| `@error.stack` | Stack trace | Root cause analysis |

## Investigation Strategy

Follow this workflow when exploring telemetry:

### 1. Start broad, then narrow down
- Begin with a high-level query (e.g., `status:error` for a time range)
- Look at volume trends — are errors increasing? When did they start?
- Identify the top error types by grouping on `@log_id` or `@error.kind`

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

### 4. Find the root cause
- Look at `@error.stack` for stack traces
- Cross-reference the log ID or error location with the codebase to find the relevant code path
- Check if the issue correlates with a specific SDK release by filtering on `@sdk_version`

### 5. Suggest next steps
Based on findings, suggest:
- Whether this needs a bug fix, a configuration change, or monitoring
- Which code paths to investigate in the codebase
- Additional queries to further refine the investigation

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
