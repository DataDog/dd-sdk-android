# Test Server

A lightweight HTTP/HTTPS test server for integration testing of network libraries such as Cronet and OkHttp.

## Overview

This module provides embedded test servers that can be used in integration tests to verify network library behavior without relying on external services.

## Features

- **HTTP Server** (`TestServer`) - Plain HTTP server using Netty
- **HTTPS Server** (`SecureTestServer`) - Secure server with self-signed certificate and HTTP/2 support
- Support for all common HTTP methods: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`
- Redirect endpoints for testing redirect handling
- Configurable error endpoints for testing error scenarios

## Usage

### HTTP Server

```kotlin
val server = TestServer(httpPort = 8080)
server.start()

// Make requests to server
val url = server.httpUrl() // "http://localhost:8080"

// Clean up
server.stop()
```

### HTTPS Server

```kotlin
val server = SecureTestServer(httpsPort = 8443)
server.start()

// Get the KeyStore to configure client trust
val keyStore = server.keyStore

// Make requests to server
val url = server.httpsUrl() // "https://localhost:8443"

// Clean up
server.stop()
```

## Available Endpoints

### Method Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /get` | Returns request info as JSON |
| `POST /post` | Echoes posted body in response |
| `PUT /put` | Echoes put body in response |
| `DELETE /delete` | Returns delete confirmation |
| `PATCH /patch` | Echoes patched body in response |
| `HEAD /head` | Returns headers only |
| `OPTIONS /options` | Returns allowed methods |

### Redirect Endpoints

| Endpoint | Redirects To |
|----------|--------------|
| `GET /redirect/get` | `/get` |
| `POST /redirect/post` | `/post` |
| `PUT /redirect/put` | `/put` |
| `DELETE /redirect/delete` | `/delete` |
| `PATCH /redirect/patch` | `/patch` |
| `HEAD /redirect/head` | `/head` |
| `OPTIONS /redirect/options` | `/options` |

### Error Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /error/{code}/get` | Returns error with specified HTTP status code |
| `POST /error/{code}/post` | Returns error with specified HTTP status code |
| `PUT /error/{code}/put` | Returns error with specified HTTP status code |
| `DELETE /error/{code}/delete` | Returns error with specified HTTP status code |
| `PATCH /error/{code}/patch` | Returns error with specified HTTP status code |
| `HEAD /error/{code}/head` | Returns error with specified HTTP status code |
| `OPTIONS /error/{code}/options` | Returns error with specified HTTP status code |

**Example:** `GET /error/500/get` returns HTTP 500 Internal Server Error

## Response Format

### Success Response (Method Endpoints)

```json
{
  "method": "GET",
  "path": "/get",
  "body": null,
  "headers": {
    "Host": "localhost:8080",
    "User-Agent": "okhttp/4.12.0"
  }
}
```

### Error Response

```json
{
  "error": true,
  "statusCode": 500,
  "method": "GET",
  "message": "Internal Server Error"
}
```

## Integration with Network Libraries

### OkHttp

```kotlin
val server = TestServer(httpPort = 8080)
server.start()

val client = OkHttpClient.Builder().build()
val request = Request.Builder()
    .url("${server.httpUrl()}/get")
    .build()

val response = client.newCall(request).execute()

server.stop()
```

### OkHttp with HTTPS

```kotlin
val server = SecureTestServer(httpsPort = 8443)
server.start()

// Configure OkHttp to trust the self-signed certificate
val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
trustManagerFactory.init(server.keyStore)
val trustManagers = trustManagerFactory.trustManagers
val sslContext = SSLContext.getInstance("TLS")
sslContext.init(null, trustManagers, SecureRandom())

val client = OkHttpClient.Builder()
    .sslSocketFactory(sslContext.socketFactory, trustManagers[0] as X509TrustManager)
    .hostnameVerifier { _, _ -> true }
    .build()

val request = Request.Builder()
    .url("${server.httpsUrl()}/get")
    .build()

val response = client.newCall(request).execute()

server.stop()
```

### Cronet

Unlike OkHttp, Cronet doesn't provide a programmatic API to add custom trust stores at runtime.
Cronet relies on Android's Network Security Config for certificate trust.

**Option 1: Use Cronet's URLConnection API with custom SSLSocketFactory**

```kotlin
val server = SecureTestServer(httpsPort = 8443)
server.start()

// Configure TrustManager with the server's certificate (same as OkHttp)
val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
trustManagerFactory.init(server.keyStore)
val trustManagers = trustManagerFactory.trustManagers
val sslContext = SSLContext.getInstance("TLS")
sslContext.init(null, trustManagers, SecureRandom())

// Build Cronet engine
val cronetEngine = CronetEngine.Builder(context)
    .enableQuic(true)
    .build()

// Use Cronet's URLConnection API which respects custom SSLSocketFactory
val url = URL("${server.httpsUrl()}/get")
val connection = cronetEngine.openConnection(url) as HttpsURLConnection
connection.sslSocketFactory = sslContext.socketFactory
connection.hostnameVerifier = HostnameVerifier { _, _ -> true }

val responseCode = connection.responseCode
val responseBody = connection.inputStream.bufferedReader().readText()

server.stop()
```

**Option 2: Use HTTP for integration tests**

For testing Cronet's native `UrlRequest` API with full control, use the plain HTTP server:

```kotlin
val server = TestServer(httpPort = 8080)
server.start()

val cronetEngine = CronetEngine.Builder(context)
    .build()

val requestBuilder = cronetEngine.newUrlRequestBuilder(
    "${server.httpUrl()}/get",
    callback,
    executor
)
requestBuilder.build().start()

server.stop()
```
