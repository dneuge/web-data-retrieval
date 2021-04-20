# Web Data Retrieval Helper

[![Build Status](https://travis-ci.com/dneuge/web-data-retrieval.svg?branch=master)](https://travis-ci.com/dneuge/web-data-retrieval)
[![Coverage Status](https://coveralls.io/repos/github/dneuge/web-data-retrieval/badge.svg?branch=master)](https://coveralls.io/github/dneuge/web-data-retrieval?branch=master)
[![License: MIT with dependencies](https://img.shields.io/badge/license-MIT%20w%2F%20deps-blue.svg)](LICENSE.md)

Just another small helper library for common web-based data retrieval tasks.

This library wraps [Apache HttpComponents](http://hc.apache.org/) HttpClient to make it more accessible for easier data retrieval and processing.

## Examples

### Basic GET retrieval

```java
// basic configuration
HttpRetrieval retrieval = new HttpRetrieval()
    .setUserAgent("TestClient/0.1"); // it's always nice to properly identify your program

// go fetch
boolean success = retrieval.requestByGet("https://www.energiequant.de/");
System.out.println(success ? "success" : "failed");

// let's see what we got
String body = new String(retrieval.getResponseBodyBytes());
System.out.println(body);

// let's check some more details...
System.out.println(retrieval.hasCompleteContentResponseStatus()); // Was the content transferred completely?
System.out.println(retrieval.getLastRetrievedLocation()); // What was the URL after following redirects?
System.out.println(retrieval.getResponseHeaders().getFirstByName("content-type")); // inspect content-type HTTP response header
```

### GET retrieval through promises

In addition to retrieving data directly as shown above you can also use a `CompletableFuture`: Simply instantiate a reusable `HttpPromiseBuilder` instance by providing it with a decoder `Function` to process the `HttpRetrieval` resulting from a request. Some `DefaultHttpRetrievalDecoders` are available and can easily be chained. Don't forget to provide a configuration template via `HttpPromiseBuilder#withConfiguration` and you are good to go.

The following example retrieves the content (HTTP response body) of [http://www.energiequant.de/](http://www.energiequant.de/) again and automatically decodes the data to a `String` using the character set indicated by the server (falling back to UTF-8 if unavailable). We are still interested in the final location so we wrap response processing `withMetaData` to wrap the content into a `RetrievedData` container:

```java
DefaultHttpRetrievalDecoders decoders = new DefaultHttpRetrievalDecoders();
HttpPromiseBuilder<RetrievedData<String>> builder = new HttpPromiseBuilder<RetrievedData<String>>(
    decoders.withMetaData(
        decoders.bodyAsStringWithHeaderCharacterSet(StandardCharsets.UTF_8)
    )
).withConfiguration(
    new HttpRetrieval()
        .setUserAgent("TestClient/0.1")
);

RetrievedData<String> retrievedData = builder.requestByGet("http://www.energiequant.de/").get();
System.out.println(retrievedData.getData()); // HTTP response body
System.out.println(retrievedData.getRetrievedLocation()); // final location after following all redirects
```

This becomes much more practical when actually performing some kind of repeated processing to a different target type. To keep the example simple, let's just count the number of lines on some websites by chaining a lambda after decoding the response body to a `String` and perform two requests for different URLs:

```java
DefaultHttpRetrievalDecoders decoders = new DefaultHttpRetrievalDecoders();
HttpPromiseBuilder<Integer> builder = new HttpPromiseBuilder<Integer>(
    decoders //
        .bodyAsStringWithHeaderCharacterSet(StandardCharsets.UTF_8)
        .andThen(s -> s.split("\n").length)
).withConfiguration(
    new HttpRetrieval()
        .setUserAgent("TestClient/0.1")
);

String[] urls = { "http://www.energiequant.de/", "https://www.github.com/" };
for (String url : urls) {
    System.out.println(String.format("%5d %s", builder.requestByGet(url).get(), url));
}
```

Note that for a real application you should perform proper error and exception handling and identify your application uniquely by setting a proper user agent string.


## License

The implementation and accompanying files are released under MIT license.

As this library requires runtime dependencies, further licenses apply on distribution and at runtime. Please check licenses of all dependencies (not limited to those listed on this page) and any transitive dependencies individually.

### Major Runtime Dependencies

The following dependencies have a major impact on this library's operation at runtime:

 * [Apache Commons](https://commons.apache.org/)
 * [Apache HttpComponents](https://hc.apache.org/)
 * [Simple Logging Facade for Java (SLF4J)](https://www.slf4j.org/)
