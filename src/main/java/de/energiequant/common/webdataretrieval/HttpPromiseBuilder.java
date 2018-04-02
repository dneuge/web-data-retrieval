package de.energiequant.common.webdataretrieval;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * This builder provides an easy asynchronous way to retrieve content via HTTP.
 * <p>
 * The decoder given at construction time is being fed with the
 * internally used {@link HttpRetrieval} instance upon success and is expected
 * to decode the retrieval's response body to the inferred return type T.
 * </p>
 * <p>
 * Retrieval errors and exceptions should be expected and handled through
 * default API of {@link CompletableFuture}.
 * </p>
 * @param <T> return type of {@link CompletableFuture}, result of given decoder
 */
public class HttpPromiseBuilder<T> {
    private volatile HttpRetrieval lastProvidedConfigurationTemplate = createDefaultConfigurationTemplate();
    private final Function<HttpRetrieval, T> decoder;
    
    /**
     * Constructs a new builder applying the given decoder to all requests.
     * @param decoder receives all successful {@link HttpRetrieval}s for decoding to type T; should be stateless in order to reuse builder and apply concurrently
     */
    public HttpPromiseBuilder(Function<HttpRetrieval, T> decoder) {
        this.decoder = decoder;
    }
    
    /**
     * Subsequently initiated requests will use the provided configuration.
     * <p>
     * Builder can be reconfigured to apply another configuration at any time.
     * Already initiated requests will remain unchanged.
     * </p>
     * <p>
     * If left unconfigured, default configuration of {@link HttpRetrieval}
     * will be applied.
     * </p>
     * <p>
     * The configuration object itself should remain unchanged upon supplying
     * it to a builder (builder doesn't copy the configuration, thus it is not
     * being isolated from concurrent modification).
     * </p>
     * @param configurationTemplate configuration to apply to subsequently initiated requests; configuration object must not change concurrently
     * @return same builder for method-chaining
     */
    public HttpPromiseBuilder<T> withConfiguration(HttpRetrieval configurationTemplate) {
        if (configurationTemplate == null) {
            throw new IllegalArgumentException("Configuration template must not be null!");
        }
        
        this.lastProvidedConfigurationTemplate = configurationTemplate;
        
        return this;
    }
    
    /**
     * The returned future retrieves the decoded content from specified URL.
     * Failure to retrieve or decode the content (including exceptions of any
     * kind) should be expected and can easily be handled through default
     * {@link CompletableFuture} API.
     * @param url URL of content to be retrieved
     * @return future retrieving decoded content from specified URL
     */
    public CompletableFuture<T> requestByGet(CharSequence url) {
        final HttpRetrieval configurationTemplate = this.lastProvidedConfigurationTemplate;
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(new Supplier<T>(){
            @Override
            public T get() {
                HttpRetrieval retrieval = createRetrieval();
                configurationTemplate.copyConfigurationTo(retrieval);
                
                boolean success = retrieval.requestByGet(url);
                
                if (!success) {
                    throw new RuntimeException("GET request for "+url+" failed on network level.");
                }
                
                if (!retrieval.hasCompleteContentResponseStatus()) {
                    throw new RuntimeException("GET request for "+url+" returned incomplete content by HTTP response status code.");
                }
                
                T decoded = decoder.apply(retrieval);
                
                return decoded;
            }
        });
        
        return future;
    }
    
    /**
     * Creates a new instance of {@link HttpRetrieval}.
     * Required for unit-testing.
     * @return new instance of {@link HttpRetrieval}
     */
    HttpRetrieval createRetrieval() {
        return new HttpRetrieval();
    }
    
    /**
     * Creates a default configuration template for a {@link HttpRetrieval}.
     * Required for unit-testing.
     * @return instance of {@link HttpRetrieval} used as configuration template
     */
    HttpRetrieval createDefaultConfigurationTemplate() {
        return new HttpRetrieval();
    }
}
