package uk.co.bbc.iplayer.common.model.impl;

import org.codehaus.jackson.annotate.JsonIgnoreType;
import uk.co.bbc.iplayer.common.model.KeyValuePairMap;

import javax.xml.bind.annotation.XmlTransient;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * <p>
 * This class provides a default implementation of the KeyValuePairMap interface
 * by extending the java.util.LinkedHashMap class.
 * </p>
 * <p>
 * Also:
 * <ul>
 * <li>key is a String</li>
 * <li>value is a Serializable object</li>
 * </ul>
 * </p>
 * <p>
 * And for each map entry:
 * <ul>
 * <li>key is used as the key</li>
 * <li>value is used as the value</li>
 * </ul>
 * </p>
 * <p>
 * Finally, all the elements of the Map are ordered by the order they were added to the Map.
 * </p>
 *
 * @author <a href="mailto:spsarras@cyantific.net">Stelios Psarras</a>
 * @see uk.co.bbc.iplayer.common.model.KeyValuePairMap
 * @see java.util.LinkedHashMap
 */
@XmlTransient
@JsonIgnoreType
public class OrderedStringSerializableKeyValuePairMapImpl<V extends Serializable>
        extends LinkedHashMap<String, V> implements KeyValuePairMap<String, V> {

    private static final long serialVersionUID = 1L;

    /**
     * <p>
     * Default no parameters constructor that creates an empty instance of
     * OrderedStringSerializableKeyValuePairMapImpl.
     * </p>
     */
    public OrderedStringSerializableKeyValuePairMapImpl() {
        super();
    }

    /**
     * <p>
     * Simple one argument constructor that creates an instance of
     * OrderedStringSerializableKeyValuePairMapImpl with the elements provided.
     * </p>
     *
     * @param map a Map containing the elements to be used
     */
    public OrderedStringSerializableKeyValuePairMapImpl(Map<String, V> map) {
        super(map);
    }
}
