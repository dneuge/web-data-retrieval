package de.energiequant.vatplanner.commons.web;

import com.google.common.collect.ImmutableList;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.InputStreamFactory;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import static org.hamcrest.Matchers.*;
import org.hamcrest.junit.ExpectedException;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.*;
import org.mockito.invocation.Invocation;
import uk.org.lidalia.slf4jext.Level;
import uk.org.lidalia.slf4jtest.LoggingEvent;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

@RunWith(DataProviderRunner.class)
public class HttpRetrievalTest {
    TestLogger testLogger = TestLoggerFactory.getTestLogger(HttpRetrieval.class);
    
    /**
     * Used for testing copy creation.
     */
    public static class HttpRetrievalDerived extends HttpRetrieval {}
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    
    @Before
    public void clearLog() {
        testLogger.clearAll();
    }
    
    @DataProvider
    public static String[] dataProviderUrlsWithSupportedProtocols() {
        return new String[] {
            "http://a.b/",
            "http://a.b.de/a.html",
            "HTTP://a.b.de/a.html",
            "HtTp://a.b.de/a.html",
            "https://a.b.de/a.html"
        };
    }
    
    @DataProvider
    public static String[] dataProviderUrlsWithoutSupportedProtocols() {
        return new String[] {
            null,
            "",
            "   ",
            "www.somewhere.com",
            "www.somewhere.com/abc.php",
            "http:/www.abc.de/",
            "https:/www.abc.de/",
            "http:www.abc.de",
            "https:www.abc.de",
            "http/www.abc.de/",
            "https/www.abc.de/",
            "http:www.abc.de/",
            "https:www.abc.de/"
        };
    }
    
    @DataProvider
    public static Object[] dataProviderCompleteContentResponseStatusCodes() {
        return new Integer[] {
            200,
            201,
            202,
            203,
            204,
            205
        };
    }
    
    @DataProvider
    public static Object[] dataProviderIncompleteContentResponseStatus() {
        return new Integer[] {
            0,
            301,
            302,
            304,
            307,
            403,
            404,
            410,
            502,
            503,
            599,
            999,
            200123
        };
    }
    
    /**
     * Extracts the default request configuration from a collection of
     * invocations.
     * @param invocations invocations to extract parameter from
     * @return default request configuration as passed by invocation parameter
     */
    private RequestConfig getRequestConfigFromInvocations(final Collection<Invocation> invocations) {
        List<RequestConfig> setRequestConfigs = invocations.stream()
            .filter((Invocation t) -> t.getMethod().getName().equals("setDefaultRequestConfig"))
            .map((Invocation t) -> (RequestConfig) t.getArgument(0))
            .collect(Collectors.toList());
        
        assertThat(setRequestConfigs, hasSize(1));
        
        RequestConfig requestConfig = setRequestConfigs.iterator().next();
        
        return requestConfig;
    }
    
    @Test
    public void testCopyConfiguration_anyCall_copiesConfigurationToNewInstance() {
        HttpRetrieval spyHttpRetrieval = spy(HttpRetrieval.class);
        
        HttpRetrieval newInstance = spyHttpRetrieval.copyConfiguration();
        
        verify(spyHttpRetrieval).copyConfigurationTo(same(newInstance));
    }
    
    @Test
    public void testCopyConfiguration_derivedClass_returnsNewInstanceOfDerivedClass() {
        HttpRetrieval original = new HttpRetrievalDerived();
        
        HttpRetrieval copied = original.copyConfiguration();
        
        assertThat(copied, is(instanceOf(HttpRetrievalDerived.class)));
    }
    
    @Test
    public void testCopyConfiguration_inaccessibleDerivedClass_returnsNull() {
        HttpRetrieval original = new HttpRetrieval(){};
        
        HttpRetrieval res = original.copyConfiguration();
        
        assertThat(res, is(nullValue()));
    }
    
    @Test
    public void testCopyConfiguration_inaccessibleDerivedClass_logsWarning() {
        HttpRetrieval original = new HttpRetrieval(){};
        
        original.copyConfiguration();
        
        // it seems we can not easily compare events which wrap a Throwable :(
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents().stream()
            .filter((LoggingEvent t) -> t.getLevel() == Level.WARN)
            .collect(Collectors.toList());
        assertThat(loggingEvents.size(), is(1));
        LoggingEvent actualEvent = loggingEvents.iterator().next();
        assertThat(actualEvent.getMessage(), is("Unable to create new instance of class."));
    }
    
    @Test
    public void testCopyConfigurationTo_setTimeout_invokesSetterWithSameArgument() {
        Duration timeout = Duration.ofMillis(12345);
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        HttpRetrieval mock = mock(HttpRetrieval.class);
        httpRetrieval.setTimeout(timeout);
        
        httpRetrieval.copyConfigurationTo(mock);
        
        verify(mock).setTimeout(timeout);
    }

    @Test
    public void testCopyConfigurationTo_uninitializedTimeout_usesGetter() {
        Duration timeout = Duration.ofMillis(321321);
        HttpRetrieval sourceSpy = spy(HttpRetrieval.class);
        when(sourceSpy.getTimeout()).thenReturn(timeout);
        HttpRetrieval targetMock = mock(HttpRetrieval.class);
        
        sourceSpy.copyConfigurationTo(targetMock);
        
        verify(sourceSpy).getTimeout();
    }

    @Test
    public void testCopyConfigurationTo_setUserAgent_invokesSetterWithSameArgument() {
        String userAgent = "testCopyConfigurationTo_setUserAgent_invokesSetterWithSameArgument";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(userAgent);
        HttpRetrieval mock = mock(HttpRetrieval.class);
        
        httpRetrieval.copyConfigurationTo(mock);
        
        verify(mock).setUserAgent(userAgent);
    }

    @Test
    public void testCopyConfigurationTo_uninitializedUserAgent_usesGetter() {
        String userAgent = "testCopyConfigurationTo_uninitializedUserAgent_usesGetter";
        HttpRetrieval sourceSpy = spy(HttpRetrieval.class);
        when(sourceSpy.getUserAgent()).thenReturn(userAgent);
        HttpRetrieval targetMock = mock(HttpRetrieval.class);
        
        sourceSpy.copyConfigurationTo(targetMock);
        
        verify(sourceSpy).getUserAgent();
    }

    @Test
    public void testCopyConfigurationTo_setMaximumFollowedRedirects_invokesSetterWithSameArgument() {
        int numRedirects = 42;
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(numRedirects);
        HttpRetrieval mock = mock(HttpRetrieval.class);
        
        httpRetrieval.copyConfigurationTo(mock);
        
        verify(mock).setMaximumFollowedRedirects(numRedirects);
    }

    @Test
    public void testCopyConfigurationTo_uninitializedMaximumFollowedRedirects_usesGetter() {
        int numRedirects = 42;
        HttpRetrieval sourceSpy = spy(HttpRetrieval.class);
        when(sourceSpy.getMaximumFollowedRedirects()).thenReturn(numRedirects);
        HttpRetrieval targetMock = mock(HttpRetrieval.class);
        
        sourceSpy.copyConfigurationTo(targetMock);
        
        verify(sourceSpy).getMaximumFollowedRedirects();
    }

    @Test
    public void testSetTimeout_anyValue_appliesToInternalAttribute() {
        Duration timeout = Duration.ofMillis(54321);
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setTimeout(timeout);
        
        assertThat(httpRetrieval.timeout, is(timeout));
    }

    @Test
    public void testGetTimeout_anyValue_returnsSetTimeout() {
        Duration expectedTimeout = Duration.ofMillis(654654);
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setTimeout(expectedTimeout);
        
        Duration actualTimeout = httpRetrieval.getTimeout();
        
        assertThat(actualTimeout, is(expectedTimeout));
    }

    @Test
    public void testGetTimeout_initially_returns30s() {
        Duration expectedTimeout = Duration.ofSeconds(30);
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        Duration actualTimeout = httpRetrieval.getTimeout();
        
        assertThat(actualTimeout, is(expectedTimeout));
    }

    @Test
    public void testSetUserAgent_notNull_appliesToInternalAttribute() {
        String expectedUserAgent = "testSetUserAgent_notNull_appliesToInternalAttribute";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setUserAgent(expectedUserAgent);
        
        assertThat(httpRetrieval.userAgent, is(expectedUserAgent));
    }

    @Test
    public void testSetUserAgent_null_doesNotApply() {
        String originalUserAgent = "not null";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(originalUserAgent);
        
        httpRetrieval.setUserAgent(null);
        
        assertThat(httpRetrieval.userAgent, is(originalUserAgent));
    }

    @Test
    public void testSetUserAgent_null_logsWarning() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setUserAgent(null);
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("User-agent string is required and cannot be set to null! Using previous/default value.");
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testSetUserAgent_whitespaceString_doesNotApply() {
        String originalUserAgent = "not white-space";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(originalUserAgent);
        
        httpRetrieval.setUserAgent("      ");
        
        assertThat(httpRetrieval.userAgent, is(originalUserAgent));
    }

    @Test
    public void testSetUserAgent_whitespaceString_logsWarning() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setUserAgent("      ");
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("User-agent string is required and cannot be set to empty/white-space string! Using previous/default value.");
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testGetUserAgent_anyValue_returnsSetUserAgent() {
        String expectedUserAgent = "testGetUserAgent_anyValue_returnsSetUserAgent";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(expectedUserAgent);
        
        String actualUserAgent = httpRetrieval.getUserAgent();
        
        assertThat(actualUserAgent, is(expectedUserAgent));
    }
    
    @Test
    public void testGetUserAgent_initially_returnsDefaultUserAgent() {
        String expectedUserAgent = "HttpRetrieval";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        String actualUserAgent = httpRetrieval.getUserAgent();
        
        assertThat(actualUserAgent, is(expectedUserAgent));
    }
    
    @Test
    public void testSetMaximumFollowedRedirects_positiveNumber_appliesToInternalAttribute() {
        int expectedNumRedirects = 123;
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setMaximumFollowedRedirects(expectedNumRedirects);
        
        assertThat(httpRetrieval.maximumFollowedRedirects, is(expectedNumRedirects));
    }
    
    @Test
    public void testSetMaximumFollowedRedirects_zero_appliesToInternalAttribute() {
        int expectedNumRedirects = 0;
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(10);
        
        httpRetrieval.setMaximumFollowedRedirects(expectedNumRedirects);
        
        assertThat(httpRetrieval.maximumFollowedRedirects, is(expectedNumRedirects));
    }
    
    @Test
    public void testSetMaximumFollowedRedirects_negativeNumber_appliesAsZero() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(100);
        
        httpRetrieval.setMaximumFollowedRedirects(-1);
        
        assertThat(httpRetrieval.maximumFollowedRedirects, is(0));
    }
    
    @Test
    public void testSetMaximumFollowedRedirects_negativeNumber_logsWarning() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setMaximumFollowedRedirects(-1);
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("Attempted to set a negative number of maximum allowed redirects ({}), limiting to 0.", -1);
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    public void testSetMaximumFollowedRedirects_exactly10_doesNotLogWarning() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setMaximumFollowedRedirects(10);
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        assertThat(loggingEvents, is(empty()));
    }
    
    @Test
    public void testSetMaximumFollowedRedirects_moreThan10_logsWarning() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.setMaximumFollowedRedirects(11);
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("Allowing a high number of redirects to be followed ({}), this may not make sense and should be reduced for practical reasons.", 11);
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    public void testGetMaximumFollowedRedirects_anyValue_returnsSetMaximumFollowedRedirects() {
        int expected = 512;
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(expected);
        
        int actual = httpRetrieval.getMaximumFollowedRedirects();
        
        assertThat(actual, is(expected));
    }
    
    @Test
    public void testGetMaximumFollowedRedirects_initially_returns5() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        int actual = httpRetrieval.getMaximumFollowedRedirects();
        
        assertThat(actual, is(5));
    }

    @Test
    public void testBuildHttpClient_initially_returnsNotNull() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        HttpClient res = httpRetrieval.buildHttpClient();
        
        assertThat(res, is(not(nullValue())));
    }

    @Test
    public void testBuildHttpClient_setTimeout_appliedToDefaultRequestConfigConnectTimeout() {
        int expectedTimeout = 121314;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setTimeout(Duration.ofMillis(expectedTimeout));
        
        spy.buildHttpClient();
        
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        int actualTimeout = requestConfig.getConnectTimeout();
        assertThat(actualTimeout, is(expectedTimeout));
    }

    @Test
    public void testBuildHttpClient_setTimeout_appliedToDefaultRequestConfigConnectionRequestTimeout() {
        int expectedTimeout = 321321;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setTimeout(Duration.ofMillis(expectedTimeout));
        
        spy.buildHttpClient();
        
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        int actualTimeout = requestConfig.getConnectionRequestTimeout();
        assertThat(actualTimeout, is(expectedTimeout));
    }

    @Test
    public void testBuildHttpClient_setTimeout_appliedToDefaultRequestConfigSocketTimeout() {
        int expectedTimeout = 9876;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setTimeout(Duration.ofMillis(expectedTimeout));
        
        spy.buildHttpClient();
        
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        int actualTimeout = requestConfig.getSocketTimeout();
        assertThat(actualTimeout, is(expectedTimeout));
    }

    @Test
    public void testBuildHttpClient_setMaximumFollowedRedirects_appliedToDefaultRequestConfig() {
        int expectedMaximum = 42;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setMaximumFollowedRedirects(expectedMaximum);
        
        spy.buildHttpClient();
        
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        int actualMaximum = requestConfig.getMaxRedirects();
        assertThat(actualMaximum, is(expectedMaximum));
    }
    
    @Test
    public void testBuildHttpClient_anyConfig_allowsCompression() {
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        
        spy.buildHttpClient();
        
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        boolean isEnabled = requestConfig.isContentCompressionEnabled();
        assertThat(isEnabled, is(true));
    }
    
    @Test
    public void testBuildHttpClient_setUserAgent_appliedToBuilder() {
        String expectedUserAgent = "Expected UA/123.45.6.7";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setUserAgent(expectedUserAgent);
        
        spy.buildHttpClient();
        
        verify(spyBuilder).setUserAgent(expectedUserAgent);
    }
    
    @Test
    public void testBuildHttpClient_anyConfig_setsContentDecoderRegistry() {
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        Map<String, InputStreamFactory> expectedMap = spy.getContentDecoderMap();
        
        spy.buildHttpClient();
        
        // NOTE: this test relies on getContentDecoderMap returning the same
        //       instance for every call
        verify(spyBuilder).setContentDecoderRegistry(expectedMap);
    }
    
    @Test
    public void testGetContentDecoderMap_calledTwice_returnsSameInstance() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        Map<String, InputStreamFactory> firstMap = httpRetrieval.getContentDecoderMap();
        
        Map<String, InputStreamFactory> secondMap = httpRetrieval.getContentDecoderMap();
        
        assertThat(secondMap, is(sameInstance(firstMap)));
    }
    
    @Test
    public void testGetContentDecoderMap_initially_isUnmodifiable() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        Map<String, InputStreamFactory> map = httpRetrieval.getContentDecoderMap();
        
        thrown.expect(UnsupportedOperationException.class);
        map.put("test", null);
    }
    
    @Test
    public void testGetContentDecoderMap_initially_containsGzipInputStreamFactory() throws IOException {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        Map<String, InputStreamFactory> map = httpRetrieval.getContentDecoderMap();
        
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[0]);
        InputStreamFactory factory = map.get("gzip");
        thrown.expect(EOFException.class);
        InputStream factoredInputStream = factory.create(byteArrayInputStream);
        assertThat(factoredInputStream, is(instanceOf(GZIPInputStream.class)));
    }
    
    @Test
    public void testRequestByGet_supportedProtocol_invokesBuildHttpClient() {
        HttpRetrieval spy = spy(HttpRetrieval.class);
        when(spy.buildHttpClient()).thenReturn(mock(HttpClient.class));
        
        spy.requestByGet("http://a.local/");
        
        // mocking a method is recorded as an invocation, thus we expect to see
        // n+1 invocations when mocking a method, only one should usually occur
        verify(spy, times(2)).buildHttpClient();
    }

    @Test
    public void testRequestByGet_supportedProtocol_invokesBuildHttpGetPassingUrl() {
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mock(HttpGet.class));
        
        spy.requestByGet(url);
        
        verify(spy).buildHttpGet(url);
    }

    @Test
    public void testRequestByGet_supportedProtocol_executesHttpGet() throws IOException {
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        
        spy.requestByGet(url);
        
        verify(mockClient).execute(mockGet);
    }

    @Test
    public void testRequestByGet_supportedProtocol_logsDebug() throws IOException {
        String url = "http://b.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenReturn(mock(HttpResponse.class));
        
        spy.requestByGet(url);
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.debug("requesting \"{}\" by GET method", url);
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    public void testRequestByGet_networkLevelSuccess_returnsTrue() throws IOException {
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenReturn(mock(HttpResponse.class));
        
        boolean res = spy.requestByGet(url);
        
        assertThat(res, is(true));
    }

    @Test
    public void testRequestByGet_networkLevelSuccess_storesResponseInternally() throws IOException {
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        HttpResponse mockResponse = mock(HttpResponse.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenReturn(mockResponse);
        
        spy.requestByGet(url);
        
        assertThat(spy.httpResponse, is(mockResponse));
    }

    @Test
    public void testRequestByGet_unsupportedProtocol_clearsPreviousResponse() throws IOException {
        String url = "";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        spy.httpResponse = mock(HttpResponse.class);
        
        spy.requestByGet(url);
        
        assertThat(spy.httpResponse, is(nullValue()));
    }

    @Test
    public void testRequestByGet_nullUrl_clearsPreviousResponse() throws IOException {
        String url = null;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        spy.httpResponse = mock(HttpResponse.class);
        
        spy.requestByGet(url);
        
        assertThat(spy.httpResponse, is(nullValue()));
    }

    @Test
    public void testRequestByGet_retrievalFailsWithException_clearsPreviousResponse() throws IOException {
        String url = "";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenThrow(IOException.class);
        spy.httpResponse = mock(HttpResponse.class);
        
        spy.requestByGet(url);
        
        assertThat(spy.httpResponse, is(nullValue()));
    }

    @Test
    public void testRequestByGet_retrievalFailsWithException_returnsFalse() throws IOException {
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenThrow(IOException.class);
        
        boolean res = spy.requestByGet(url);
        
        assertThat(res, is(false));
    }

    @Test
    public void testRequestByGet_retrievalFailsWithException_logsWarning() throws IOException {
        Class expectedThrowable = IOException.class;
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        HttpClient mockClient = mock(HttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenThrow(expectedThrowable);
        
        spy.requestByGet(url);
        
        // it seems we can not easily compare events which wrap a Throwable :(
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents().stream()
            .filter((LoggingEvent t) -> t.getLevel() == Level.WARN)
            .collect(Collectors.toList());
        assertThat(loggingEvents.size(), is(1));
        LoggingEvent actualEvent = loggingEvents.iterator().next();
        assertThat(actualEvent.getMessage(), is("GET request to \"{}\" failed with an exception."));
        ImmutableList<Object> arguments = actualEvent.getArguments();
        assertThat(arguments.size(), is(1));
        String actualArgument = (String) arguments.get(0);
        assertThat(actualArgument, is(url));
        Throwable actualThrowable = actualEvent.getThrowable().get();
        assertThat(actualThrowable, is(instanceOf(expectedThrowable)));
    }

    @Test
    public void testRequestByGet_nullUrl_returnsFalse() throws IOException {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        boolean res = httpRetrieval.requestByGet(null);
        
        assertThat(res, is(false));
    }

    @Test
    public void testRequestByGet_nullUrl_logsWarning() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        httpRetrieval.requestByGet(null);
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("Attempted to perform a GET request with null as URL.");
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testRequestByGet_unsupportedProtocol_returnsFalse() {
        String url = "whats-this://clearly.not.supported/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        when(spy.checkSupportedUrlProtocol(url)).thenReturn(false);
        
        boolean res = spy.requestByGet(url);
        
        assertThat(res, is(false));
    }

    @Test
    public void testRequestByGet_unsupportedProtocol_logsWarning() {
        String url = "whats-this://clearly.not.supported/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        when(spy.checkSupportedUrlProtocol(url)).thenReturn(false);
        
        spy.requestByGet(url);
        
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("Unsupported protocol used in URL for GET request: \"{}\"", url);
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    @UseDataProvider("dataProviderUrlsWithSupportedProtocols")
    public void checkSupportedUrlProtocol_supportedProtocols_returnsTrue(final String url) {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        boolean res = httpRetrieval.checkSupportedUrlProtocol(url);
        
        assertThat(res, is(true));
    }
    
    @Test
    @UseDataProvider("dataProviderUrlsWithoutSupportedProtocols")
    public void checkSupportedUrlProtocol_noSupportedProtocol_returnsFalse(final String url) {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        boolean res = httpRetrieval.checkSupportedUrlProtocol(url);
        
        assertThat(res, is(false));
    }

    @Test
    public void testGetResponseBodyBytes_nullResponse_returnsNull() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = null;
        
        byte[] bytes = httpRetrieval.getResponseBodyBytes();
        
        assertThat(bytes, is(nullValue()));
    }

    @Test
    public void testGetResponseBodyBytes_ioExceptionThrown_returnsNull() throws IOException {
        Exception expectedException = new IOException("expected exception");
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = mock(HttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockEntity.getContent()).thenThrow(expectedException);
        when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
        
        byte[] bytes = httpRetrieval.getResponseBodyBytes();
        
        assertThat(bytes, is(nullValue()));
    }

    @Test
    public void testGetResponseBodyBytes_unsupportedOperationExceptionThrown_returnsNull() throws IOException {
        Exception expectedException = new UnsupportedOperationException("expected exception");
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = mock(HttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockEntity.getContent()).thenThrow(expectedException);
        when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
        
        byte[] bytes = httpRetrieval.getResponseBodyBytes();
        
        assertThat(bytes, is(nullValue()));
    }

    @Test
    public void testGetResponseBodyBytes_ioExceptionThrown_logsWarning() throws IOException {
        Exception expectedException = new IOException("expected exception");
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = mock(HttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockEntity.getContent()).thenThrow(expectedException);
        when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
        
        httpRetrieval.getResponseBodyBytes();
        
        // it seems we can not easily compare events which wrap a Throwable :(
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        assertThat(loggingEvents.size(), is(1));
        LoggingEvent actualEvent = loggingEvents.iterator().next();
        assertThat(actualEvent.getMessage(), is("Failed to copy bytes from HTTP response."));
        Throwable actualThrowable = actualEvent.getThrowable().get();
        assertThat(actualThrowable, is(expectedException));
    }

    @Test
    public void testGetResponseBodyBytes_unsupportedOperationExceptionThrown_logsWarning() throws IOException {
        Exception expectedException = new UnsupportedOperationException("expected exception");
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = mock(HttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockEntity.getContent()).thenThrow(expectedException);
        when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
        
        httpRetrieval.getResponseBodyBytes();
        
        // it seems we can not easily compare events which wrap a Throwable :(
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        assertThat(loggingEvents.size(), is(1));
        LoggingEvent actualEvent = loggingEvents.iterator().next();
        assertThat(actualEvent.getMessage(), is("Failed to copy bytes from HTTP response."));
        Throwable actualThrowable = actualEvent.getThrowable().get();
        assertThat(actualThrowable, is(expectedException));
    }

    @Test
    public void testGetResponseBodyBytes_withResponse_returnsBodyBytes() throws IOException {
        byte[] expectedBytes = new byte[]{
            'A', 'B', 'C', '\n', '1', '2', '3', 0, 1, 2, 3
        };
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = mock(HttpResponse.class);
        HttpEntity mockEntity = mock(HttpEntity.class);
        when(mockEntity.getContent()).thenReturn(new ByteArrayInputStream(expectedBytes));
        when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
        
        byte[] actualBytes = httpRetrieval.getResponseBodyBytes();
        
        assertThat(actualBytes, is(equalTo(expectedBytes)));
    }

    @Test
    public void testHasCompleteContentResponseStatus_nullResponse_returnsFalse() {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = null;
        
        boolean res = httpRetrieval.hasCompleteContentResponseStatus();
        
        assertThat(res, is(false));
    }
    
    @Test
    public void testHasCompleteContentResponseStatus_withResponse_queriesCheckCompleteContentResponseStatus() {
        HttpRetrieval spyRetrieval = spy(HttpRetrieval.class);
        spyRetrieval.httpResponse = mock(HttpResponse.class);
        StatusLine mockStatusLine = mock(StatusLine.class);
        when(spyRetrieval.httpResponse.getStatusLine()).thenReturn(mockStatusLine);
        when(mockStatusLine.getStatusCode()).thenReturn(999);
        when(spyRetrieval.checkCompleteContentResponseStatus(999)).thenReturn(true);
        
        boolean res = spyRetrieval.hasCompleteContentResponseStatus();
        
        verify(spyRetrieval).checkCompleteContentResponseStatus(999);
        assertThat(res, is(true));
    }
    
    @Test
    @UseDataProvider("dataProviderCompleteContentResponseStatusCodes")
    public void testCompleteContentResponseStatus_completeCode_returnsTrue(final int statusCode) {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        boolean res = httpRetrieval.checkCompleteContentResponseStatus(statusCode);
        
        assertThat(res, is(true));
    }
    
    @Test
    @UseDataProvider("dataProviderIncompleteContentResponseStatus")
    public void testCompleteContentResponseStatus_incompleteCode_returnsFalse(final int statusCode) {
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        
        boolean res = httpRetrieval.checkCompleteContentResponseStatus(statusCode);
        
        assertThat(res, is(false));
    }
}
