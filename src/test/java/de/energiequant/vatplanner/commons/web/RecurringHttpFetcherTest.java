package de.energiequant.vatplanner.commons.web;

import de.energiequant.vatplanner.commons.web.HttpRetrieval;
import de.energiequant.vatplanner.commons.web.entities.RetrievedInformation;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.hamcrest.Matchers.*;
import org.hamcrest.junit.ExpectedException;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import static org.mockito.Mockito.*;
import uk.org.lidalia.slf4jtest.LoggingEvent;
import uk.org.lidalia.slf4jtest.TestLogger;
import uk.org.lidalia.slf4jtest.TestLoggerFactory;

public class RecurringHttpFetcherTest {
    TestLogger testLogger = TestLoggerFactory.getTestLogger(RecurringHttpFetcher.class);
    
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    
    @Before
    public void clearLog() {
        testLogger.clearAll();
    }
    
    @Test
    public void testCreateHttpRetrieval_noTemplateSet_returnsNull() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        HttpRetrieval res = fetcher.createHttpRetrieval();
        
        // Assert
        assertThat(res, is(nullValue()));
    }
    
    @Test
    public void testCreateHttpRetrieval_noTemplateSet_logsWarning() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        fetcher.createHttpRetrieval();
        
        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("requested HttpRetrieval instance without any template");
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    public void testCreateHttpRetrieval_templateSet_returnsNewInstance() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval original = new HttpRetrieval();
        fetcher.setTemplateHttpRetrieval(original);
        
        // Act
        HttpRetrieval copy = fetcher.createHttpRetrieval();
        
        // Assert
        assertThat(copy, is(not(sameInstance(original))));
    }
    
    @Test
    public void testCreateHttpRetrieval_templateSet_invokesCopyCreation() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval mockHttpRetrieval = mock(HttpRetrieval.class);
        when(mockHttpRetrieval.copyConfiguration()).thenReturn(mockHttpRetrieval);
        fetcher.setTemplateHttpRetrieval(mockHttpRetrieval);
        clearInvocations(mockHttpRetrieval);
        
        // Act
        fetcher.createHttpRetrieval();
        
        // Assert
        verify(mockHttpRetrieval).copyConfiguration();
    }
    
    @Test
    public void testCreateHttpRetrieval_templateSet_changesToOriginalTemplateDoNotApplyImmediately() {
        // This test only checks that createHttpRetrieval creates a copy.
        
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval original = new HttpRetrieval();
        original.setMaximumFollowedRedirects(1);
        fetcher.setTemplateHttpRetrieval(original);
        
        // Act
        HttpRetrieval copy = fetcher.createHttpRetrieval();
        original.setMaximumFollowedRedirects(2);
        
        // Assert
        int copyRedirects = copy.getMaximumFollowedRedirects();
        assertThat(copyRedirects, is(1));
    }
    
    @Test
    public void testCreateHttpRetrieval_templateSet_changesToOriginalTemplateDoNotApplyToNewInstances() {
        // This test checks that setTemplateHttpRetrieval also created a copy used as template.
        
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval original = new HttpRetrieval();
        original.setMaximumFollowedRedirects(1);
        fetcher.setTemplateHttpRetrieval(original);
        original.setMaximumFollowedRedirects(2);
        
        // Act
        HttpRetrieval copy = fetcher.createHttpRetrieval();
        
        // Assert
        int copyRedirects = copy.getMaximumFollowedRedirects();
        assertThat(copyRedirects, is(1));
    }
    
    @Test
    public void testCreateHttpRetrieval_templateSetTwice_latestTemplateApplies() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval original = new HttpRetrieval();
        original.setMaximumFollowedRedirects(1);
        fetcher.setTemplateHttpRetrieval(original);
        original.setMaximumFollowedRedirects(2);
        fetcher.setTemplateHttpRetrieval(original);
        
        // Act
        HttpRetrieval copy = fetcher.createHttpRetrieval();
        
        // Assert
        int copyRedirects = copy.getMaximumFollowedRedirects();
        assertThat(copyRedirects, is(2));
    }
    
    @Test
    public void testGetLatestRetrievedInformation_initially_returnsNull() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        RetrievedInformation<Object> res = fetcher.getLatestRetrievedInformation();
        
        // Assert
        assertThat(res, is(nullValue()));
    }
    
    @Test
    public void testGetActualRetrievalInterval_initially_returns30Minutes() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        Duration res = fetcher.getActualRetrievalInterval();
        
        // Assert
        assertThat(res, is(Duration.of(30, ChronoUnit.MINUTES)));
    }
    
    @Test
    public void testFetch_initiallyWithoutUrl_logsError() {
        // Arrange
        //HttpClient mockClient = mock(HttpClient.class);
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        //fetcher.setHttpClient(mockClient);
        
        // Act
        fetcher.fetch();
        
        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.error("attempted to fetch without any URL to fetch from");
        assertThat(loggingEvents, contains(expectedEvent));
    }

    @Test
    public void testSetNextRetrievalUrls_nullInput_logsWarning() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        fetcher.setNextRetrievalUrls(null);
        
        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("retrieval URLs were reset due to null input");
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    public void testSetNextRetrievalUrls_emptyInput_logsWarning() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        fetcher.setNextRetrievalUrls(new ArrayList<>());
        
        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("retrieval URLs were reset due to empty input");
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    public void testSetNextRetrievalUrls_emptyInput_copiesCollection() throws MalformedURLException {
        // Arrange
        Collection<URL> inputUrls = new ArrayList<>();
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        fetcher.setNextRetrievalUrls(inputUrls);
        
        // Assert
        URL additionalUrl = new URL("http://c/");
        inputUrls.add(additionalUrl);
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        assertThat(res, not(hasItem(additionalUrl)));
    }

    
    @Test
    public void testSetNextRetrievalUrls_nonEmptyInput_copiesCollection() throws MalformedURLException {
        // Arrange
        Collection<URL> inputUrls = new ArrayList<>(Arrays.asList(new URL[] {
            new URL("http://a/"),
            new URL("http://b/")
        }));
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        fetcher.setNextRetrievalUrls(inputUrls);
        
        // Assert
        URL additionalUrl = new URL("http://c/");
        inputUrls.add(additionalUrl);
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        assertThat(res, not(hasItem(additionalUrl)));
    }
    
    @Test
    public void testGetNextRetrievalUrls_initially_returnsEmptyCollection() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        assertThat(res, is(empty()));
    }
    
    @Test
    public void testGetNextRetrievalUrls_afterNullInput_returnsEmptyCollection() throws MalformedURLException {
        // Arrange
        Collection<URL> initialUrls = Arrays.asList(new URL[] {
            new URL("http://a/")
        });
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setNextRetrievalUrls(initialUrls);
        fetcher.setNextRetrievalUrls(null);
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        assertThat(res, is(empty()));
    }
    
    @Test
    public void testGetNextRetrievalUrls_afterEmptyInput_returnsEmptyCollection() throws MalformedURLException {
        // Arrange
        Collection<URL> initialUrls = Arrays.asList(new URL[] {
            new URL("http://a/")
        });
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setNextRetrievalUrls(initialUrls);
        fetcher.setNextRetrievalUrls(new ArrayList<>());
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        assertThat(res, is(empty()));
    }
    
    @Test
    public void testGetNextRetrievalUrls_afterNonEmptyInput_returnsExpectedUrls() throws MalformedURLException {
        // Arrange
        Collection<URL> inputUrls = Arrays.asList(new URL[] {
            new URL("http://a/"),
            new URL("http://b/")
        });
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setNextRetrievalUrls(inputUrls);
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        assertThat(res.size(), is(inputUrls.size()));
        assertThat(res, containsInAnyOrder(inputUrls.toArray()));
    }
    
    @Test
    public void testGetNextRetrievalUrls_afterSecondNonEmptyInput_returnsUpdatedUrls() throws MalformedURLException {
        // Arrange
        Collection<URL> inputUrls = Arrays.asList(new URL[] {
            new URL("http://a/"),
            new URL("http://b/")
        });
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setNextRetrievalUrls(inputUrls);
        inputUrls = Arrays.asList(new URL[] {
            new URL("http://c/"),
            new URL("http://d/")
        });
        fetcher.setNextRetrievalUrls(inputUrls);
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        assertThat(res.size(), is(inputUrls.size()));
        assertThat(res, containsInAnyOrder(inputUrls.toArray()));
    }
    
    @Test
    public void testGetNextRetrievalUrls_nullInput_returnsUnmodifiableCopy() throws MalformedURLException {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setNextRetrievalUrls(null);
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        URL additionalUrl = new URL("http://c/");
        thrown.expect(UnsupportedOperationException.class);
        res.add(additionalUrl);
    }
    
    @Test
    public void testGetNextRetrievalUrls_emptyInput_returnsUnmodifiableCopy() throws MalformedURLException {
        // Arrange
        Collection<URL> inputUrls = new ArrayList<>();
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setNextRetrievalUrls(inputUrls);
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        URL additionalUrl = new URL("http://c/");
        thrown.expect(UnsupportedOperationException.class);
        res.add(additionalUrl);
    }
    
    @Test
    public void testGetNextRetrievalUrls_nonEmptyInput_returnsUnmodifiableCopy() throws MalformedURLException {
        // Arrange
        Collection<URL> inputUrls = new ArrayList<>(Arrays.asList(new URL[] {
            new URL("http://a/")
        }));
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setNextRetrievalUrls(inputUrls);
        
        // Act
        Collection<URL> res = fetcher.getNextRetrievalUrls();
        
        // Assert
        URL additionalUrl = new URL("http://c/");
        thrown.expect(UnsupportedOperationException.class);
        res.add(additionalUrl);
    }
    
    @Test
    public void testSetTemplateHttpRetrieval_null_logsWarning() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        fetcher.setTemplateHttpRetrieval(null);
        
        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        LoggingEvent expectedEvent = LoggingEvent.warn("unset HTTP retrieval template object, fetching will not work until a pre-configured instance is given");
        assertThat(loggingEvents, contains(expectedEvent));
    }
    
    @Test
    public void testSetTemplateHttpRetrieval_nullAfterNotNull_unsetsPreviousTemplate() throws MalformedURLException {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        fetcher.setTemplateHttpRetrieval(httpRetrieval);
        
        // Act
        fetcher.setTemplateHttpRetrieval(null);
        
        // Assert
        HttpRetrieval copy = fetcher.createHttpRetrieval();
        assertThat(copy, is(nullValue()));
    }
    
    @Test
    public void testSetTemplateHttpRetrieval_notNull_setsTemplateForCreation() throws MalformedURLException {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval httpRetrieval = new HttpRetrieval();
        String expectedUserAgent = "Test UserAgent";
        httpRetrieval.setUserAgent(expectedUserAgent);
        
        // Act
        fetcher.setTemplateHttpRetrieval(httpRetrieval);
        
        // Assert
        HttpRetrieval copy = fetcher.createHttpRetrieval();
        String actualUserAgent = copy.getUserAgent();
        assertThat(actualUserAgent, is(expectedUserAgent));
    }
    
    @Test
    public void testSetTemplateHttpRetrieval_notNull_invokesCopyCreation() {
        // Arrange
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        HttpRetrieval mockHttpRetrieval = mock(HttpRetrieval.class);
        when(mockHttpRetrieval.copyConfiguration()).thenReturn(mockHttpRetrieval);
        
        // Act
        fetcher.setTemplateHttpRetrieval(mockHttpRetrieval);
        
        // Assert
        verify(mockHttpRetrieval).copyConfiguration();
    }
    
    @Test
    public void testSetTemplateHttpRetrieval_notNull_doesNotLog() {
        // Arrange
        HttpRetrieval mockHttpRetrieval = mock(HttpRetrieval.class);
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        
        // Act
        fetcher.setTemplateHttpRetrieval(mockHttpRetrieval);
        
        // Assert
        List<LoggingEvent> loggingEvents = testLogger.getLoggingEvents();
        assertThat(loggingEvents, is(empty()));
    }
    
    @Test
    public void testFetch_withSingleUrls_requestsUrlByGetRequest() throws MalformedURLException, URISyntaxException {
        // Arrange
        final URL url = new URL("http://a/");
        HttpRetrieval mockHttpRetrieval = mock(HttpRetrieval.class);
        when(mockHttpRetrieval.copyConfiguration()).thenReturn(mockHttpRetrieval);
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setTemplateHttpRetrieval(mockHttpRetrieval);
        fetcher.setNextRetrievalUrls(Arrays.asList(new URL[]{ url }));
        
        // Act
        fetcher.fetch();
        
        // Assert
        verify(mockHttpRetrieval).requestByGet(eq(url.toString()));
    }
    
    @Test
    public void testFetch_withMultipleUrls_choosesOverAllUrls() throws MalformedURLException, URISyntaxException {
        // This test works by calling fetch many times and expecting every URL
        // to have been called at least a few times.
        
        // Arrange
        final Map<CharSequence, Integer> encounteredUris = new HashMap<>();
        HttpRetrieval mockHttpRetrieval = new HttpRetrieval() {
            @Override
            public HttpRetrieval copyConfiguration() {
                return this;
            }

            @Override
            public boolean requestByGet(CharSequence url) {
                Integer numCalls = encounteredUris.get(url);
                if (numCalls == null) {
                    numCalls = 0;
                }
                
                encounteredUris.put(url, ++numCalls);
                
                return true;
            }
        };
        RecurringHttpFetcher<Object> fetcher = new RecurringHttpFetcher<>();
        fetcher.setTemplateHttpRetrieval(mockHttpRetrieval);
        fetcher.setNextRetrievalUrls(Arrays.asList(new URL[]{
            new URL("http://a/"),
            new URL("http://b/"),
            new URL("http://c/")
        }));
        
        // Act
        for (int i=0; i<100; i++) {
            fetcher.fetch();
        }
        
        // Assert
        Set<Map.Entry<CharSequence, Integer>> entries = encounteredUris.entrySet();
        assertThat(entries.size(), is(3));
        assertThat(encounteredUris.keySet(), containsInAnyOrder(
            "http://a/",
            "http://b/",
            "http://c/"
        ));
        for (Map.Entry<CharSequence, Integer> entry : entries) {
            Integer numCalls = entry.getValue();
            assertThat(numCalls, is(greaterThanOrEqualTo(5)));
        }
    }
}
