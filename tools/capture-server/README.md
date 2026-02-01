# Datadog Capture Server

A local proxy server that captures and inspects Datadog SDK requests before forwarding them to the real Datadog intake. Useful for debugging, development, and benchmarking compression algorithms.

## Features

- Captures all Datadog SDK features: RUM, Logs, Traces, Session Replay, Profiling, Feature Flags
- Web UI for browsing and inspecting captured requests
- Forwards requests to real Datadog intake
- Decompresses zstd/gzip bodies for viewing
- SQLite storage for persistence
- Export captured data as JSON

## Quick Start

```bash
# Install dependencies
npm install

# Start the server
npm start

# Open Web UI
open http://localhost:8080/ui
```

## Endpoints

| SDK Feature | Local Endpoint |
|-------------|----------------|
| RUM | `POST /api/v2/rum` |
| Logs | `POST /api/v2/logs` |
| Traces | `POST /api/v2/spans` |
| Session Replay | `POST /api/v2/replay` |
| Profiling | `POST /api/v2/profile` |
| Feature Flags (exposures) | `POST /api/v2/exposures` |
| Feature Flags (assignments) | `GET /flags/*` |

## Android Configuration

### 1. Network Security Config

Allow cleartext HTTP for the local server. Create `res/xml/network_security_config.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">10.0.2.2</domain>
    </domain-config>
</network-security-config>
```

Reference it in `AndroidManifest.xml`:

```xml
<application android:networkSecurityConfig="@xml/network_security_config" ...>
```

### 2. Configure SDK Custom Endpoints

```kotlin
// For emulator, use 10.0.2.2 (maps to host machine's localhost)
private val localServerUrl = "http://10.0.2.2:8080"

val rumConfig = RumConfiguration.Builder(appId)
    .useCustomEndpoint("$localServerUrl/api/v2/rum")
    .build()

val logsConfig = LogsConfiguration.Builder()
    .useCustomEndpoint("$localServerUrl/api/v2/logs")
    .build()

val srConfig = SessionReplayConfiguration.Builder(sampleRate)
    .useCustomEndpoint("$localServerUrl/api/v2/replay")
    .build()

val traceConfig = TraceConfiguration.Builder()
    .useCustomEndpoint("$localServerUrl/api/v2/spans")
    .build()
```

## Web UI

Access the Web UI at `http://localhost:8080/ui` to:

- View all captured requests
- Filter by feature type
- Inspect request headers and body
- See forward status
- Export data for analysis

## API

- `GET /ui-api/requests` - List all requests (supports `?feature=rum&limit=100`)
- `GET /ui-api/requests/:id` - Get request details
- `GET /ui-api/stats` - Get capture statistics
- `DELETE /ui-api/requests` - Clear all captured data
- `GET /ui-api/export` - Export all data as JSON
