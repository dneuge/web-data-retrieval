package de.energiequant.common.webdataretrieval;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;
import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.InOrder;
import org.mockito.Mockito;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

@RunWith(DataProviderRunner.class)
public class DefaultHttpRetrievalDecodersTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DefaultHttpRetrievalDecoders spyDecoders;

    @Before
    public void setUp() {
        DefaultHttpRetrievalDecoders templateDecoders = new DefaultHttpRetrievalDecoders();
        spyDecoders = spy(templateDecoders);
    }

    @DataProvider
    public static Object[][] dataproviderCharsetDecoding() {
        return new Object[][]{
            new Object[]{byteArray(0xc3, 0xa4, 0xc3, 0xb6, 0xc3, 0xbc, 0x0a, 0xc3, 0x9f), "UTF-8", Charset.forName("UTF-8"), unescapeHtml4("&auml;&ouml;&uuml;\n&szlig;")},
            new Object[]{byteArray(0xe4, 0xf6, 0xfc, 0x0a, 0xdf), "latin1", Charset.forName("ISO-8859-1"), unescapeHtml4("&auml;&ouml;&uuml;\n&szlig;")},};
    }

    private static byte[] byteArray(int... bytes) {
        byte[] out = new byte[bytes.length];

        for (int i = 0; i < bytes.length; i++) {
            out[i] = (byte) bytes[i];
        }

        return out;
    }

    @Test
    public void testBodyAsStringWithFixedCharacterSet_nullCharacterSet_throwsIllegalArgumentException() {
        // Arrange
        thrown.expect(IllegalArgumentException.class);

        // Act
        spyDecoders.bodyAsStringWithFixedCharacterSet(null);

        // Assert (nothing to do)
    }

    @Test
    @UseDataProvider("dataproviderCharsetDecoding")
    public void testBodyAsStringWithFixedCharacterSet_always_decodesBodyWithGivenCharacterSet(byte[] bytes, String charsetName, Charset charset, String expectedResult) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);
        when(mockRetrieval.getResponseBodyBytes()).thenReturn(bytes);

        // Act
        String res = spyDecoders.bodyAsStringWithFixedCharacterSet(charset).apply(mockRetrieval);

        // Assert
        assertThat(res, is(equalTo(expectedResult)));
    }

    @Test
    public void testBodyAsStringWithHeaderCharacterSet_nullCharacterSet_throwsIllegalArgumentException() {
        // Arrange
        thrown.expect(IllegalArgumentException.class);

        // Act
        spyDecoders.bodyAsStringWithHeaderCharacterSet(null);

        // Assert (nothing to do)
    }

    @Test
    @UseDataProvider("dataproviderCharsetDecoding")
    public void testBodyAsStringWithHeaderCharacterSet_headerWithCorrectCharset_decodesBodyWithHeaderCharacterSet(byte[] bytes, String charsetName, Charset charset, String expectedResult) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class, Answers.RETURNS_DEEP_STUBS);
        when(mockRetrieval.getResponseBodyBytes()).thenReturn(bytes);
        when(mockRetrieval.getResponseHeaders().getFirstByName(argEqIgnoreCase("content-type"))).thenReturn("text/plain; charset=" + charsetName);

        Charset wrongCharset = !charset.name().equals("KOI8-R") ? Charset.forName("KOI8-R") : Charset.forName("UTF-8");

        // Precondition
        assertThat("Precondition: wrong and correct charsets must not decode to same result", new String(bytes, wrongCharset), is(not(equalTo(new String(bytes, charset)))));

        // Act
        String res = spyDecoders.bodyAsStringWithHeaderCharacterSet(wrongCharset).apply(mockRetrieval);

        // Assert
        assertThat(res, is(equalTo(expectedResult)));
    }

    @Test
    @UseDataProvider("dataproviderCharsetDecoding")
    public void testBodyAsStringWithHeaderCharacterSet_headerWithUnknownCharset_decodesBodyWithFallbackCharacterSet(byte[] bytes, String charsetName, Charset charset, String expectedResult) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class, Answers.RETURNS_DEEP_STUBS);
        when(mockRetrieval.getResponseBodyBytes()).thenReturn(bytes);
        when(mockRetrieval.getResponseHeaders().getFirstByName(argEqIgnoreCase("content-type"))).thenReturn("text/plain; charset=DEFINITELY-UNKNOWN");

        // Act
        String res = spyDecoders.bodyAsStringWithHeaderCharacterSet(charset).apply(mockRetrieval);

        // Assert
        assertThat(res, is(equalTo(expectedResult)));
    }

    @Test
    @UseDataProvider("dataproviderCharsetDecoding")
    public void testBodyAsStringWithHeaderCharacterSet_headerWithoutCharset_decodesBodyWithFallbackCharacterSet(byte[] bytes, String charsetName, Charset charset, String expectedResult) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class, Answers.RETURNS_DEEP_STUBS);
        when(mockRetrieval.getResponseBodyBytes()).thenReturn(bytes);
        when(mockRetrieval.getResponseHeaders().getFirstByName(argEqIgnoreCase("content-type"))).thenReturn("text/plain");

        // Act
        String res = spyDecoders.bodyAsStringWithHeaderCharacterSet(charset).apply(mockRetrieval);

        // Assert
        assertThat(res, is(equalTo(expectedResult)));
    }

    @Test
    @UseDataProvider("dataproviderCharsetDecoding")
    public void testBodyAsStringWithHeaderCharacterSet_headerMissing_decodesBodyWithFallbackCharacterSet(byte[] bytes, String charsetName, Charset charset, String expectedResult) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class, Answers.RETURNS_DEEP_STUBS);
        when(mockRetrieval.getResponseBodyBytes()).thenReturn(bytes);
        when(mockRetrieval.getResponseHeaders().getFirstByName(argEqIgnoreCase("content-type"))).thenReturn(null);

        // Act
        String res = spyDecoders.bodyAsStringWithHeaderCharacterSet(charset).apply(mockRetrieval);

        // Assert
        assertThat(res, is(equalTo(expectedResult)));
    }

    @Test
    public void testWithMetaData_nullDecoder_throwsIllegalArgumentException() {
        // Arrange
        thrown.expect(IllegalArgumentException.class);

        // Act
        spyDecoders.withMetaData(null);

        // Assert (nothing to do)
    }

    @Test
    public void testWithMetaData_anyDecoder_containerHoldsDecodedResult() {
        // Arrange
        Object expectedResult = new Object();

        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);

        Function<HttpRetrieval, Object> mockDecoder = mock(Function.class);
        when(mockDecoder.apply(Mockito.same(mockRetrieval))).thenReturn(expectedResult);

        // Act
        RetrievedData<Object> result = spyDecoders.withMetaData(mockDecoder).apply(mockRetrieval);

        // Assert
        assertThat(result.getData(), is(sameInstance(expectedResult)));
    }

    @Test
    @DataProvider({"1523805621000", "123456"})
    public void testWithMetaData_anyDecoder_containerHoldsCurrentTime(long expectedEpochMillis) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);

        Function<HttpRetrieval, Object> mockDecoder = mock(Function.class);

        doReturn(Instant.ofEpochMilli(expectedEpochMillis)).when(spyDecoders).getInstantNow();

        // Act
        RetrievedData<Object> result = spyDecoders.withMetaData(mockDecoder).apply(mockRetrieval);

        // Assert
        assertThat(result.getRetrievedTime().toEpochMilli(), is(equalTo(expectedEpochMillis)));
    }

    @Test
    public void testWithMetaData_anyDecoder_timeIsOnlyQueriedBeforeDecoding() {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);

        Function<HttpRetrieval, Object> mockDecoder = mock(Function.class);

        // Act
        spyDecoders.withMetaData(mockDecoder).apply(mockRetrieval);

        // Assert
        InOrder inOrder = inOrder(spyDecoders, mockDecoder);
        inOrder.verify(spyDecoders).getInstantNow();
        inOrder.verify(mockDecoder).apply(Mockito.any(HttpRetrieval.class));
        inOrder.verify(spyDecoders, never()).getInstantNow();
        verifyZeroInteractions(mockDecoder);
    }

    @Test
    @DataProvider({"http://something/somewhere", "https://whatever/"})
    public void testWithMetaData_anyDecoder_containerHoldsLastRequestedLocation(String expectedLocation) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);
        doReturn(expectedLocation).when(mockRetrieval).getLastRequestedLocation();

        Function<HttpRetrieval, Object> mockDecoder = mock(Function.class);

        // Act
        RetrievedData<Object> result = spyDecoders.withMetaData(mockDecoder).apply(mockRetrieval);

        // Assert
        assertThat(result.getRequestedLocation(), is(equalTo(expectedLocation)));
    }

    @Test
    public void testWithMetaData_nullForLastRequestedLocation_containerHoldsNullAsLastRequestedLocation() {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);
        doReturn(null).when(mockRetrieval).getLastRequestedLocation();

        Function<HttpRetrieval, Object> mockDecoder = mock(Function.class);

        // Act
        RetrievedData<Object> result = spyDecoders.withMetaData(mockDecoder).apply(mockRetrieval);

        // Assert
        assertThat(result.getRequestedLocation(), is(nullValue()));
    }

    @Test
    @DataProvider({"http://something/somewhere", "https://whatever/"})
    public void testWithMetaData_anyDecoder_containerHoldsLastRetrievedLocation(String expectedLocation) {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);
        doReturn(expectedLocation).when(mockRetrieval).getLastRetrievedLocation();

        Function<HttpRetrieval, Object> mockDecoder = mock(Function.class);

        // Act
        RetrievedData<Object> result = spyDecoders.withMetaData(mockDecoder).apply(mockRetrieval);

        // Assert
        assertThat(result.getRetrievedLocation(), is(equalTo(expectedLocation)));
    }

    @Test
    public void testWithMetaData_nullForLastRetrievedLocation_containerHoldsNullAsLastRetrievedLocation() {
        // Arrange
        HttpRetrieval mockRetrieval = mock(HttpRetrieval.class);
        doReturn(null).when(mockRetrieval).getLastRetrievedLocation();

        Function<HttpRetrieval, Object> mockDecoder = mock(Function.class);

        // Act
        RetrievedData<Object> result = spyDecoders.withMetaData(mockDecoder).apply(mockRetrieval);

        // Assert
        assertThat(result.getRetrievedLocation(), is(nullValue()));
    }

    @Test
    public void testGetInstantNow_always_returnsCurrentInstant() {
        // Arrange
        Instant myInstant = Instant.now();

        // Act
        Instant theirInstant = spyDecoders.getInstantNow();

        // Assert
        long millisecondsBetweenInstants = Duration.between(myInstant, theirInstant).toMillis();
        assertThat(millisecondsBetweenInstants, is(lessThan(500L)));
    }

    private static String argEqIgnoreCase(String expected) {
        return Mockito.argThat(s -> s.equalsIgnoreCase(expected));
    }

}
