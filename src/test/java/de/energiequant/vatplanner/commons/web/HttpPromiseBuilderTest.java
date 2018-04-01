package de.energiequant.vatplanner.commons.web;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@RunWith(DataProviderRunner.class)
public class HttpPromiseBuilderTest {
    @Rule
    public ExpectedException thrown = ExpectedException.none();
    
    private HttpPromiseBuilder<Object> spyBuilder;
    private Function<HttpRetrieval, Object> mockDecoder;
    private HttpRetrieval mockRetrieval;
    private HttpRetrieval mockDefaultConfigurationTemplate;
    
    @Before
    public void setUp() {
        mockDecoder = mock(Function.class);
        mockDefaultConfigurationTemplate = mock(HttpRetrieval.class);
        
        HttpPromiseBuilder<Object> templateBuilder = new HttpPromiseBuilder(mockDecoder) {
            @Override
            HttpRetrieval createDefaultConfigurationTemplate() {
                return mockDefaultConfigurationTemplate;
            }
        };
        spyBuilder = spy(templateBuilder);

        mockRetrieval = mockHttpRetrievalIndicatingSuccess();
        doReturn(mockRetrieval).when(spyBuilder).createRetrieval();
    }

    @Test
    public void testRequestByGet_completed_returnsDecoderResult() throws Exception {
        // Arrange
        Object expectedDecoderResult = new Object();
        when(mockDecoder.apply(Mockito.any(HttpRetrieval.class))).thenReturn(expectedDecoderResult);
        
        // Act
        Object res = spyBuilder.requestByGet("http://myUrl.local/").get();
        
        // Assert
        assertThat(res, is(sameInstance(expectedDecoderResult)));
    }
    
    @Test
    public void testRequestByGet_completed_appliesRetrievalToDecoderAfterRequest() throws Exception {
        // Arrange (nothing to do)
        
        // Act
        spyBuilder.requestByGet("http://myUrl.local/").join();
        
        // Assert
        InOrder inOrder = inOrder(mockDecoder, mockRetrieval);
        inOrder.verify(mockRetrieval).requestByGet(Mockito.any(CharSequence.class));
        inOrder.verify(mockDecoder).apply(Mockito.same(mockRetrieval));
    }
    
    @Test
    @DataProvider({"http://first.url.local/abc.html", "https://something.else.local/?id=123"})
    public void testRequestByGet_always_passesUrlToRetrieval(String expectedUrl) throws Exception {
        // Arrange (nothing to do)
        
        // Act
        spyBuilder.requestByGet(expectedUrl).join();
        
        // Assert
        verify(mockRetrieval).requestByGet(Mockito.eq(expectedUrl));
    }
    
    @Test
    public void testRequestByGet_unconfigured_appliesDefaultConfiguration() {
        // Arrange (nothing to do)
        
        // Act
        spyBuilder.requestByGet("http://myUrl.local/").join();
        
        // Assert
        InOrder inOrder = inOrder(mockDefaultConfigurationTemplate, mockRetrieval);
        inOrder.verify(mockDefaultConfigurationTemplate).copyConfigurationTo(Mockito.same(mockRetrieval));
        inOrder.verify(mockRetrieval, never()).setMaximumFollowedRedirects(Mockito.anyInt());
        inOrder.verify(mockRetrieval, never()).setTimeout(Mockito.any(Duration.class));
        inOrder.verify(mockRetrieval, never()).setUserAgent(Mockito.anyString());
        inOrder.verify(mockRetrieval).requestByGet(Mockito.any(CharSequence.class));
    }
    
    @Test
    public void testRequestByGet_withConfiguration_appliesGivenConfiguration() {
        // Arrange
        HttpRetrieval mockCustomConfiguration = mock(HttpRetrieval.class);
        
        // Act
        spyBuilder.withConfiguration(mockCustomConfiguration).requestByGet("http://myUrl.local/").join();
        
        // Assert
        InOrder inOrder = inOrder(mockCustomConfiguration, mockRetrieval);
        inOrder.verify(mockCustomConfiguration).copyConfigurationTo(Mockito.same(mockRetrieval));
        inOrder.verify(mockRetrieval, never()).setMaximumFollowedRedirects(Mockito.anyInt());
        inOrder.verify(mockRetrieval, never()).setTimeout(Mockito.any(Duration.class));
        inOrder.verify(mockRetrieval, never()).setUserAgent(Mockito.anyString());
        inOrder.verify(mockRetrieval).requestByGet(Mockito.any(CharSequence.class));
    }
    
    @Test
    public void testRequestByGet_secondCallNotReconfigured_appliesConfigurationFromFirstCall() {
        // Arrange
        HttpRetrieval mockCustomConfiguration = mock(HttpRetrieval.class);
        
        spyBuilder.withConfiguration(mockCustomConfiguration).requestByGet("http://myUrl.local/").join();
        reset(mockCustomConfiguration);
        resetHttpRetrievalIndicatingSuccess(mockRetrieval);
        
        // Act
        spyBuilder.requestByGet("http://myUrl.local/").join();
        
        // Assert
        InOrder inOrder = inOrder(mockCustomConfiguration, mockRetrieval);
        inOrder.verify(mockCustomConfiguration).copyConfigurationTo(Mockito.same(mockRetrieval));
        inOrder.verify(mockRetrieval, never()).setMaximumFollowedRedirects(Mockito.anyInt());
        inOrder.verify(mockRetrieval, never()).setTimeout(Mockito.any(Duration.class));
        inOrder.verify(mockRetrieval, never()).setUserAgent(Mockito.anyString());
        inOrder.verify(mockRetrieval).requestByGet(Mockito.any(CharSequence.class));
    }
    
    @Test
    public void testRequestByGet_secondCallReconfigured_appliesConfigurationFromSecondCall() {
        // Arrange
        HttpRetrieval mockFirstCustomConfiguration = mock(HttpRetrieval.class);
        
        spyBuilder.withConfiguration(mockFirstCustomConfiguration).requestByGet("http://myUrl.local/").join();
        reset(mockFirstCustomConfiguration);
        resetHttpRetrievalIndicatingSuccess(mockRetrieval);
        
        HttpRetrieval mockSecondCustomConfiguration = mock(HttpRetrieval.class);
        
        // Act
        spyBuilder.withConfiguration(mockSecondCustomConfiguration).requestByGet("http://myUrl.local/").join();
        
        // Assert
        InOrder inOrder = inOrder(mockSecondCustomConfiguration, mockRetrieval);
        inOrder.verify(mockSecondCustomConfiguration).copyConfigurationTo(Mockito.same(mockRetrieval));
        inOrder.verify(mockRetrieval, never()).setMaximumFollowedRedirects(Mockito.anyInt());
        inOrder.verify(mockRetrieval, never()).setTimeout(Mockito.any(Duration.class));
        inOrder.verify(mockRetrieval, never()).setUserAgent(Mockito.anyString());
        inOrder.verify(mockRetrieval).requestByGet(Mockito.any(CharSequence.class));
    }
    
    @Test
    public void testRequestByGet_always_retrievalRunsAsynchronously() throws Exception {
        // Arrange
        final Object syncObject = new Object();
        
        doAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                // block until testcase thread says we are allowed to continue
                // but don't wait forever
                synchronized (syncObject) {
                    syncObject.wait(2000);
                }
                
                return true;
            }
        }).when(mockRetrieval).requestByGet(Mockito.any(CharSequence.class));
        
        when(mockDecoder.apply(Mockito.any(HttpRetrieval.class))).thenReturn(new Object()); // causes "not null" to be available from future when finished
        
        // Act 1: start and delay
        CompletableFuture<?> future = spyBuilder.requestByGet("http://myUrl.local/");
        
        // Assert 1: must not have run past sync yet
        Object interimResult = future.getNow(null); // returns null if not finished
        assertThat("Promise should not have run past sync yet (if it did, it did not spawn async)", interimResult, is(nullValue()));
        
        // Act 2: continue answer execution and allow answer thread to complete
        synchronized (syncObject) {
            syncObject.notify();
        }
        
        while (!future.isDone()) {
            Thread.yield();
        }
        
        // Assert 2: we now should have a result
        Object finalResult = future.getNow(null); // returns null if not finished
        assertThat("Promise should have run past sync now (completed/joined thread again)", finalResult, is(notNullValue()));
    }
    
    @Test
    public void testRequestByGet_requestSucceedsAndHasFullContent_futureCompletesNormally() throws Exception {
        // Arrange
        Function<Throwable, Object> mockExceptionHandler = mock(Function.class);
        
        // Act
        spyBuilder.requestByGet("http://myUrl.local/").exceptionally(mockExceptionHandler).join();
        
        // Assert
        verify(mockExceptionHandler, never()).apply(Mockito.any(Exception.class));
    }
    
    @Test
    public void testRequestByGet_requestFailsOnNetworkLevel_futureCompletesExceptionallyWithRuntimeException() throws Exception {
        // Arrange
        when(mockRetrieval.requestByGet(Mockito.any(CharSequence.class))).thenReturn(false);
        
        Function<Throwable, Object> mockExceptionHandler = mock(Function.class);
        
        // Act
        spyBuilder.requestByGet("http://myUrl.local/").exceptionally(mockExceptionHandler).join();
        
        // Assert
        verify(mockExceptionHandler).apply(Mockito.isA(RuntimeException.class));
    }
    
    @Test
    public void testRequestByGet_retrievalHasIncompleteContent_futureCompletesExceptionallyWithRuntimeException() throws Exception {
        // Arrange
        when(mockRetrieval.hasCompleteContentResponseStatus()).thenReturn(false);
        
        Function<Throwable, Object> mockExceptionHandler = mock(Function.class);
        
        // Act
        spyBuilder.requestByGet("http://myUrl.local/").exceptionally(mockExceptionHandler).join();
        
        // Assert
        verify(mockExceptionHandler).apply(Mockito.isA(RuntimeException.class));
    }
    
    @Test
    public void testRequestByGet_decodingThrowsException_futureCompletesExceptionally() throws Exception {
        // Arrange
        Function<Throwable, Object> mockExceptionHandler = mock(Function.class);
        
        RuntimeException decoderException = new RuntimeException();
        when(mockDecoder.apply(Mockito.any(HttpRetrieval.class))).thenThrow(decoderException);
        
        // Act
        spyBuilder.requestByGet("http://myUrl.local/").exceptionally(mockExceptionHandler).join();
        
        // Assert
        verify(mockExceptionHandler).apply(Mockito.any(Exception.class));
    }
    
    @Test
    public void testWithConfiguration_notNull_returnsSameBuilder() {
        // Arrange
        HttpRetrieval mockCustomConfiguration = mock(HttpRetrieval.class);
        
        // Act
        HttpPromiseBuilder<?> res = spyBuilder.withConfiguration(mockCustomConfiguration);
        
        // Assert
        assertThat(res, is(sameInstance(spyBuilder)));
    }
    
    @Test
    public void testWithConfiguration_null_throwsIllegalArgumentException() {
        // Arrange
        thrown.expect(IllegalArgumentException.class);
        
        // Act
        spyBuilder.withConfiguration(null);
        
        // Assert (nothing to do)
    }

    private HttpRetrieval mockHttpRetrievalIndicatingSuccess() {
        HttpRetrieval mock = mock(HttpRetrieval.class);
        stubHttpRetrievalIndicatingSuccess(mock);
        
        return mock;
    }

    private void resetHttpRetrievalIndicatingSuccess(HttpRetrieval mockRetrieval) {
        reset(mockRetrieval);
        stubHttpRetrievalIndicatingSuccess(mockRetrieval);
    }

    private void stubHttpRetrievalIndicatingSuccess(HttpRetrieval mockRetrieval) {
        when(mockRetrieval.requestByGet(Mockito.any(CharSequence.class))).thenReturn(true);
        when(mockRetrieval.hasCompleteContentResponseStatus()).thenReturn(true);
    }
}
