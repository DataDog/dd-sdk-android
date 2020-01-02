# Datadog SDK for Android

> A client-side Android library to interact with Datadog.

## Getting Started

See the dedicated [Datadog Android log collection documentation](http://docs.datadoghq.com/logs/log_collection/android) to learn how to forward logs from your Androind application to Datadog.

### Migrating from earlier versions

If you were a user of the SDK version `0.2.5` or lower, take a look at our [Migration Guide](docs/Migrating_To_1.0.0.md).

### Integrating with Timber

If you're existing codebase is already using Timber, you can migrate to Datadog easily by using our [dedicated library](dd-sdk-android-timber/README.md).

## Looking up your logs

When you open your console in Datadog, navigate to the Logs section, and in the search bar, type `source:mobile`. This will filter your logs to only show the ones coming from mobile applications (Android and iOS).

![Datadog Mobile Logs](docs/images/screenshot.png)

## Contributing

Pull requests are welcome, but please open an issue first to discuss what you would like to change. For more information, read the [Contributing Guide](CONTRIBUTING.md).

## License

[Apache License, v2.0](LICENSE)
