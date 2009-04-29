/* 
@ITMillApache2LicenseForJavaFiles@
 */

package com.itmill.toolkit.event;

import java.lang.reflect.Method;
import java.util.EventObject;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * <code>EventRouter</code> class implementing the inheritable event listening
 * model. For more information on the event model see the
 * {@link com.itmill.toolkit.event package documentation}.
 * 
 * @author IT Mill Ltd.
 * @version
 * @VERSION@
 * @since 3.0
 */
@SuppressWarnings("serial")
public class EventRouter implements MethodEventSource {

    /**
     * List of registered listeners.
     */
    private Set listenerList = null;

    /*
     * Registers a new listener with the specified activation method to listen
     * events generated by this component. Don't add a JavaDoc comment here, we
     * use the default documentation from implemented interface.
     */
    public void addListener(Class eventType, Object object, Method method) {
        if (listenerList == null) {
            listenerList = new LinkedHashSet();
        }
        listenerList.add(new ListenerMethod(eventType, object, method));
    }

    /*
     * Registers a new listener with the specified named activation method to
     * listen events generated by this component. Don't add a JavaDoc comment
     * here, we use the default documentation from implemented interface.
     */
    public void addListener(Class eventType, Object object, String methodName) {
        if (listenerList == null) {
            listenerList = new LinkedHashSet();
        }
        listenerList.add(new ListenerMethod(eventType, object, methodName));
    }

    /*
     * Removes all registered listeners matching the given parameters. Don't add
     * a JavaDoc comment here, we use the default documentation from implemented
     * interface.
     */
    public void removeListener(Class eventType, Object target) {
        if (listenerList != null) {
            final Iterator i = listenerList.iterator();
            while (i.hasNext()) {
                final ListenerMethod lm = (ListenerMethod) i.next();
                if (lm.matches(eventType, target)) {
                    i.remove();
                    return;
                }
            }
        }
    }

    /*
     * Removes the event listener methods matching the given given paramaters.
     * Don't add a JavaDoc comment here, we use the default documentation from
     * implemented interface.
     */
    public void removeListener(Class eventType, Object target, Method method) {
        if (listenerList != null) {
            final Iterator i = listenerList.iterator();
            while (i.hasNext()) {
                final ListenerMethod lm = (ListenerMethod) i.next();
                if (lm.matches(eventType, target, method)) {
                    i.remove();
                    return;
                }
            }
        }
    }

    /*
     * Removes the event listener method matching the given given parameters.
     * Don't add a JavaDoc comment here, we use the default documentation from
     * implemented interface.
     */
    public void removeListener(Class eventType, Object target, String methodName) {

        // Find the correct method
        final Method[] methods = target.getClass().getMethods();
        Method method = null;
        for (int i = 0; i < methods.length; i++) {
            if (methods[i].getName().equals(methodName)) {
                method = methods[i];
            }
        }
        if (method == null) {
            throw new IllegalArgumentException();
        }

        // Remove the listeners
        if (listenerList != null) {
            final Iterator i = listenerList.iterator();
            while (i.hasNext()) {
                final ListenerMethod lm = (ListenerMethod) i.next();
                if (lm.matches(eventType, target, method)) {
                    i.remove();
                    return;
                }
            }
        }

    }

    /**
     * Removes all listeners from event router.
     */
    public void removeAllListeners() {
        listenerList = null;
    }

    /**
     * Sends an event to all registered listeners. The listeners will decide if
     * the activation method should be called or not.
     * 
     * @param event
     *            the Event to be sent to all listeners.
     */
    public void fireEvent(EventObject event) {
        // It is not necessary to send any events if there are no listeners
        if (listenerList != null) {
            // Send the event to all listeners. The listeners themselves
            // will filter out unwanted events.

            final Iterator i = listenerList.iterator();
            while (i.hasNext()) {
                ((ListenerMethod) i.next()).receiveEvent(event);
            }
        }
    }
}
