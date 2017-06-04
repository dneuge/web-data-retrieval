package de.energiequant.vatplanner.commons.web;

/**
 * Defines a listener for registration to a NotificationHub.
 * @param <T> class of information to be propagated by hub
 */
public interface NotificationHubListener<T> {
    /**
     * Called when a NotificationHub propagates information to registered
     * listeners.
     * @param information propagated information object
     */
    public void handle(T information);
}
