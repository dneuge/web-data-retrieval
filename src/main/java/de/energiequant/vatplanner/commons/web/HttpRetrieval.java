package de.energiequant.vatplanner.commons.web;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.InputStreamFactory;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpRetrieval {
    private static final Logger logger = LoggerFactory.getLogger(HttpRetrieval.class.getName());
    
    protected Duration timeout = Duration.ofSeconds(30);
    protected String userAgent = "HttpRetrieval";
    protected int maximumFollowedRedirects = 5;
    protected Map<String, InputStreamFactory> unmodifiableContentDecoderMap = null;
    protected HttpResponse httpResponse = null;
    
    private static final Pattern PATTERN_URL_PROTOCOL = Pattern.compile("^([a-z]+)://.*", Pattern.CASE_INSENSITIVE);
    private static final Set<String> supportedUrlProtocols = new TreeSet<String>(Arrays.asList(new String[]{
        // list all URL protocols in lower-case
        "http",
        "https"
    }));
    
    /**
     * Creates a new instance with a copy of this instance's configuration.
     * May return null if no copy could be created.
     * @return new instance with all configuration copied
     */
    public HttpRetrieval copyConfiguration() {
        HttpRetrieval newInstance = null;
        try {
            newInstance = this.getClass().newInstance();
        } catch (Exception ex) {
            logger.warn("Unable to create new instance of class.", ex);
            return null;
        }
        
        copyConfigurationTo(newInstance);
        
        return newInstance;
    }
    
    /**
     * Copies configuration to another instance of HttpRetrieval.
     * Result data will not be copied, so this method can be used to help on
     * instantiation of fresh objects using this instance as a prototype.
     * @param other instance to configure
     */
    public void copyConfigurationTo(final HttpRetrieval other) {
        other.setTimeout(getTimeout());
        other.setUserAgent(getUserAgent());
        other.setMaximumFollowedRedirects(getMaximumFollowedRedirects());
    }
    
    /**
     * Sets all internally used timeouts to the given duration.
     * @param timeout generic timeout duration
     */
    public void setTimeout(final Duration timeout) {
        this.timeout = timeout;
    }
    
    /**
     * Returns the generic timeout to be applied to all following requests.
     * @return generic timeout to be applied internally
     */
    public Duration getTimeout() {
        return this.timeout;
    }
    
    /**
     * Sets the user agent string to identify all requests with.
     * Null or user agent strings only consisting of white-spaces will not be
     * accepted.
     * @param userAgent complete user agent string to use
     */
    public void setUserAgent(final String userAgent) {
        if (userAgent == null) {
            logger.warn("User-agent string is required and cannot be set to null! Using previous/default value.");
            return;
        }
        
        if (userAgent.trim().isEmpty()) {
            logger.warn("User-agent string is required and cannot be set to empty/white-space string! Using previous/default value.");
            return;
        }
        
        this.userAgent = userAgent;
    }
    
    /**
     * Returns the user agent string to be used on all following requests.
     * @return user agent string to be used on requests
     */
    public String getUserAgent() {
        return this.userAgent;
    }
    
    /**
     * Sets the maximum number of redirects that should be followed.
     * @param maximumFollowedRedirects maximum number of redirects to follow
     */
    public void setMaximumFollowedRedirects(int maximumFollowedRedirects) {
        if (maximumFollowedRedirects < 0) {
            logger.warn("Attempted to set a negative number of maximum allowed redirects ({}), limiting to 0.", maximumFollowedRedirects);
            maximumFollowedRedirects = 0;
        } else if (maximumFollowedRedirects > 10) {
            logger.warn("Allowing a high number of redirects to be followed ({}), this may not make sense and should be reduced for practical reasons.", maximumFollowedRedirects);
        }
        
        this.maximumFollowedRedirects = maximumFollowedRedirects;
    }
    
    /**
     * Returns the maximum number of redirects to follow automatically on all following requests.
     * @return maximum number of redirects to follow
     */
    public int getMaximumFollowedRedirects() {
        return this.maximumFollowedRedirects;
    }
    
    /**
     * Helper method to aid in injecting a mock for testing buildHttpClient.
     * @return HttpClientBuilder to use
     */
    protected HttpClientBuilder getHttpClientBuilder() {
        return HttpClients.custom();
    }
    
    /**
     * Returns a single instance of a map of InputStreamFactory instances to be
     * used for decoding streams indexed by their HTTP Content-Encoding header.
     * The returned map should usually be unmodifiable so it can be reused
     * safely after a single initialization.
     * Content-Encoding usually indicates compression, so decoders should perform
     * gzip decompression etc.
     * @return InputStreamFactory instances to be used for content stream decoding
     */
    protected Map<String, InputStreamFactory> getContentDecoderMap() {
        // only initialize map once
        synchronized (this) {
            if (unmodifiableContentDecoderMap == null) {
                Map<String, InputStreamFactory> contentDecoderMap = new HashMap<>();
                contentDecoderMap.put("gzip", (InputStreamFactory) (InputStream instream) -> new GZIPInputStream(instream));
                unmodifiableContentDecoderMap = Collections.unmodifiableMap(contentDecoderMap);
            }
        }
        
        return unmodifiableContentDecoderMap;
    }
    
    /**
     * Builds an HttpClient instance, fully configured by the settings and
     * defaults of this instance.
     * @return fully configured HttpClient
     */
    protected HttpClient buildHttpClient() {
        int timeoutMillis = (int) getTimeout().toMillis();
        
        RequestConfig config = RequestConfig.custom()
            .setConnectTimeout(timeoutMillis)
            .setConnectionRequestTimeout(timeoutMillis)
            .setSocketTimeout(timeoutMillis)
            .setMaxRedirects(getMaximumFollowedRedirects())
            .setContentCompressionEnabled(true)
            .build();
        
        HttpClient client = getHttpClientBuilder()
            .setDefaultRequestConfig(config)
            .setUserAgent(getUserAgent())
            .setContentDecoderRegistry(getContentDecoderMap())
            .build();
        
        return client;
    }
    
    /**
     * Helper method to aid in injecting a mock for testing requestByGet.
     * @param url URL to pass on to HttpGet constructor
     * @return instance of HttpGet
     */
    protected HttpGet buildHttpGet(final CharSequence url) {
        return new HttpGet(url.toString());
    }
    
    /**
     * Checks if the given URL uses a supported protocol.
     * @param url URL to check protocol for
     * @return Is the URL's protocol supported?
     */
    protected boolean checkSupportedUrlProtocol(final CharSequence url) {
        boolean isSupportedProtocol = false;
        
        if (url == null) {
            return false;
        }
        
        Matcher matcher = PATTERN_URL_PROTOCOL.matcher(url);
        if (matcher.matches()) {
            String protocol = matcher.group(1).toLowerCase();
            isSupportedProtocol = supportedUrlProtocols.contains(protocol);
        }
        
        return isSupportedProtocol;
    }
    
    /**
     * Requests the given URL using a GET request.
     * Return value only indicates very basic network-level success, hiding any
     * Exceptions that might get thrown on lower layers. Indication of basic
     * success does not interpret the actual HTTP response, it just means that
     * we have a response that can be further interpreted by the caller (could
     * still yield HTTP errors such as 404 or 503).
     * @param url URL to request
     * @return boolean Basic network level success? (i.e. do we have an HTTP response? The return value does not interpret actual HTTP status/response!)
     */
    public boolean requestByGet(final CharSequence url) {
        httpResponse = null;
        
        if (url == null) {
            logger.warn("Attempted to perform a GET request with null as URL.");
            return false;
        }
        
        boolean isSupportedProtocol = checkSupportedUrlProtocol(url);
        if (!isSupportedProtocol) {
            logger.warn("Unsupported protocol used in URL for GET request: \"{}\"", url);
            return false;
        }
        
        logger.debug("requesting \"{}\" by GET method", url);
        
        HttpClient client = buildHttpClient();
        HttpUriRequest request = buildHttpGet(url);
        
        try {
            httpResponse = client.execute(request);
        } catch (IOException ex) {
            logger.warn("GET request to \"{}\" failed with an exception.", url, ex);
            return false;
        }
        
        return true;
    }
    
    /**
     * Returns the response body as a byte array.
     * If transfer had been compressed, this method will not return the raw
     * compressed data but instead yield the uncompressed result, so consumers
     * do not need to care about compression.
     * @return response body
     */
    public byte[] getResponseBodyBytes() {
        if (httpResponse == null) {
            return null;
        }
        
        try {
            return IOUtils.toByteArray(httpResponse.getEntity().getContent());
        } catch (IOException|UnsupportedOperationException ex) {
            logger.warn("Failed to copy bytes from HTTP response.", ex);
            return null;
        }
    }
    
    /**
     * Checks if the response had a "good" status code indicating a full
     * response.
     * @see checkCompleteContentResponseStatus for details
     * @return Did the response indicate complete content retrieval?
     */
    public boolean hasCompleteContentResponseStatus() {
        if (httpResponse == null) {
            return false;
        }
        
        int statusCode = httpResponse.getStatusLine().getStatusCode();
        
        return checkCompleteContentResponseStatus(statusCode);
    }
    
    /**
     * Checks if the given status code indicates a full response.
     * This excludes partial responses (such as 206) as the retrieved
     * bytes would not be complete.
     * @param statusCode status code to check
     * @return Does the status code indicate complete content retrieval?
     */
    protected boolean checkCompleteContentResponseStatus(final int statusCode) {
        return (statusCode >= 200) && (statusCode <= 205);
    }
}
