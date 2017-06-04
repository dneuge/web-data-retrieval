package de.energiequant.vatplanner.commons.web;

/**
 * A parser which processes HttpRetrievals to objects of the given class.
 * @param <T> class of parsed information objects
 */
public interface HttpRetrievalParser<T> {
    /**
     * Parses the given HttpRetrieval to a target class object.
     * If the retrieved data is erroneous and cannot be processed, parsing
     * should return null instead of an object.
     * @param retrieval previously received HttpRetrieval which should be parsed
     * @return information parsed from the given retrieval; null on error
     */
    public T parse(HttpRetrieval retrieval);
}
