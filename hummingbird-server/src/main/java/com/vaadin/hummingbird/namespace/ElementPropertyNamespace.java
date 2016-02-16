/*
 * Copyright 2000-2016 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.hummingbird.namespace;

import java.io.Serializable;
import java.util.Set;

import com.vaadin.hummingbird.StateNode;

import elemental.json.JsonValue;

/**
 * Namespace for element property values.
 *
 * @since
 * @author Vaadin Ltd
 */
public class ElementPropertyNamespace extends MapNamespace {

    /**
     * Creates a new element property namespace for the given node.
     *
     * @param node
     *            the node that the namespace belongs to
     */
    public ElementPropertyNamespace(StateNode node) {
        super(node);
    }

    /**
     * Sets a property to the given value.
     *
     * @param name
     *            the property name
     * @param value
     *            the value, must be a string, a boolean, a double or
     *            <code>null</code>
     */
    public void setProperty(String name, Serializable value) {
        assert value == null || value instanceof String
                || value instanceof Boolean || value instanceof Double;
        assert name != null;
        assert !"textContent".equals(name);

        put(name, value);
    }

    /**
     * Sets a property to the given JSON value.
     *
     * @param name
     *            the property name
     * @param value
     *            the JSON value
     */
    public void setJsonProperty(String name, JsonValue value) {
        putJson(name, value);
    }

    /**
     * Checks whether there is a property of the given name.
     *
     * @param name
     *            the name of the property
     * @return <code>true</code> if there is a propety with the given name;
     *         <code>false</code> if there is no property
     */
    public boolean hasProperty(String name) {
        return contains(name);
    }

    /**
     * Removes the given property.
     *
     * @param name
     *            the name of the property to remove
     */
    @Override
    public void remove(String name) {
        super.remove(name);
    }

    /**
     * Gets the value of the given property.
     *
     * @param name
     *            the name of the property
     * @return the property value; <code>null</code> if there is no property or
     *         if the value is explicitly set to null
     */
    public Object getProperty(String name) {
        return get(name);
    }

    /**
     * Gets the property names.
     *
     * @return a set containing all the property names that have been set
     */
    public Set<String> getPropertyNames() {
        return keySet();
    }
}