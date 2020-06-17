# Datadog Sample Apps

## Getting Started

These sample apps are configured based on configuration JSON files which need to be added in `config` folder in your root directory.
For each flavor you will have to provide a config file named `[flavorName].json`.

Example of a sample app configuration file:
```json
   {
     "logsEndpoint": "YOUR PREFERRED ENDPOINT",
     "tracesEndpoint": "YOUR PREFERRED ENDPOINT",
     "rumEndpoint": "YOUR PREFERRED ENDPOINT",
     "token": "YOUR APP TOKEN",
     "appId": "YOUR RUM APP ID"
   }
```
**Note** `logsEndpoint`, `tracesEndpoint` and `rumEndpoint` are not mandatory attributes, so if you want to use the default endpoints (production ones) you just omit them in the configuration file.
