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

## SQLDelight sample database

The Kotlin app exercises the SDK's SQLDelight 1.x integration. Its schema lives in
`sample/sqldelight/src/main/sqldelight` and is generated in a JVM module because the
SQLDelight 1.x Gradle plugin does not support AGP's built-in Kotlin. Android modules
keep built-in Kotlin and the new DSL enabled, and the app consumes the generated
database classes through a project dependency.

Building the app generates the database automatically:

```shell
./gradlew :sample:kotlin:assembleUs1Debug
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

### Remote Configuration

To enable Remote Configuration, add the following attribute. You can find the ID in the Datadog UI during application onboarding.

```json
{
    "remoteConfigurationId": "YOUR REMOTE CONFIGURATION ID"
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
