# Datadog Sample Apps

## Getting Started

These sample apps are configured based on configuration JSON files which need to be added in `config` folder in your root directory.
For each flavor, you must provide a config file named `[flavorName].json`. By default, flavors should match one of the existing sites in the `DatadogSite` enum (for example: `us1`, `us1_fed`, `us3`, `us5`, `eu1`).

Example of a minimal sample app configuration file:

```json
{
    "token": "YOUR APP TOKEN",
    "rumApplicationId": "YOUR RUM APPLICATION ID"
}
```

## Advanced configuration

### Remote API

To allow the download of logs (to test the `Data List` screen), add the following attributes. You can find them in the `Organization Settings` page in Datadog.

```json
{
    "apiKey": "YOUR API ID",
    "applicationKey": "YOUR APPLICATION KEY"
}
```

### Staging

If you need to target a site that is not part of the `DatadogSite` enum, configure custom endpoints using the following attributes:

```json
{
    "logsEndpoint": "http://api.example.com/logs",
    "tracesEndpoint": "http://api.example.com/spans",
    "rumEndpoint": "http://api.example.com/rum"
}
```

### Internal Monitoring

If you want to enable the internal monitoring, you need to add the relevant `clientToken` in the following attribute: 

```json
{
    "internalMonitoringToken": "INTERNAL TOKEN"
}
```
