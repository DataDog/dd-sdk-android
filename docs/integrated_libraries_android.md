In## Integrated Libraries
 
### Coil
 
If you use Coil to load images in your application, take a look at Datadog's [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-coil).
 
### Fresco
 
If you use Fresco to load images in your application, take a look at Datadog's [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-fresco).
 
### Glide
 
If you use Glide to load images in your application, take a look at our [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-glide).
 
### Picasso
 
If you use Picasso, let it use your `OkHttpClient`, and you'll get RUM and APM information about network requests made by Picasso.
 
```kotlin
        val picasso = Picasso.Builder(context)
                .downloader(OkHttp3Downloader(okHttpClient))
                // …
                .build()
        Picasso.setSingletonInstance(picasso)
```
 
### Retrofit
 
If you use Retrofit, let it use your `OkHttpClient`, and you'll get RUM and APM information about network requests made with Retrofit.
 
```kotlin
        val retrofitClient = Retrofit.Builder()
                .client(okHttpClient)
                // …
                .build()
```
 
### SQLDelight
 
If you use SQLDelight, take a look at our [dedicated library](https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-sqldelight).
 
### SQLite
 
Following SQLiteOpenHelper's [Generated API documentation][8], you only have to provide the implementation of the
DatabaseErrorHandler -> `DatadogDatabaseErrorHandler` in the constructor.
 
Doing this detects whenever a database is corrupted and sends a relevant
RUM error event for it.
 
```kotlint
   class <YourOwnSqliteOpenHelper>: SqliteOpenHelper(<Context>, 
                                                     <DATABASE_NAME>, 
                                                     <CursorFactory>, 
                                                     <DATABASE_VERSION>, 
                                                     DatadogDatabaseErrorHandler()) {
                                // …
   
   }
```
 
### Apollo (GraphQL)
 
If you use Apollo, let it use your `OkHttpClient`, and you'll get RUM and APM information about all the queries performed through Apollo client.
 
```kotlin
        val apolloClient =  ApolloClient.builder()
                 .okHttpClient(okHttpClient)
                 .serverUrl(<APOLLO_SERVER_URL>)
                 .build()
```

## Further Reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://github.com/DataDog/dd-sdk-android
[2]: https://app.datadoghq.com/rum/create
[3]: https://docs.datadoghq.com/account_management/api-app-keys/#api-keys
[4]: https://docs.datadoghq.com/account_management/api-app-keys/#client-tokens
[5]: https://square.github.io/okhttp/interceptors/
[6]: https://square.github.io/okhttp/events/
