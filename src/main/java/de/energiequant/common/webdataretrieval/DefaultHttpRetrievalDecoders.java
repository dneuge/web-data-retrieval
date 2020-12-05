package de.energiequant.common.webdataretrieval;

import java.nio.charset.Charset;
import java.time.Instant;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides builders for some commonly used functions for decoding of
 * {@link HttpRetrieval}s.
 */
public class DefaultHttpRetrievalDecoders {

    private static final String HTTP_HEADER_CONTENT_TYPE = "Content-Type";

    private static final Pattern PATTERN_HTTP_HEADER_CONTENT_TYPE = Pattern.compile(".*;\\s*charset=(\\S+).*");
    private static final int PATTERN_HTTP_HEADER_CONTENT_TYPE_CHARSET = 1;

    /**
     * Builds a decoder which provides meta data about retrieval by wrapping the
     * result of another decoder into a {@link RetrievedData} container.
     *
     * @param <T> type returned by wrapped decoder
     * @param decoder decoder to be wrapped, must not be null
     * @return results of given decoder inside a container holding meta data about
     *         retrieval
     */
    public <T> Function<HttpRetrieval, RetrievedData<T>> withMetaData(Function<HttpRetrieval, T> decoder) {
        if (decoder == null) {
            throw new IllegalArgumentException("decoder must not be null");
        }

        return new Function<HttpRetrieval, RetrievedData<T>>() {
            @Override
            public RetrievedData<T> apply(HttpRetrieval retrieval) {
                Instant time = getInstantNow();
                String lastRequestedLocation = retrieval.getLastRequestedLocation();
                String lastRetrievedLocation = retrieval.getLastRetrievedLocation();

                T data = decoder.apply(retrieval);

                return new RetrievedData<>(time, lastRequestedLocation, lastRetrievedLocation, data);
            }
        };
    }

    /**
     * Builds a decoder which always decodes the response body with a fixed
     * character set.
     *
     * @param characterSet character set to apply for decoding
     * @return response body decoded with specified character set
     */
    public Function<HttpRetrieval, String> bodyAsStringWithFixedCharacterSet(Charset characterSet) {
        if (characterSet == null) {
            throw new IllegalArgumentException("character set must not be null");
        }

        return new Function<HttpRetrieval, String>() {
            @Override
            public String apply(HttpRetrieval retrieval) {
                byte[] bytes = retrieval.getResponseBodyBytes();
                return new String(bytes, characterSet);
            }
        };
    }

    /**
     * Builds a decoder which decodes the response body using the character set
     * specified in HTTP headers. If header-specified character set is unavailable,
     * the given fallback character set will be used instead.
     *
     * @param fallbackCharacterSet fallback character set to apply if retrieving
     *        character set from header fails
     * @return response body decoded with header-specified or fallback character set
     */
    public Function<HttpRetrieval, String> bodyAsStringWithHeaderCharacterSet(Charset fallbackCharacterSet) {
        if (fallbackCharacterSet == null) {
            throw new IllegalArgumentException("character set must not be null");
        }

        return new Function<HttpRetrieval, String>() {
            @Override
            public String apply(HttpRetrieval retrieval) {
                Charset charset = getCharacterSetByContentType(retrieval);
                if (charset == null) {
                    charset = fallbackCharacterSet;
                }

                byte[] bytes = retrieval.getResponseBodyBytes();

                return new String(bytes, charset);
            }
        };
    }

    /**
     * Returns the character set to be applied according to Content-Type HTTP
     * header.
     *
     * @param retrieval used to retrieve response headers from
     * @return character set specified in Content-Type header; null if unavailable
     */
    private Charset getCharacterSetByContentType(HttpRetrieval retrieval) {
        String charsetName = extractCharacterSetNameFromContentType(retrieval);

        try {
            return Charset.forName(charsetName);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Extracts the character set name from Content-Type HTTP header.
     *
     * @param retrieval used to retrieve response headers from
     * @return character set name as specified in Content-Type header; null if
     *         missing
     */
    private String extractCharacterSetNameFromContentType(HttpRetrieval retrieval) {
        String contentType = retrieval.getResponseHeaders().getFirstByName(HTTP_HEADER_CONTENT_TYPE);

        if (contentType == null) {
            return null;
        }

        Matcher matcher = PATTERN_HTTP_HEADER_CONTENT_TYPE.matcher(contentType);
        if (matcher.matches()) {
            return matcher.group(PATTERN_HTTP_HEADER_CONTENT_TYPE_CHARSET);
        }

        return null;
    }

    /**
     * Returns current time via static method call {@link Instant#now()}. Required
     * for unit-testing.
     *
     * @return current time via {@link Instant#now()}
     */
    Instant getInstantNow() {
        return Instant.now();
    }
}
