package de.energiequant.vatplanner.commons.web;

import de.energiequant.vatplanner.commons.web.HttpRetrieval;
import de.energiequant.vatplanner.commons.web.entities.RetrievedInformation;
import java.net.URL;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecurringHttpFetcher<T> {
    private static final Logger logger = LoggerFactory.getLogger(RecurringHttpFetcher.class.getName());
    
    private HttpRetrieval templateHttpRetrieval = null;
    private RetrievedInformation<T> retrievedInformation = null;
    private Duration preferredRetrievalInterval = null;
    private Duration actualRetrievalInterval = Duration.of(30, ChronoUnit.MINUTES);
    private List<URL> nextRetrievalUrls = new ArrayList<>();
    
    private final Random random = new Random();
    
    /**
     * (Re-)Schedules the fetcher to run at the configured interval.
     * Job will be triggered immediately if runNow is being requested.
     * @param runNow Should job be triggered immediately?
     */
    public void schedule(final boolean runNow) {
        
    }
    
    /**
     * Unschedules the fetcher. Already running fetches may continue but
     * scheduler will cease to trigger further runs.
     */
    public void unschedule() {
        
    }
    
    /**
     * Returns all URLs from which next retrieval URL will be used at random.
     * @return all possible choices of URLs currently set for next retrieval
     */
    public List<URL> getNextRetrievalUrls() {
        synchronized (this) {
            return Collections.unmodifiableList(nextRetrievalUrls);
        }
    }
    
    /**
     * Sets the URLs to be chosen from on next retrieval. Only one URL will be
     * used at a time, chosen randomly on each run. Note that some retrieved
     * information contains redirect instructions so these URLs may be
     * externally updated over time.
     * @param urls URLs to choose from randomly
     */
    public void setNextRetrievalUrls(final Collection<URL> urls) {
        List<URL> copiedUrls;
        
        if (urls == null) {
            logger.warn("retrieval URLs were reset due to null input");
            copiedUrls = new ArrayList<>();
        } else {
            if (urls.isEmpty()) {
                logger.warn("retrieval URLs were reset due to empty input");
            }
            
            copiedUrls = new ArrayList<>(urls);
        }
        
        synchronized (this) {
            nextRetrievalUrls = copiedUrls;
        }
    }
    
    /**
     * Sets the preferred interval to retrieve information at. Actual intervals
     * may differ if preferred interval is more frequent than permitted.
     * @param interval preferred (not actual) interval
     */
    public void setPreferredRetrievalInterval(final Duration interval) {
        
    }

    /**
     * Returns the current actually used interval to retrieve information at.
     * This interval may be higher than the preferred one if external source
     * asks us to obey a minimum interval.
     * @return actual interval currently used to retrieve information
     */
    public Duration getActualRetrievalInterval() {
        return actualRetrievalInterval;
    }
    
    /**
     * Returns the latest retrieved information wrapped in a holder to add
     * fetch-related meta information.
     * @return latest retrieved information
     */
    public RetrievedInformation<T> getLatestRetrievedInformation() {
        return null;
    }
    
    /**
     * Provides the HTTP retrieval implementation to use as a template to be
     * used for actual retrieval.
     * Configuration of the given object will be copied to a new instance which
     * will then be used. The given instance will not be used to perform any
     * action other than creating an initial copy to be used to create further
     * copies. Any change to the provided instance will not have any effect
     * unless it is again passed to this method.
     * Passing null removes the currently used template, rendering the fetcher
     * inoperable.
     * @param templateHttpRetrieval readily configured instance to create a template copy from; null disables fetcher operation
     */
    public void setTemplateHttpRetrieval(final HttpRetrieval templateHttpRetrieval) {
        if (templateHttpRetrieval != null) {
            this.templateHttpRetrieval = templateHttpRetrieval.copyConfiguration();
        } else {
            this.templateHttpRetrieval = null;
            logger.warn("unset HTTP retrieval template object, fetching will not work until a pre-configured instance is given");
        }
    }
    
    /**
     * Creates a new instance of HttpRetrieval using the previously supplied
     * template.
     * @return new instance following template configuration
     */
    protected HttpRetrieval createHttpRetrieval() {
        HttpRetrieval httpRetrieval = null;
        
        if (templateHttpRetrieval != null) {
            httpRetrieval = templateHttpRetrieval.copyConfiguration();
        } else {
            logger.warn("requested HttpRetrieval instance without any template");
        }
        
        return httpRetrieval;
    }
    
    /**
     * Fetches and updates all information once. Also applies new retrieval
     * interval if a change is required/possible and follows redirects.
     */
    protected void fetch() {
        /*
        // get a fixed reference to the HttpClient to use
        HttpClient client;
        synchronized (this) {
            client = providedHttpClient;
        }
        
        if (client == null) {
            logger.error("unable to fetch without HTTP client");
            return;
        }
        
        // get a fixed copy of URLs to use
        List<URL> urls = getNextRetrievalUrls();
        
        if (urls.isEmpty()) {
            logger.error("attempted to fetch without any URL to fetch from");
            return;
        }
        
        // choose random URL to fetch from
        URL url;
        synchronized (random) {
            url = urls.get(random.nextInt(urls.size()));
        }
        
        // convert URL to URI
        URI uri;
        try {
            uri = url.toURI();
        } catch (URISyntaxException ex) {
            logger.warn("provided with an URL that was not a valid URI: \"{}\"", url);
            return;
        }
        
        // build request
        HttpGet request = new HttpGet(uri);
        
        // execute request
        HttpResponse response;
        try {
            response = client.execute(request);
        } catch (IOException ex) {
            // TODO: handle exception as error
            
            //logger.warn("", ex);
        }
        
        // TODO: handle status error codes
        
        // TODO: parse response body, store as retrievedInformation
        
        // TODO: update next retrieval URLs from optional redirect instructions of parsed content
        // TODO: update retrieval interval
        
        // TODO: count consecutive errors/redirects, run configurable callback
        //       if limit is exceeded (should later be used to terminate a
        //       standalone fetcher for automated restart in known configuration)
        */
    }
}
