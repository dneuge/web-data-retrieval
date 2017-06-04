package de.energiequant.vatplanner.commons.web;

import java.util.HashSet;
import java.util.Set;

/**
 * A basic hub to distribute objects to listeners.
 * Information object is passed by reference directly to listeners, so be
 * aware that each listener is able to modify the object before it is passed
 * on or continued to be processed outside notification handling. If necessary,
 * make information immutable before distribution.
 * @param <T> class of information object to distribute to listeners
 */
public class NotificationHub<T> {
    private final Set<NotificationHubListener<T>> listeners = new HashSet<>();
    
    /**
     * Adds a new listener to be notified through this hub.
     * @param listener listener to notify
     */
    public void addListener(NotificationHubListener<T> listener) {
        if (listener == null) {
            return;
        }
        
        synchronized (listeners) {
            listeners.add(listener);
        }
    }
    
    /**
     * Notifies all registered listeners, passing the given information object
     * by reference.
     * Listeners are able to modify the information object, thus it should be
     * made immutable before handing over for distribution, if required.
     * @param information object to pass to listeners; may get tainted by listeners
     */
    public void distribute(final T information) {
        synchronized (listeners) {
            for (NotificationHubListener<T> listener : listeners) {
                listener.handle(information);
            }
        }
    }
}
