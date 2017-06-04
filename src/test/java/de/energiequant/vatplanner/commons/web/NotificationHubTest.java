package de.energiequant.vatplanner.commons.web;

import org.junit.Test;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NotificationHubTest {
    @Test
    public void testDistribute_twoListeners_distributesSameObjectToBothListeners() {
        // Arrange
        NotificationHub<Object> hub = new NotificationHub<>();
        NotificationHubListener<Object> listener1 = mock(NotificationHubListener.class);
        NotificationHubListener<Object> listener2 = mock(NotificationHubListener.class);
        hub.addListener(listener1);
        hub.addListener(listener2);
        Object obj = new Object();
        
        // Act
        hub.distribute(obj);
        
        // Assert
        verify(listener1).handle(Mockito.same(obj));
        verify(listener2).handle(Mockito.same(obj));
    }
    
    @Test
    public void testDistribute_sameListenerTwice_distributesOnlyOnce() {
        // Arrange
        NotificationHub<Object> hub = new NotificationHub<>();
        NotificationHubListener<Object> listener = mock(NotificationHubListener.class);
        hub.addListener(listener);
        hub.addListener(listener);
        Object obj = new Object();
        
        // Act
        hub.distribute(obj);
        
        // Assert
        verify(listener, Mockito.times(1)).handle(Mockito.any());
    }
    
    @Test
    public void testDistribute_nullListener_doesNotThrowAnyException() {
        // Arrange
        NotificationHub<Object> hub = new NotificationHub<>();
        hub.addListener(null);
        Object obj = new Object();
        
        // Act
        hub.distribute(obj);
        
        // Assert (nothing, just expect no exception :) )
    }
}
