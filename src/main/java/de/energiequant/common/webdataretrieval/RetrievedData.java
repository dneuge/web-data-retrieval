package de.energiequant.common.webdataretrieval;

import java.time.Instant;

/**
 * Holds meta data information about a retrieved data object.
 * <p>
 * {@link DefaultHttpRetrievalDecoders#withMetadata(java.util.function.Function)}
 * can be used to easily wrap decoded information into this container with a
 * {@link HttpPromiseBuilder}.
 * </p>
 * <p>
 * The holder is immutable.
 * </p>
 * @param <T> type of information to hold
 */
public class RetrievedData<T> {
    private final Instant retrievedTime;
    private final String requestedLocation;
    private final String retrievedLocation;
    private final T data;

    /**
     * Creates a new holder with given meta data description.
     * @param retrievedTime time of completed data retrieval
     * @param requestedLocation initially requested data location
     * @param retrievedLocation actual location of retrieved data
     * @param data retrieved data
     */
    public RetrievedData(Instant retrievedTime, String requestedLocation, String retrievedLocation, T data) {
        this.retrievedTime = retrievedTime;
        this.requestedLocation = requestedLocation;
        this.retrievedLocation = retrievedLocation;
        this.data = data;
    }

    /**
     * Returns the time data has been completed retrieval at.
     * @return time of completed data retrieval
     */
    public Instant getRetrievedTime() {
        return retrievedTime;
    }

    /**
     * Returns the description of initially requested data location.
     * This may not match the actual location of retrieved data if redirects
     * had to be followed.
     * See {@link #getRetrievedLocation()} for the actual location after any
     * redirects.
     * @return initially requested data location
     */
    public String getRequestedLocation() {
        return requestedLocation;
    }

    /**
     * Returns the description of actual retrieved data location.
     * This describes the location after following any redirects.
     * See {@link #getRequestedLocation()} if you need to know the
     * initially requested location.
     * @return actual location of retrieved data
     */
    public String getRetrievedLocation() {
        return retrievedLocation;
    }

    /**
     * Returns the retrieved data.
     * @return retrieved data
     */
    public T getData() {
        return data;
    }
}
