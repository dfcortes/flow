/*
 * Copyright 2000-2017 Vaadin Ltd.
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
package com.vaadin.server.communication.rpc;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.annotations.ClientDelegate;
import com.vaadin.annotations.EventHandler;
import com.vaadin.flow.JsonCodec;
import com.vaadin.flow.StateNode;
import com.vaadin.flow.nodefeature.ComponentMapping;
import com.vaadin.flow.template.PolymerTemplate;
import com.vaadin.flow.template.model.ModelType;
import com.vaadin.shared.JsonConstants;
import com.vaadin.ui.Component;
import com.vaadin.util.ReflectTools;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import elemental.json.JsonType;
import elemental.json.JsonValue;

/**
 * RPC handler for events triggered through <code>element.$server</code> or
 * simply <code>$server</code> in template event handlers.
 *
 * @see JsonConstants#RPC_PUBLISHED_SERVER_EVENT_HANDLER
 *
 * @author Vaadin Ltd
 *
 */
public class PublishedServerEventHandlerRpcHandler
        extends AbstractRpcInvocationHandler {

    @Override
    public String getRpcType() {
        return JsonConstants.RPC_PUBLISHED_SERVER_EVENT_HANDLER;
    }

    @Override
    public void handleNode(StateNode node, JsonObject invocationJson) {
        assert invocationJson
                .hasKey(JsonConstants.RPC_TEMPLATE_EVENT_METHOD_NAME);
        String methodName = invocationJson
                .getString(JsonConstants.RPC_TEMPLATE_EVENT_METHOD_NAME);
        if (methodName == null) {
            throw new IllegalArgumentException(
                    "Event handler method name may not be null");
        }
        JsonValue args = invocationJson
                .get(JsonConstants.RPC_TEMPLATE_EVENT_ARGS);
        if (args == null) {
            args = Json.createArray();
        }
        if (args.getType() != JsonType.ARRAY) {
            throw new IllegalArgumentException(
                    "Incorrect type for method arguments: " + args.getClass());
        }
        assert node.hasFeature(ComponentMapping.class);
        Optional<Component> component = node.getFeature(ComponentMapping.class)
                .getComponent();
        if (!component.isPresent()) {
            throw new IllegalStateException(
                    "Unable to handle RPC template event JSON message: "
                            + "there is no component available for the target node");
        }

        invokeMethod(component.get(), component.get().getClass(), methodName,
                (JsonArray) args);

    }

    static void invokeMethod(Component instance, Class<?> clazz,
            String methodName, JsonArray args) {
        assert instance != null;
        List<Method> methods = Stream.of(clazz.getDeclaredMethods())
                .filter(method -> methodName.equals(method.getName()))
                .filter(method -> method.isAnnotationPresent(EventHandler.class)
                        || method.isAnnotationPresent(ClientDelegate.class))
                .collect(Collectors.toList());
        if (methods.size() > 1) {
            String msg = String.format(
                    "Class '%s' contains "
                            + "several event handler method with the same name '%s'",
                    instance.getClass().getName(), methodName);
            throw new IllegalStateException(msg);
        } else if (methods.size() == 1) {
            invokeMethod(instance, methods.get(0), args);
        } else if (!Component.class.equals(clazz)) {
            invokeMethod(instance, clazz.getSuperclass(), methodName, args);
        } else {
            String msg = String.format(
                    "Neither class '%s' "
                            + "nor its super classes declare event handler method '%s'",
                    instance.getClass().getName(), methodName);
            throw new IllegalStateException(msg);
        }
    }

    private static void invokeMethod(Component instance, Method method,
            JsonArray args) {
        try {
            method.setAccessible(true);
            method.invoke(instance, decodeArgs(instance, method, args));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            Logger.getLogger(
                    PublishedServerEventHandlerRpcHandler.class.getName())
                    .log(Level.FINE, null, e);
            throw new RuntimeException(e.getCause());
        }
    }

    private static Object[] decodeArgs(Component instance, Method method,
            JsonArray argsFromClient) {
        int methodArgs = method.getParameterCount();
        int clientValuesCount = argsFromClient.length();
        JsonArray argValues;
        if (method.isVarArgs()) {
            if (clientValuesCount >= methodArgs - 1) {
                argValues = unwrapVarArgs(argsFromClient, method);
            } else {
                String msg = String.format(
                        "The number of received values (%d) is not enough "
                                + "to call the method '%s' declared in '%s' which "
                                + "has vararg parameter and the number of arguments %d",
                        argsFromClient.length(), method.getName(),
                        method.getDeclaringClass().getName(),
                        method.getParameterCount());
                throw new IllegalArgumentException(msg);
            }
        } else {
            if (methodArgs == clientValuesCount) {
                argValues = argsFromClient;
            } else {
                String msg = String.format(
                        "The number of received values (%d) is not equal "
                                + "to the number of arguments (%d) in the method '%s' "
                                + "declared in '%s'",
                        argsFromClient.length(), method.getParameterCount(),
                        method.getName(), method.getDeclaringClass().getName());
                throw new IllegalArgumentException(msg);
            }
        }
        List<Object> decoded = new ArrayList<>(method.getParameterCount());
        Class<?>[] methodParameterTypes = method.getParameterTypes();
        for (int i = 0; i < argValues.length(); i++) {
            Class<?> type = methodParameterTypes[i];
            decoded.add(decodeArg(instance, method, type, i, argValues.get(i)));
        }
        return decoded.toArray(new Object[method.getParameterCount()]);
    }

    private static JsonArray unwrapVarArgs(JsonArray argsFromClient,
            Method method) {
        int paramCount = method.getParameterCount();
        if (argsFromClient.length() == paramCount) {
            if (argsFromClient.get(paramCount - 1).getType()
                    .equals(JsonType.ARRAY)) {
                return argsFromClient;
            }
        }
        JsonArray result = Json.createArray();
        JsonArray rest = Json.createArray();
        int newIndex = 0;
        for (int i = 0; i < argsFromClient.length(); i++) {
            JsonValue value = argsFromClient.get(i);
            if (i < paramCount - 1) {
                result.set(i, value);
            } else {
                rest.set(newIndex, value);
                newIndex++;
            }
        }
        result.set(paramCount - 1, rest);
        return result;
    }

    private static Object decodeArg(Component instance, Method method,
            Class<?> type, int index, JsonValue argValue) {
        // come up with method to know that it's an id and should be gotten from
        // the model
        assert argValue != null;
        if (type.isPrimitive() && argValue.getType() == JsonType.NULL) {
            String msg = String.format(
                    "Null values are not allowed for primitive types but "
                            + "a 'null' value was received for parameter %d "
                            + "which refers to primitive type '%s' "
                            + "in the method '%s' defined in the class '%s'",
                    index, type.getName(), method.getName(),
                    method.getDeclaringClass().getName());
            throw new IllegalArgumentException(msg);
        } else if (type.isArray()) {
            return decodeArray(method, type, index, argValue);
        } else {
            Class<?> convertedType = ReflectTools.convertPrimitiveType(type);

            if (isTemplateModelValue(instance, argValue, convertedType)) {
                return getTemplateItem((PolymerTemplate<?>) instance,
                        (JsonObject) argValue,
                        method.getGenericParameterTypes()[index]);
            }

            if (!JsonCodec.canEncodeWithoutTypeInfo(convertedType)) {
                String msg = String.format(
                        "Class '%s' has the method '%s' "
                                + "whose parameter %d refers to unsupported type '%s'",
                        method.getDeclaringClass().getName(), method.getName(),
                        index, type.getName());
                throw new IllegalArgumentException(msg);
            }
            return JsonCodec.decodeAs(argValue, convertedType);
        }
    }

    private static boolean isTemplateModelValue(Component instance,
            JsonValue argValue, Class<?> convertedType) {
        return instance instanceof PolymerTemplate
                && argValue instanceof JsonObject
                && ((PolymerTemplate<?>) instance)
                        .isSupportedClass(convertedType)
                && ((JsonObject) argValue).hasKey("nodeId");
    }

    private static Object getTemplateItem(PolymerTemplate<?> template,
            JsonObject argValue, Type convertedType) {
        StateNode node = template.getUI().get().getInternals().getStateTree()
                .getNodeById((int) argValue.getNumber("nodeId"));

        ModelType propertyType = template.getModelType(convertedType);

        return propertyType.modelToApplication(node);
    }

    private static Object decodeArray(Method method, Class<?> type, int index,
            JsonValue argValue) {
        if (argValue.getType() != JsonType.ARRAY) {
            String msg = String.format(
                    "Class '%s' has the method '%s' "
                            + "whose parameter %d refers to the array type '%s' "
                            + "but received value is not an array, its type is '%s'",
                    method.getDeclaringClass().getName(), method.getName(),
                    index, type.getName(), argValue.getType().name());
            throw new IllegalArgumentException(msg);
        }
        Class<?> componentType = type.getComponentType();
        JsonArray array = (JsonArray) argValue;
        Object result = Array.newInstance(componentType, array.length());
        for (int i = 0; i < array.length(); i++) {
            Array.set(result, i, decodeArg(null, method, componentType, index,
                    array.get(i)));
        }
        return result;
    }

}
