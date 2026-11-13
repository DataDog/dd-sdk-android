# Datadog benchmark sample application

## Overview

This is a sample application for Datadog internal use to benchmark the overhead of SDK features.
The application is uploaded for every commit in `develop` branch to continuously monitor the
overhead of the SDK in case of any spikes in CPU usage, memory usage, or FPS.

## Getting started

When creating the synthetics test for continuous benchmarking with this application, optional intent
arguments needed to be defined to control the scenario that should be measured. For
example, if the test target is Session Replay, these are the intent arguments for the baseline test:

```json
{
  "synthetics.benchmark.scenario": "sr",
  "synthetics.benchmark.run": "baseline"
}
```

And here is the example for the instrumented test:

```json
{
  "synthetics.benchmark.scenario": "sr",
  "synthetics.benchmark.run": "instrumented"
}
```

If you want to run the application locally, create a `benchmark.json` file with the following content
under the `config/` folder in the root path:

```json

{
  "token": "MY_CLIENT_TOKEN",
  "rumApplicationId": "MY_RUM_APPLICATION_ID",
  "apiKey": "MY_API_KEY",
  "applicationKey": "MY_APPLICATION_KEY"
}

```


## Rick & Morty API fallback

The RumAuto screens load characters, locations, episodes and avatars from the public
[Rick & Morty API](https://rickandmortyapi.com/api). The API is rate limited (HTTP 429) and sometimes
unavailable, so the `Timeseries` scenario (`"synthetics.benchmark.scenario": "timeseries"`) uses a local
fallback in both baseline and instrumented runs. Other scenarios always use the real API.

How it works:

- `RickAndMortyLocalServer` is an in-process Ktor (CIO) server on `127.0.0.1` with an ephemeral port. It
  serves the snapshot bundled in `src/main/assets/rickandmorty/` with the same routes as the real API. API
  responses are delayed by 100 ms and avatars by 200 ms to stay close to real network timings. It is
  started in the background from `LaunchActivity`.
- `RickAndMortyFallbackInterceptor` handles requests to `rickandmortyapi.com`. It runs before
  `DatadogInterceptor`. When a request fails with an I/O error or a non-2xx status, the interceptor retries
  it once against the local server, with the same path and query.
- After the first API request that is served locally, all later API requests go to the local server
  directly for the rest of the process. Avatar requests always try the real host first.
- The snapshot contains all characters, locations and episodes, but only the avatars of the first five
  character pages (ids 1–100). The local server returns 404 for other avatars.
- If the local server can't start or the local request fails, the original response or error is
  returned.

When the fallback is used for the first time, the global RUM attribute
`benchmark.rick_and_morty_fallback = true` is set and a warning is logged. RUM events sent after that
point carry the attribute, so you can filter or split dashboards by it. A local retry is tracked as a
separate RUM resource with a `http://127.0.0.1:<port>/...` URL, next to the failed request to
`rickandmortyapi.com`.

To refresh the snapshot, run from the repository root:

```sh
python3 sample/benchmark/scripts/fetch_rick_and_morty.py
```

Add `--force` to download avatars that already exist again, or `--output-dir <dir>` to write the
snapshot to another folder.
