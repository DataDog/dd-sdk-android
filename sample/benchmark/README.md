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

