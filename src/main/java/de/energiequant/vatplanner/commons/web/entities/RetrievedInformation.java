package de.energiequant.vatplanner.commons.web.entities;

import java.net.URL;
import java.time.Instant;

public class RetrievedInformation<T> {
    private Instant retrievalTimestamp;
    private URL sourceUrl;
    private T information;
}
