## Integrated libraries
 
### Coil
 
If you use Coil to load images in your application, take a look at Datadog's [dedicated Coil library][1].
 
### Fresco
 
If you use Fresco to load images in your application, take a look at Datadog's [dedicated Fresco library][2].
 
### Glide
 
If you use Glide to load images in your application, take a look at Datadog's [dedicated Glide library][3].
 
### Picasso
 
If you use Picasso, let it use your `OkHttpClient` for RUM and APM information about network requests made by Picasso.
 
```kotlin
val picasso = Picasso.Builder(context)
        .downloader(OkHttp3Downloader(okHttpClient))
        // …
        .build()
Picasso.setSingletonInstance(picasso)
```
 
### Retrofit
 
If you use Retrofit, let it use your `OkHttpClient` for RUM and APM information about network requests made with Retrofit.
 
```kotlin
val retrofitClient = Retrofit.Builder()
        .client(okHttpClient)
        // …
        .build()
```
 
### SQLDelight
 
If you use SQLDelight, take a look at Datadog's [dedicated SQLDelight library][4].
 
### SQLite
 
Following SQLiteOpenHelper's [generated API documentation][5], you only have to provide the implementation of the
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
 
If you use Apollo, let it use your `OkHttpClient` for RUM and APM information about all the queries performed through Apollo client.
 
```kotlin
val apolloClient =  ApolloClient.builder()
         .okHttpClient(okHttpClient)
         .serverUrl(<APOLLO_SERVER_URL>)
         .build()
```

## Further reading

{{< partial name="whats-next/whats-next.html" >}}

[1]: https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-coil
[2]: https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-fresco
[3]: https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-glide
[4]: https://github.com/DataDog/dd-sdk-android/tree/master/dd-sdk-android-sqldelight
[5]: https://developer.android.com/reference/android/database/sqlite/SQLiteOpenHelper
