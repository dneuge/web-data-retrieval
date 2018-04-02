package de.energiequant.vatplanner.commons.web;

import com.google.common.hash.Funnels;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import java.nio.charset.Charset;
import static org.apache.commons.lang3.StringEscapeUtils.unescapeHtml4;
import org.junit.Test;
import org.mockito.Mockito;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
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
            new Object[]{ byteArray(0xc3, 0xa4, 0xc3, 0xb6, 0xc3, 0xbc, 0x0a, 0xc3, 0x9f), "UTF-8", Charset.forName("UTF-8"), unescapeHtml4("&auml;&ouml;&uuml;\n&szlig;") },
            new Object[]{ byteArray(0xe4, 0xf6, 0xfc, 0x0a, 0xdf), "latin1", Charset.forName("ISO-8859-1"), unescapeHtml4("&auml;&ouml;&uuml;\n&szlig;") },
        };
    }
    
    private static byte[] byteArray(int... bytes) {
        byte[] out = new byte[bytes.length];
        
        for (int i=0; i<bytes.length; i++) {
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
        when(mockRetrieval.getResponseHeaders().getFirstByName(argEqIgnoreCase("content-type"))).thenReturn("text/plain; charset="+charsetName);
        
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

    private static String argEqIgnoreCase(String expected) {
        return Mockito.argThat(s -> s.equalsIgnoreCase(expected));
    }
    
}
