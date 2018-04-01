package de.energiequant.vatplanner.commons.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.http.Header;
import org.apache.http.HttpEntity;

/**
 * Holds headers indexed by case-insensitive keys. This applies for example to
 * HTTP headers. All keys are converted to lower-case for indexing.
 * <p>
 * Headers of same name can appear multiple times.
 * Such values are being returned in order of appearance.
 * </p>
 */
public class CaseInsensitiveHeaders {
    private final HashMap<String, List<String>> map = new HashMap<>();

    /**
     * Adds all {@link Header}s as available from {@link HttpEntity}.
     * Adding headers is not thread-safe and should be avoided after providing
     * the container to outside.
     * @param headers headers to be indexed
     */
    void addAll(Header[] headers) {
        if (headers == null) {
            return;
        }
        
        for (Header header : headers) {
            add(header.getName(), header.getValue());
        }
    }
    
    /**
     * Adds the given value to be indexed by the specified name.
     * Adding headers is not thread-safe and should be avoided after providing
     * the container to outside.
     * @param name name will be converted to lower case
     * @param value value to be added
     */
    void add(String name, String value) {
        name = name.toLowerCase();
        
        List<String> list = map.get(name);
        if (list == null) {
            list = new ArrayList<>();
            map.put(name, list);
        }
        
        list.add(value);
    }
    
    /**
     * Returns all header values indexed by lower case header name.
     * @return header values indexed by lower case header name
     */
    public Map<String, List<String>> getAll() {
        HashMap<String, List<String>> copy = new HashMap<>();
        
        for (Entry<String, List<String>> entry : map.entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            
            copy.put(key, Collections.unmodifiableList(values));
        }
        
        return Collections.unmodifiableMap(copy);
    }
    
    /**
     * Returns all header values for the given header name.
     * Values are being returned in order of appearance.
     * @param name header name is case-insensitive
     * @return values indexed by name; empty if no value has been recorded for given name
     */
    public List<String> getAllByName(String name) {
        List<String> list = map.getOrDefault(name.toLowerCase(), new ArrayList<>());
        
        return Collections.unmodifiableList(list);
    }
    
    /**
     * Returns the first header value encountered for the given header name.
     * @param name header name is case-insensitive
     * @return first header value encountered for the given header name
     */
    public String getFirstByName(String name) {
        List<String> values = getAllByName(name);
        
        Iterator<String> it = values.iterator();
        if (it.hasNext()) {
            return it.next();
        } else {
            return null;
        }
    }
}
