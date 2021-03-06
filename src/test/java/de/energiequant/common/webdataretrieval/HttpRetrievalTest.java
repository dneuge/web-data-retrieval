package de.energiequant.common.webdataretrieval;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequest;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.entity.InputStreamFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.protocol.RedirectLocations;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.hamcrest.junit.ExpectedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import com.google.common.collect.ImmutableList;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;

import de.energiequant.common.webdataretrieval.HttpRetrieval.CompletedHttpResponse;
import uk.org.lidalia.slf4jext.Level;
import uk.org.lidalia.slf4jtest.LoggingEvent;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

@RunWith(DataProviderRunner.class)
// @RunWith(PowerMockRunner.class)
// @PowerMockRunnerDelegate(DataProviderRunner.class)
public class HttpRetrievalTest {

    TestLogger testLogger = TestLoggerFactory.getTestLogger(HttpRetrieval.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() {
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
     * Extracts the default request configuration from a collection of invocations.
     *
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
    public void testCopyConfigurationTo_setTimeout_invokesSetterWithSameArgument() {
        // Arrange
        Duration timeout = Duration.ofMillis(12345);
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        HttpRetrieval mock = mock(HttpRetrieval.class);
        httpRetrieval.setTimeout(timeout);

        // Act
        httpRetrieval.copyConfigurationTo(mock);

        // Assert
        verify(mock).setTimeout(timeout);
    }

    @Test
    public void testCopyConfigurationTo_uninitializedTimeout_usesGetter() {
        // Arrange
        Duration timeout = Duration.ofMillis(321321);
        HttpRetrieval sourceSpy = spy(HttpRetrieval.class);
        when(sourceSpy.getTimeout()).thenReturn(timeout);
        HttpRetrieval targetMock = mock(HttpRetrieval.class);

        // Act
        sourceSpy.copyConfigurationTo(targetMock);

        // Assert
        verify(sourceSpy).getTimeout();
    }

    @Test
    public void testCopyConfigurationTo_setUserAgent_invokesSetterWithSameArgument() {
        // Arrange
        String userAgent = "testCopyConfigurationTo_setUserAgent_invokesSetterWithSameArgument";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(userAgent);
        HttpRetrieval mock = mock(HttpRetrieval.class);

        // Act
        httpRetrieval.copyConfigurationTo(mock);

        // Assert
        verify(mock).setUserAgent(userAgent);
    }

    @Test
    public void testCopyConfigurationTo_uninitializedUserAgent_usesGetter() {
        // Arrange
        String userAgent = "testCopyConfigurationTo_uninitializedUserAgent_usesGetter";
        HttpRetrieval sourceSpy = spy(HttpRetrieval.class);
        when(sourceSpy.getUserAgent()).thenReturn(userAgent);
        HttpRetrieval targetMock = mock(HttpRetrieval.class);

        // Act
        sourceSpy.copyConfigurationTo(targetMock);

        // Assert
        verify(sourceSpy).getUserAgent();
    }

    @Test
    public void testCopyConfigurationTo_setMaximumFollowedRedirects_invokesSetterWithSameArgument() {
        // Arrange
        int numRedirects = 42;
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(numRedirects);
        HttpRetrieval mock = mock(HttpRetrieval.class);

        // Act
        httpRetrieval.copyConfigurationTo(mock);

        // Assert
        verify(mock).setMaximumFollowedRedirects(numRedirects);
    }

    @Test
    public void testCopyConfigurationTo_uninitializedMaximumFollowedRedirects_usesGetter() {
        // Arrange
        int numRedirects = 42;
        HttpRetrieval sourceSpy = spy(HttpRetrieval.class);
        when(sourceSpy.getMaximumFollowedRedirects()).thenReturn(numRedirects);
        HttpRetrieval targetMock = mock(HttpRetrieval.class);

        // Act
        sourceSpy.copyConfigurationTo(targetMock);

        // Assert
        verify(sourceSpy).getMaximumFollowedRedirects();
    }

    @Test
    public void testSetTimeout_anyValue_appliesToInternalAttribute() {
        // Arrange
        Duration timeout = Duration.ofMillis(54321);
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setTimeout(timeout);

        // AssertappliesToInternalAttribute
        assertThat(httpRetrieval.timeout, is(timeout));
    }

    @Test
    public void testSetTimeout_always_returnsSameInstance() {
        // Arrange
        Duration timeout = Duration.ofMillis(54321);
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        HttpRetrieval res = httpRetrieval.setTimeout(timeout);

        // Assert
        assertThat(res, is(sameInstance(httpRetrieval)));
    }

    @Test
    public void testGetTimeout_anyValue_returnsSetTimeout() {
        // Arrange
        Duration expectedTimeout = Duration.ofMillis(654654);
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setTimeout(expectedTimeout);

        // Act
        Duration actualTimeout = httpRetrieval.getTimeout();

        // Assert
        assertThat(actualTimeout, is(expectedTimeout));
    }

    @Test
    public void testGetTimeout_initially_returns30s() {
        // Arrange
        Duration expectedTimeout = Duration.ofSeconds(30);
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        Duration actualTimeout = httpRetrieval.getTimeout();

        // Assert
        assertThat(actualTimeout, is(expectedTimeout));
    }

    @Test
    public void testSetUserAgent_goodString_appliesToInternalAttribute() {
        // Arrange
        String expectedUserAgent = "testSetUserAgent_notNull_appliesToInternalAttribute";
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setUserAgent(expectedUserAgent);

        // Assert
        assertThat(httpRetrieval.userAgent, is(expectedUserAgent));
    }

    @Test
    public void testSetUserAgent_null_doesNotApply() {
        // Arrange
        String originalUserAgent = "not null";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(originalUserAgent);

        // Act
        httpRetrieval.setUserAgent(null);

        // Assert
        assertThat(httpRetrieval.userAgent, is(originalUserAgent));
    }

    @Test
    public void testSetUserAgent_null_logsWarning() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setUserAgent(null);

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn(
            "User-agent string is required and cannot be set to null! Using previous/default value." //
        );
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testSetUserAgent_whitespaceString_doesNotApply() {
        // Arrange
        String originalUserAgent = "not white-space";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(originalUserAgent);

        // Act
        httpRetrieval.setUserAgent("      ");

        // Assert
        assertThat(httpRetrieval.userAgent, is(originalUserAgent));
    }

    @Test
    public void testSetUserAgent_whitespaceString_logsWarning() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setUserAgent("      ");

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn(
            "User-agent string is required and cannot be set to empty/white-space string! Using previous/default value." //
        );
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testSetUserAgent_goodString_returnsSameInstance() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        HttpRetrieval res = httpRetrieval.setUserAgent("123");

        // Assert
        assertThat(res, is(sameInstance(httpRetrieval)));
    }

    @Test
    public void testSetUserAgent_null_returnsSameInstance() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        HttpRetrieval res = httpRetrieval.setUserAgent(null);

        // Assert
        assertThat(res, is(sameInstance(httpRetrieval)));
    }

    @Test
    public void testSetUserAgent_whitespaceString_returnsSameInstance() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        HttpRetrieval res = httpRetrieval.setUserAgent("  ");

        // Assert
        assertThat(res, is(sameInstance(httpRetrieval)));
    }

    @Test
    public void testGetUserAgent_anyValue_returnsSetUserAgent() {
        // Arrange
        String expectedUserAgent = "testGetUserAgent_anyValue_returnsSetUserAgent";
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setUserAgent(expectedUserAgent);

        // Act
        String actualUserAgent = httpRetrieval.getUserAgent();

        // Assert
        assertThat(actualUserAgent, is(expectedUserAgent));
    }

    @Test
    public void testGetUserAgent_initially_returnsDefaultUserAgent() {
        // Arrange
        String expectedUserAgent = "HttpRetrieval";
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        String actualUserAgent = httpRetrieval.getUserAgent();

        // Assert
        assertThat(actualUserAgent, is(expectedUserAgent));
    }

    @Test
    public void testSetMaximumFollowedRedirects_positiveNumber_appliesToInternalAttribute() {
        // Arrange
        int expectedNumRedirects = 123;
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setMaximumFollowedRedirects(expectedNumRedirects);

        // Assert
        assertThat(httpRetrieval.maximumFollowedRedirects, is(expectedNumRedirects));
    }

    @Test
    public void testSetMaximumFollowedRedirects_zero_appliesToInternalAttribute() {
        // Arrange
        int expectedNumRedirects = 0;
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(10);

        // Act
        httpRetrieval.setMaximumFollowedRedirects(expectedNumRedirects);

        // Assert
        assertThat(httpRetrieval.maximumFollowedRedirects, is(expectedNumRedirects));
    }

    @Test
    public void testSetMaximumFollowedRedirects_negativeNumber_appliesAsZero() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(100);

        // Act
        httpRetrieval.setMaximumFollowedRedirects(-1);

        // Assert
        assertThat(httpRetrieval.maximumFollowedRedirects, is(0));
    }

    @Test
    public void testSetMaximumFollowedRedirects_negativeNumber_logsWarning() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setMaximumFollowedRedirects(-1);

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn(
            "Attempted to set a negative number of maximum allowed redirects ({}), limiting to 0.",
            -1 //
        );
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testSetMaximumFollowedRedirects_exactly10_doesNotLogWarning() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setMaximumFollowedRedirects(10);

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        assertThat(loggingEvents, is(empty()));
    }

    @Test
    public void testSetMaximumFollowedRedirects_moreThan10_logsWarning() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.setMaximumFollowedRedirects(11);

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn(
            "Allowing a high number of redirects to be followed ({}), this may not make sense and should be reduced for practical reasons.",
            11 //
        );
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testSetMaximumFollowedRedirects_always_returnsSameInstance() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        HttpRetrieval res = httpRetrieval.setMaximumFollowedRedirects(1);

        // Assert
        assertThat(res, is(sameInstance(httpRetrieval)));
    }

    @Test
    public void testGetMaximumFollowedRedirects_anyValue_returnsSetMaximumFollowedRedirects() {
        // Arrange
        int expected = 512;
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.setMaximumFollowedRedirects(expected);

        // Act
        int actual = httpRetrieval.getMaximumFollowedRedirects();

        // Assert
        assertThat(actual, is(expected));
    }

    @Test
    public void testGetMaximumFollowedRedirects_initially_returns5() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        int actual = httpRetrieval.getMaximumFollowedRedirects();

        // Assert
        assertThat(actual, is(5));
    }

    @Test
    public void testBuildHttpClient_initially_returnsNotNull() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        CloseableHttpClient res = httpRetrieval.buildHttpClient();

        // Assert
        assertThat(res, is(not(nullValue())));
    }

    @Test
    public void testBuildHttpClient_setTimeout_appliedToDefaultRequestConfigConnectTimeout() {
        // Arrange
        long expectedTimeout = 121314;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setTimeout(Duration.ofMillis(expectedTimeout));

        // Act
        spy.buildHttpClient();

        // Assert
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        long actualTimeoutDuration = requestConfig.getConnectTimeout().getDuration();
        TimeUnit actualTimeoutUnit = requestConfig.getConnectTimeout().getTimeUnit();
        assertThat(actualTimeoutUnit, is(TimeUnit.MILLISECONDS));
        assertThat(actualTimeoutDuration, is(expectedTimeout));
    }

    @Test
    public void testBuildHttpClient_setTimeout_appliedToDefaultRequestConfigConnectionRequestTimeout() {
        // Arrange
        long expectedTimeout = 321321;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setTimeout(Duration.ofMillis(expectedTimeout));

        // Act
        spy.buildHttpClient();

        // Assert
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        long actualTimeoutDuration = requestConfig.getConnectionRequestTimeout().getDuration();
        TimeUnit actualTimeoutUnit = requestConfig.getConnectionRequestTimeout().getTimeUnit();
        assertThat(actualTimeoutUnit, is(TimeUnit.MILLISECONDS));
        assertThat(actualTimeoutDuration, is(expectedTimeout));
    }

    @Test
    public void testBuildHttpClient_setMaximumFollowedRedirects_appliedToDefaultRequestConfig() {
        // Arrange
        int expectedMaximum = 42;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setMaximumFollowedRedirects(expectedMaximum);

        // Act
        spy.buildHttpClient();

        // Assert
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        int actualMaximum = requestConfig.getMaxRedirects();
        assertThat(actualMaximum, is(expectedMaximum));
    }

    @Test
    public void testBuildHttpClient_anyConfig_allowsCompression() {
        // Arrange
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);

        // Act
        spy.buildHttpClient();

        // Assert
        RequestConfig requestConfig = getRequestConfigFromInvocations(mockingDetails(spyBuilder).getInvocations());
        boolean isEnabled = requestConfig.isContentCompressionEnabled();
        assertThat(isEnabled, is(true));
    }

    @Test
    public void testBuildHttpClient_setUserAgent_appliedToBuilder() {
        // Arrange
        String expectedUserAgent = "Expected UA/123.45.6.7";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpClientBuilder spyBuilder = spy(HttpClientBuilder.class);
        when(spy.getHttpClientBuilder()).thenReturn(spyBuilder);
        spy.setUserAgent(expectedUserAgent);

        // Act
        spy.buildHttpClient();

        // Assert
        verify(spyBuilder).setUserAgent(expectedUserAgent);
    }

    /*
     * FIXME: map instance can no longer be shared, test does not work the old way
     * any more
     */
    /*
     * @Test public void testBuildHttpClient_anyConfig_setsContentDecoderRegistry()
     * { // Arrange HttpRetrieval spy = spy(HttpRetrieval.class); HttpClientBuilder
     * spyBuilder = spy(HttpClientBuilder.class);
     * when(spy.getHttpClientBuilder()).thenReturn(spyBuilder); Map<String,
     * InputStreamFactory> expectedMap = spy.getContentDecoderMap();
     * 
     * // Act spy.buildHttpClient();
     * 
     * // Assert // NOTE: this test relies on getContentDecoderMap returning the
     * same // instance for every call
     * verify(spyBuilder).setContentDecoderRegistry(expectedMap); }
     */

    @Test
    public void testGetContentDecoderMap_calledTwice_returnsSameInstance() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        Map<String, InputStreamFactory> firstMap = httpRetrieval.getContentDecoderMap();

        // Act
        Map<String, InputStreamFactory> secondMap = httpRetrieval.getContentDecoderMap();

        // Assert
        assertThat(secondMap, is(sameInstance(firstMap)));
    }

    @Test
    public void testGetContentDecoderMap_initially_isUnmodifiable() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        Map<String, InputStreamFactory> map = httpRetrieval.getContentDecoderMap();

        // Assert
        thrown.expect(UnsupportedOperationException.class);
        map.put("test", null);
    }

    @Test
    public void testGetContentDecoderMap_initially_containsGzipInputStreamFactory() throws IOException {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        Map<String, InputStreamFactory> map = httpRetrieval.getContentDecoderMap();

        // Assert
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(new byte[0]);
        InputStreamFactory factory = map.get("gzip");
        thrown.expect(EOFException.class);
        InputStream factoredInputStream = factory.create(byteArrayInputStream);
        assertThat(factoredInputStream, is(instanceOf(GZIPInputStream.class)));
    }

    @Test
    public void testRequestByGet_supportedProtocol_invokesBuildHttpClient() throws IOException {
        // Arrange
        HttpRetrieval spy = spy(HttpRetrieval.class);
        when(spy.buildHttpClient()).thenReturn(mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS));

        doNothing().when(spy).onHttpResponseCompleted(Mockito.any());

        // Act
        spy.requestByGet("http://a.local/");

        // Assert
        // mocking a method is recorded as an invocation, thus we expect to see
        // n+1 invocations when mocking a method, only one should usually occur
        verify(spy, times(2)).buildHttpClient();
    }

    @Test
    public void testRequestByGet_supportedProtocol_invokesBuildHttpGetPassingUrl() throws Exception {
        // Arrange
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mock(HttpGet.class));

        doNothing().when(spy).onHttpResponseCompleted(Mockito.any());

        // Act
        spy.requestByGet(url);

        // Assert
        verify(spy).buildHttpGet(url);
    }

    @Test
    public void testRequestByGet_supportedProtocol_executesHttpGet() throws IOException {
        // Arrange
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);

        doNothing().when(spy).onHttpResponseCompleted(Mockito.any());

        // Act
        spy.requestByGet(url);

        // Assert
        verify(mockClient).execute(same(mockGet), Mockito.any(HttpClientContext.class));
    }

    @Test
    public void testRequestByGet_supportedProtocol_logsDebug() throws IOException {
        // Arrange
        String url = "http://b.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenReturn(mock(CloseableHttpResponse.class));

        doNothing().when(spy).onHttpResponseCompleted(Mockito.any());

        // Act
        spy.requestByGet(url);

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.debug("requesting \"{}\" by GET method", url);
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testRequestByGet_networkLevelSuccess_returnsTrue() throws IOException {
        // Arrange
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenReturn(mock(CloseableHttpResponse.class));

        doNothing().when(spy).onHttpResponseCompleted(Mockito.any());

        // Act
        boolean res = spy.requestByGet(url);

        // Assert
        assertThat(res, is(true));
    }

    @Test
    public void testRequestByGet_networkLevelSuccess_storesResponseInternally() throws IOException {
        // Arrange
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        when(mockClient.execute(same(mockGet), Mockito.any(HttpClientContext.class))).thenReturn(mockResponse);

        doNothing().when(spy).onHttpResponseCompleted(Mockito.any());

        // Act
        spy.requestByGet(url);

        // Assert
        verify(spy).onHttpResponseCompleted(Mockito.same(mockResponse));
    }

    @Test
    public void testRequestByGet_unsupportedProtocol_clearsPreviousResponse() throws IOException {
        // Arrange
        String url = "";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        spy.httpResponse = mock(CompletedHttpResponse.class);

        // Act
        spy.requestByGet(url);

        // Assert
        assertThat(spy.httpResponse, is(nullValue()));
    }

    @Test
    public void testRequestByGet_nullUrl_clearsPreviousResponse() throws IOException {
        // Arrange
        String url = null;
        HttpRetrieval spy = spy(HttpRetrieval.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        spy.httpResponse = mock(CompletedHttpResponse.class);

        // Act
        spy.requestByGet(url);

        // Assert
        assertThat(spy.httpResponse, is(nullValue()));
    }

    @Test
    public void testRequestByGet_retrievalFailsWithException_clearsPreviousResponse() throws IOException {
        // Arrange
        String url = "";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(mockGet)).thenThrow(IOException.class);
        spy.httpResponse = mock(CompletedHttpResponse.class);

        // Act
        spy.requestByGet(url);

        // Assert
        assertThat(spy.httpResponse, is(nullValue()));
    }

    @Test
    public void testRequestByGet_retrievalFailsWithException_returnsFalse() throws IOException {
        // Arrange
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(same(mockGet), Mockito.any(HttpClientContext.class))).thenThrow(IOException.class);

        // Act
        boolean res = spy.requestByGet(url);

        // Assert
        assertThat(res, is(false));
    }

    @Test
    public void testRequestByGet_retrievalFailsWithException_logsWarning() throws IOException {
        // Arrange
        Class<? extends Throwable> expectedThrowable = IOException.class;
        String url = "http://a.local/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        HttpGet mockGet = mock(HttpGet.class);
        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        when(spy.buildHttpClient()).thenReturn(mockClient);
        when(spy.buildHttpGet(url)).thenReturn(mockGet);
        when(mockClient.execute(same(mockGet), Mockito.any(HttpClientContext.class))).thenThrow(expectedThrowable);

        // Act
        spy.requestByGet(url);

        // Assert
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
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        boolean res = httpRetrieval.requestByGet(null);

        // Assert
        assertThat(res, is(false));
    }

    @Test
    public void testRequestByGet_nullUrl_logsWarning() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        httpRetrieval.requestByGet(null);

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("Attempted to perform a GET request with null as URL.");
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testRequestByGet_unsupportedProtocol_returnsFalse() {
        // Arrange
        String url = "whats-this://clearly.not.supported/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        when(spy.checkSupportedUrlProtocol(url)).thenReturn(false);

        // Act
        boolean res = spy.requestByGet(url);

        // Assert
        assertThat(res, is(false));
    }

    @Test
    public void testRequestByGet_unsupportedProtocol_logsWarning() {
        // Arrange
        String url = "whats-this://clearly.not.supported/";
        HttpRetrieval spy = spy(HttpRetrieval.class);
        when(spy.checkSupportedUrlProtocol(url)).thenReturn(false);

        // Act
        spy.requestByGet(url);

        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("Unsupported protocol used in URL for GET request: \"{}\"", url);
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    @UseDataProvider("dataProviderUrlsWithSupportedProtocols")
    public void checkSupportedUrlProtocol_supportedProtocols_returnsTrue(final String url) {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        boolean res = httpRetrieval.checkSupportedUrlProtocol(url);

        // Assert
        assertThat(res, is(true));
    }

    @Test
    @UseDataProvider("dataProviderUrlsWithoutSupportedProtocols")
    public void checkSupportedUrlProtocol_noSupportedProtocol_returnsFalse(final String url) {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        boolean res = httpRetrieval.checkSupportedUrlProtocol(url);

        // Assert
        assertThat(res, is(false));
    }

    @Test
    public void testGetResponseBodyBytes_nullResponse_returnsNull() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.httpResponse = null;

        // Act
        byte[] bytes = httpRetrieval.getResponseBodyBytes();

        // Assert
        assertThat(bytes, is(nullValue()));
    }

    // FIXME: response body bytes are now read during retrieval
    /*
     * @Test public void testGetResponseBodyBytes_ioExceptionThrown_returnsNull()
     * throws IOException { // Arrange Exception expectedException = new
     * IOException("expected exception"); HttpRetrieval httpRetrieval = new
     * HttpRetrieval(); httpRetrieval.httpResponse =
     * mock(CloseableHttpResponse.class); HttpEntity mockEntity =
     * mock(HttpEntity.class);
     * when(mockEntity.getContent()).thenThrow(expectedException);
     * when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
     * 
     * // Act byte[] bytes = httpRetrieval.getResponseBodyBytes();
     * 
     * // Assert assertThat(bytes, is(nullValue())); }
     * 
     * @Test public void
     * testGetResponseBodyBytes_unsupportedOperationExceptionThrown_returnsNull()
     * throws IOException { // Arrange Exception expectedException = new
     * UnsupportedOperationException("expected exception"); HttpRetrieval
     * httpRetrieval = new HttpRetrieval(); httpRetrieval.httpResponse =
     * mock(CloseableHttpResponse.class); HttpEntity mockEntity =
     * mock(HttpEntity.class);
     * when(mockEntity.getContent()).thenThrow(expectedException);
     * when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
     * 
     * // Act byte[] bytes = httpRetrieval.getResponseBodyBytes();
     * 
     * // Assert assertThat(bytes, is(nullValue())); }
     * 
     * @Test public void testGetResponseBodyBytes_ioExceptionThrown_logsWarning()
     * throws IOException { // Arrange Exception expectedException = new
     * IOException("expected exception"); HttpRetrieval httpRetrieval = new
     * HttpRetrieval(); httpRetrieval.httpResponse =
     * mock(CloseableHttpResponse.class); HttpEntity mockEntity =
     * mock(HttpEntity.class);
     * when(mockEntity.getContent()).thenThrow(expectedException);
     * when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
     * 
     * // Act httpRetrieval.getResponseBodyBytes();
     * 
     * // Assert // it seems we can not easily compare events which wrap a Throwable
     * :( List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
     * assertThat(loggingEvents.size(), is(1)); LoggingEvent actualEvent =
     * loggingEvents.iterator().next(); assertThat(actualEvent.getMessage(),
     * is("Failed to copy bytes from HTTP response.")); Throwable actualThrowable =
     * actualEvent.getThrowable().get(); assertThat(actualThrowable,
     * is(expectedException)); }
     * 
     * @Test public void
     * testGetResponseBodyBytes_unsupportedOperationExceptionThrown_logsWarning()
     * throws IOException { // Arrange Exception expectedException = new
     * UnsupportedOperationException("expected exception"); HttpRetrieval
     * httpRetrieval = new HttpRetrieval(); httpRetrieval.httpResponse =
     * mock(CloseableHttpResponse.class); HttpEntity mockEntity =
     * mock(HttpEntity.class);
     * when(mockEntity.getContent()).thenThrow(expectedException);
     * when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
     * 
     * // Act httpRetrieval.getResponseBodyBytes();
     * 
     * // Assert // it seems we can not easily compare events which wrap a Throwable
     * :( List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
     * assertThat(loggingEvents.size(), is(1)); LoggingEvent actualEvent =
     * loggingEvents.iterator().next(); assertThat(actualEvent.getMessage(),
     * is("Failed to copy bytes from HTTP response.")); Throwable actualThrowable =
     * actualEvent.getThrowable().get(); assertThat(actualThrowable,
     * is(expectedException)); }
     * 
     * @Test public void testGetResponseBodyBytes_withResponse_returnsBodyBytes()
     * throws IOException { // Arrange byte[] expectedBytes = new byte[] { 'A', 'B',
     * 'C', '\n', '1', '2', '3', 0, 1, 2, 3 }; HttpRetrieval httpRetrieval = new
     * HttpRetrieval(); httpRetrieval.httpResponse =
     * mock(CloseableHttpResponse.class); HttpEntity mockEntity =
     * mock(HttpEntity.class); when(mockEntity.getContent()).thenReturn(new
     * ByteArrayInputStream(expectedBytes));
     * when(httpRetrieval.httpResponse.getEntity()).thenReturn(mockEntity);
     * 
     * // Act byte[] actualBytes = httpRetrieval.getResponseBodyBytes();
     * 
     * // Assert assertThat(actualBytes, is(equalTo(expectedBytes))); }
     */

    // FIXME: status code is now read during retrieval
    /*
     * @Test public void
     * testHasCompleteContentResponseStatus_nullResponse_returnsFalse() { // Arrange
     * HttpRetrieval httpRetrieval = new HttpRetrieval(); httpRetrieval.httpResponse
     * = null;
     * 
     * // Act boolean res = httpRetrieval.hasCompleteContentResponseStatus();
     * 
     * // Assert assertThat(res, is(false)); }
     * 
     * @Test public void
     * testHasCompleteContentResponseStatus_withResponse_queriesCheckCompleteContentResponseStatus
     * () { // Arrange HttpRetrieval spyRetrieval = spy(HttpRetrieval.class);
     * spyRetrieval.httpResponse = mock(CompletedHttpResponse.class);
     * when(spyRetrieval.httpResponse.getCode()).thenReturn(999);
     * when(spyRetrieval.checkCompleteContentResponseStatus(999)).thenReturn(true);
     * 
     * // Act boolean res = spyRetrieval.hasCompleteContentResponseStatus();
     * 
     * // Assert verify(spyRetrieval).checkCompleteContentResponseStatus(999);
     * assertThat(res, is(true)); }
     */

    @Test
    @UseDataProvider("dataProviderCompleteContentResponseStatusCodes")
    public void testCompleteContentResponseStatus_completeCode_returnsTrue(final int statusCode) {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        boolean res = httpRetrieval.checkCompleteContentResponseStatus(statusCode);

        // Assert
        assertThat(res, is(true));
    }

    @Test
    @UseDataProvider("dataProviderIncompleteContentResponseStatus")
    public void testCompleteContentResponseStatus_incompleteCode_returnsFalse(final int statusCode) {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        boolean res = httpRetrieval.checkCompleteContentResponseStatus(statusCode);

        // Assert
        assertThat(res, is(false));
    }

    // FIXME: response headers are now read during retrieval
    /*
     * @Test public void testGetResponseHeaders_nullResponse_returnsNull() { //
     * Arrange HttpRetrieval httpRetrieval = new HttpRetrieval();
     * httpRetrieval.httpResponse = null;
     * 
     * // Act CaseInsensitiveHeaders result = httpRetrieval.getResponseHeaders();
     * 
     * // Assert assertThat(result, is(nullValue())); }
     * 
     * @Test public void
     * testGetResponseHeaders_withResponse_returnsWithOnlyAllResponseHeadersAdded()
     * { // Arrange HttpRetrieval spyRetrieval = spy(new HttpRetrieval());
     * spyRetrieval.httpResponse = mock(CompletedHttpResponse.class);
     * 
     * Header[] headersArr = new Header[0];
     * when(spyRetrieval.httpResponse.getHeaders()).thenReturn(headersArr);
     * 
     * doReturn(mock(CaseInsensitiveHeaders.class)).when(spyRetrieval).
     * createCaseInsensitiveHeaders();
     * 
     * // Act CaseInsensitiveHeaders mockHeaders =
     * spyRetrieval.getResponseHeaders();
     * 
     * // Assert verify(mockHeaders).addAll(Mockito.same(headersArr));
     * verifyNoMoreInteractions(mockHeaders); }
     */

    @Test
    public void testGetLastRequestedLocation_beforeRequest_returnsNull() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        String result = httpRetrieval.getLastRequestedLocation();

        // Assert
        assertThat(result, is(nullValue()));
    }

    @Test
    @DataProvider({ "http://abc.local/test.html", "https://somewhere.else.local/?id=123", ":thisisgarbage:!!" })
    public void testGetLastRequestedLocation_afterRequest_returnsSameUrl(String url) {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.requestByGet(url);

        // Act
        String result = httpRetrieval.getLastRequestedLocation();

        // Assert
        assertThat(result, is(equalTo(url)));
    }

    @Test
    public void testGetLastRequestedLocation_afterSecondRequest_returnsSecondUrl() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.requestByGet("http://not.what.i.want/");
        httpRetrieval.requestByGet("https://thats.what.i.want/");

        // Act
        String result = httpRetrieval.getLastRequestedLocation();

        // Assert
        assertThat(result, is(equalTo("https://thats.what.i.want/")));
    }

    @Test
    public void testGetLastRequestedLocation_afterSecondRequestWithNullUrl_returnsNull() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        httpRetrieval.requestByGet("http://not.what.i.want/");
        httpRetrieval.requestByGet(null);

        // Act
        String result = httpRetrieval.getLastRequestedLocation();

        // Assert
        assertThat(result, is(nullValue()));
    }

    @Test
    public void testGetLastRetrievedLocation_beforeRequest_returnsNull() {
        // Arrange
        HttpRetrieval httpRetrieval = new HttpRetrieval();

        // Act
        String result = httpRetrieval.getLastRetrievedLocation();

        // Assert
        assertThat(result, is(nullValue()));
    }

    @Test
    @DataProvider({ "https://this-is-the-actual.location:123/aaa.aspx?id=54321&something",
        "http://much-easier.local/test.html" })
    public void testGetLastRetrievedLocation_afterRequestManyRedirects_returnsLastRedirectLocationFromContext(String expectedUrl) throws Exception {
        // Arrange
        HttpClientContext mockContext = mock(HttpClientContext.class);
        RedirectLocations mockRedirectLocations = mock(RedirectLocations.class);
        when(mockRedirectLocations.getAll())
            .thenReturn(Arrays.asList(
                new URI("http://first-redirect/abc.html"),
                new URI("http://another.com/"),
                new URI(expectedUrl) //
            ));
        when(mockContext.getRedirectLocations())
            .thenReturn(mockRedirectLocations);

        HttpRetrieval spyRetrieval = spy(new HttpRetrieval());
        doReturn(mockContext, (HttpClientContext) null).when(spyRetrieval).createHttpClientContext();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        doReturn(mockClient).when(spyRetrieval).buildHttpClient();

        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        doReturn(mockResponse).when(mockClient).execute(Mockito.any(HttpUriRequest.class),
            Mockito.any(HttpClientContext.class));

        doAnswer(invocation -> {
            spyRetrieval.httpResponse = mock(CompletedHttpResponse.class);
            return null;
        }).when(spyRetrieval).onHttpResponseCompleted(Mockito.any());

        spyRetrieval.requestByGet("http://this-was-originally-requested/");

        // Act
        String result = spyRetrieval.getLastRetrievedLocation();

        // Assert
        assertThat(result, is(equalTo(expectedUrl)));
    }

    @Test
    @DataProvider({ "https://this-is-the-actual.location:123/aaa.aspx?id=54321&something",
        "http://much-easier.local/test.html" })
    public void testGetLastRetrievedLocation_afterRequestNoRedirects_returnsRequestedUrl(String url) throws Exception {
        // Arrange
        HttpClientContext mockContext = mock(HttpClientContext.class);
        RedirectLocations mockRedirectLocations = mock(RedirectLocations.class);
        when(mockRedirectLocations.getAll())
            .thenReturn(Arrays.asList());
        when(mockContext.getRedirectLocations()).thenReturn(mockRedirectLocations);

        HttpRetrieval spyRetrieval = spy(new HttpRetrieval());
        doReturn(mockContext, (HttpClientContext) null).when(spyRetrieval).createHttpClientContext();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        doReturn(mockClient).when(spyRetrieval).buildHttpClient();

        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        doReturn(mockResponse).when(mockClient).execute(
            Mockito.any(HttpUriRequest.class),
            Mockito.any(HttpClientContext.class) //
        );

        doAnswer(invocation -> {
            spyRetrieval.httpResponse = mock(CompletedHttpResponse.class);
            return null;
        }).when(spyRetrieval).onHttpResponseCompleted(Mockito.any());

        spyRetrieval.requestByGet(url);

        // Act
        String result = spyRetrieval.getLastRetrievedLocation();

        // Assert
        assertThat(result, is(equalTo(url)));
    }

    @Test
    @DataProvider({ "https://this-is-the-actual.location:123/aaa.aspx?id=54321&something",
        "http://much-easier.local/test.html" })
    public void testGetLastRetrievedLocation_afterRequestNullAsRedirectLocations_returnsRequestedUrl(String url) throws Exception {
        // Arrange
        HttpClientContext mockContext = mock(HttpClientContext.class);
        when(mockContext.getRedirectLocations()).thenReturn(null);

        HttpRetrieval spyRetrieval = spy(new HttpRetrieval());
        doReturn(mockContext, (HttpClientContext) null).when(spyRetrieval).createHttpClientContext();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        doReturn(mockClient).when(spyRetrieval).buildHttpClient();

        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        doReturn(mockResponse).when(mockClient).execute(
            Mockito.any(ClassicHttpRequest.class),
            Mockito.any(HttpClientContext.class) //
        );

        doAnswer(invocation -> {
            spyRetrieval.httpResponse = mock(CompletedHttpResponse.class);
            return null;
        }).when(spyRetrieval).onHttpResponseCompleted(Mockito.any());

        spyRetrieval.requestByGet(url);

        // Act
        String result = spyRetrieval.getLastRetrievedLocation();

        // Assert
        assertThat(result, is(equalTo(url)));
    }

    @Test
    @DataProvider({ "https://this-is-the-actual.location:123/aaa.aspx?id=54321&something",
        "http://much-easier.local/test.html" })
    public void testGetLastRetrievedLocation_afterSecondRequestWithNewContext_returnsLocationFromNewContext(String expectedUrl) throws Exception {
        // Arrange
        HttpClientContext mockFirstContext = mock(HttpClientContext.class);
        HttpClientContext mockSecondContext = mock(HttpClientContext.class);
        RedirectLocations mockRedirectLocations = mock(RedirectLocations.class);
        when(mockRedirectLocations.getAll())
            .thenReturn(Arrays.asList(
                new URI(expectedUrl) //
            ));
        when(mockSecondContext.getRedirectLocations()).thenReturn(mockRedirectLocations);

        HttpRetrieval spyRetrieval = spy(new HttpRetrieval());
        doReturn(mockFirstContext, mockSecondContext).when(spyRetrieval).createHttpClientContext();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class);
        doReturn(mockClient).when(spyRetrieval).buildHttpClient();

        CloseableHttpResponse mockResponse = mock(CloseableHttpResponse.class);
        doReturn(mockResponse).when(mockClient).execute(
            Mockito.any(HttpUriRequest.class),
            Mockito.any(HttpClientContext.class) //
        );

        doAnswer(invocation -> {
            spyRetrieval.httpResponse = mock(CompletedHttpResponse.class);
            return null;
        }).when(spyRetrieval).onHttpResponseCompleted(Mockito.any());

        spyRetrieval.requestByGet("https://some-url.local/");
        spyRetrieval.requestByGet("https://some-url.local/");

        // Act
        String result = spyRetrieval.getLastRetrievedLocation();

        // Assert
        assertThat(result, is(equalTo(expectedUrl)));
    }

    @Test
    public void testGetLastRetrievedLocation_afterSecondRequestFailed_returnsNull() throws Exception {
        // Arrange
        HttpClientContext mockFirstContext = mock(HttpClientContext.class);
        RedirectLocations mockRedirectLocations = mock(RedirectLocations.class);
        when(mockRedirectLocations.getAll())
            .thenReturn(Arrays.asList(
                new URI("http://unexpected.url.local/") //
            ));
        when(mockFirstContext.getRedirectLocations()).thenReturn(mockRedirectLocations);

        HttpClientContext mockSecondContext = mock(HttpClientContext.class);

        HttpRetrieval spyRetrieval = spy(new HttpRetrieval());
        doReturn(mockFirstContext, mockSecondContext).when(spyRetrieval).createHttpClientContext();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS);
        doReturn(mockClient).when(spyRetrieval).buildHttpClient();

        doNothing().when(spyRetrieval).onHttpResponseCompleted(Mockito.any());

        spyRetrieval.requestByGet("https://some-url.local/");

        doThrow(new IOException()).when(mockClient).execute(
            Mockito.any(HttpUriRequest.class),
            Mockito.any(HttpClientContext.class) //
        );

        spyRetrieval.requestByGet("https://some-url.local/");

        // Act
        String result = spyRetrieval.getLastRetrievedLocation();

        // Assert
        assertThat(result, is(nullValue()));
    }

    @Test
    public void testRequestByGet_secondRequest_executesWithSecondContext() throws Exception {
        // Arrange
        HttpClientContext mockFirstContext = mock(HttpClientContext.class);
        HttpClientContext mockSecondContext = mock(HttpClientContext.class);

        HttpRetrieval spyRetrieval = spy(new HttpRetrieval());
        doReturn(mockFirstContext, mockSecondContext, (HttpClientContext) null)
            .when(spyRetrieval)
            .createHttpClientContext();

        CloseableHttpClient mockClient = mock(CloseableHttpClient.class, RETURNS_DEEP_STUBS);
        doReturn(mockClient).when(spyRetrieval).buildHttpClient();

        doNothing().when(spyRetrieval).onHttpResponseCompleted(Mockito.any());

        spyRetrieval.requestByGet("https://some-url.local/");

        reset(mockClient);

        // Act
        spyRetrieval.requestByGet("https://some-url.local/");

        // Assert
        verify(mockClient).execute(Mockito.any(HttpUriRequest.class), Mockito.same(mockSecondContext));
    }
}
