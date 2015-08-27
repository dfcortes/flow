/*
 * Copyright 2000-2014 Vaadin Ltd.
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

package com.vaadin.server.communication;

import java.io.IOException;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.vaadin.annotations.Bower;
import com.vaadin.annotations.HTML;
import com.vaadin.annotations.JavaScript;
import com.vaadin.annotations.StyleSheet;
import com.vaadin.hummingbird.kernel.Element;
import com.vaadin.hummingbird.kernel.JsonConverter;
import com.vaadin.hummingbird.kernel.RootNode.PendingRpc;
import com.vaadin.server.ClientConnector;
import com.vaadin.server.Constants;
import com.vaadin.server.LegacyCommunicationManager;
import com.vaadin.server.LegacyCommunicationManager.ClientCache;
import com.vaadin.server.VaadinService;
import com.vaadin.server.VaadinSession;
import com.vaadin.shared.ApplicationConstants;
import com.vaadin.ui.Component;
import com.vaadin.ui.ConnectorTracker;
import com.vaadin.ui.UI;

import elemental.json.Json;
import elemental.json.JsonArray;
import elemental.json.JsonObject;
import elemental.json.JsonValue;

/**
 * Serializes pending server-side changes to UI state to JSON. This includes
 * shared state, client RPC invocations, connector hierarchy changes, connector
 * type information among others.
 *
 * @author Vaadin Ltd
 * @since 7.1
 */
public class UidlWriter implements Serializable {

    private static final String DEPENDENCY_JAVASCRIPT = "scriptDependencies";
    private static final String DEPENDENCY_STYLESHEET = "stylesheetDependencies";
    private static final String DEPENDENCY_HTML = "htmlDependencies";
    private final Set<Class<? extends ClientConnector>> usedClientConnectors = new HashSet<Class<? extends ClientConnector>>();

    /**
     * Writes a JSON object containing all pending changes to the given UI.
     *
     * @param ui
     *            The {@link UI} whose changes to write
     * @param writer
     *            The writer to use
     * @param analyzeLayouts
     *            Whether detected layout problems should be logged.
     * @param async
     *            True if this message is sent by the server asynchronously,
     *            false if it is a response to a client message.
     *
     * @throws IOException
     *             If the writing fails.
     */
    public void write(UI ui, Writer writer, boolean async) throws IOException {
        VaadinSession session = ui.getSession();
        VaadinService service = session.getService();

        // Purge pending access calls as they might produce additional changes
        // to write out
        service.runPendingAccessTasks(session);

        Set<ClientConnector> processedConnectors = new HashSet<ClientConnector>();

        LegacyCommunicationManager manager = session.getCommunicationManager();
        ClientCache clientCache = manager.getClientCache(ui);
        boolean repaintAll = clientCache.isEmpty();
        // Paints components
        ConnectorTracker uiConnectorTracker = ui.getConnectorTracker();
        getLogger().log(Level.FINE, "* Creating response to client");

        while (true) {
            ArrayList<ClientConnector> connectorsToProcess = new ArrayList<ClientConnector>();
            for (ClientConnector c : uiConnectorTracker.getDirtyConnectors()) {
                if (!processedConnectors.contains(c)
                        && LegacyCommunicationManager
                                .isConnectorVisibleToClient(c)) {
                    connectorsToProcess.add(c);
                }
            }

            if (connectorsToProcess.isEmpty()) {
                break;
            }

            for (ClientConnector connector : connectorsToProcess) {
                boolean initialized = uiConnectorTracker
                        .isClientSideInitialized(connector);
                processedConnectors.add(connector);

                try {
                    connector.beforeClientResponse(!initialized);
                } catch (RuntimeException e) {
                    manager.handleConnectorRelatedException(connector, e);
                }
            }
        }

        getLogger().log(Level.FINE, "Found " + processedConnectors.size()
                + " dirty connectors to paint");

        uiConnectorTracker.setWritingResponse(true);
        try {
            JsonObject response = Json.createObject();

            int syncId = service.getDeploymentConfiguration()
                    .isSyncIdCheckEnabled()
                            ? uiConnectorTracker.getCurrentSyncId() : -1;
            // writer.write("\"" + ApplicationConstants.SERVER_SYNC_ID + "\": "
            // + syncId + ", ");
            response.put(ApplicationConstants.SERVER_SYNC_ID, syncId);
            if (repaintAll) {
                response.put(ApplicationConstants.RESYNCHRONIZE_ID, true);
                // writer.write("\"" + ApplicationConstants.RESYNCHRONIZE_ID +
                // "\": true, ");
            }
            int nextClientToServerMessageId = ui
                    .getLastProcessedClientToServerId() + 1;
            response.put(ApplicationConstants.CLIENT_TO_SERVER_ID,
                    nextClientToServerMessageId);
            // writer.write("\"" + ApplicationConstants.CLIENT_TO_SERVER_ID +
            // "\": " + nextClientToServerMessageId);

            Collection<ClientConnector> dirtyVisibleConnectors = ui
                    .getConnectorTracker().getDirtyVisibleConnectors();
            JsonObject dependencies = Json.createObject();
            List<Class<? extends ClientConnector>> dependencyClasses = new ArrayList<>();
            for (ClientConnector c : dirtyVisibleConnectors) {
                Class<? extends ClientConnector> cls = c.getClass();
                if (!ui.getResourcesHandled().contains(cls)) {
                    dependencyClasses.add(cls);
                }
            }

            // /*
            // * Ensure super classes come before sub classes to get script
            // * dependency order right. Sub class @JavaScript might assume that
            // *
            // * @JavaScript defined by super class is already loaded.
            // */
            Collections.sort(dependencyClasses, new Comparator<Class<?>>() {
                @Override
                public int compare(Class<?> o1, Class<?> o2) {
                    // TODO optimize using Class.isAssignableFrom?
                    return hierarchyDepth(o1) - hierarchyDepth(o2);
                }

                private int hierarchyDepth(Class<?> type) {
                    if (type == Object.class) {
                        return 0;
                    } else {
                        return hierarchyDepth(type.getSuperclass()) + 1;
                    }
                }
            });
            for (Class<? extends ClientConnector> cls : dependencyClasses) {
                handleDependencies(ui, cls, response);
            }

            encodeChanges(ui, response);

            encodeRpc(ui, response);

            String r = response.toString();
            writer.write(r.substring(1, r.length() - 1));
        } finally {
            uiConnectorTracker.setWritingResponse(false);
            uiConnectorTracker.cleanConnectorMap();
        }
    }

    public static void encodeRpc(UI ui, JsonObject response) {
        List<PendingRpc> rpcQueue = ui.getRootNode().flushRpcQueue();
        if (!rpcQueue.isEmpty()) {
            response.put("rpc", encodeRpcQueue(rpcQueue));
        }
    }

    private static void encodeChanges(UI ui, JsonObject response) {
        ChangeUidlBuilder uidlBuilder = new ChangeUidlBuilder(ui);
        ui.getRootNode().commit(uidlBuilder);
        response.put("elementTemplates", uidlBuilder.getNewTemplates());
        response.put("elementChanges", uidlBuilder.getChanges());
    }

    private static JsonArray encodeRpcQueue(List<PendingRpc> rpcQueue) {
        JsonArray array = Json.createArray();

        for (PendingRpc pendingRpc : rpcQueue) {
            JsonArray rpc = Json.createArray();
            rpc.set(0, pendingRpc.getJavascript());

            Object[] params = pendingRpc.getParams();
            for (int i = 0; i < params.length; i++) {
                Object param = params[i];

                rpc.set(i + 1, serializeRpcParam(param));
            }

            array.set(array.length(), rpc);
        }

        return array;
    }

    private static JsonValue serializeRpcParam(Object param) {
        if (param instanceof Element) {
            Element element = (Element) param;

            JsonObject object = Json.createObject();
            object.put("node", element.getNode().getId());
            object.put("template", element.getTemplate().getId());

            return object;
        } else {
            return JsonConverter.toJson(param);
        }
    }

    /**
     * @since
     * @param cls
     * @param response
     */
    private void handleDependencies(UI ui, Class<? extends ClientConnector> cls,
            JsonObject response) {
        if (ui.getResourcesHandled().contains(cls)) {
            return;
        }

        ui.getResourcesHandled().add(cls);
        LegacyCommunicationManager manager = ui.getSession()
                .getCommunicationManager();

        JavaScript jsAnnotation = cls.getAnnotation(JavaScript.class);
        if (jsAnnotation != null) {
            if (!response.hasKey(DEPENDENCY_JAVASCRIPT)) {
                response.put(DEPENDENCY_JAVASCRIPT, Json.createArray());
            }
            JsonArray scriptsJson = response.getArray(DEPENDENCY_JAVASCRIPT);

            for (String uri : jsAnnotation.value()) {
                scriptsJson.set(scriptsJson.length(),
                        manager.registerDependency(uri, cls));
            }
        }

        StyleSheet styleAnnotation = cls.getAnnotation(StyleSheet.class);
        if (styleAnnotation != null) {
            if (!response.hasKey(DEPENDENCY_STYLESHEET)) {
                response.put(DEPENDENCY_STYLESHEET, Json.createArray());
            }
            JsonArray stylesJson = response.getArray(DEPENDENCY_STYLESHEET);

            for (String uri : styleAnnotation.value()) {
                stylesJson.set(stylesJson.length(),
                        manager.registerDependency(uri, cls));
            }
        }

        List<String> htmlResources = getHtmlResources(cls);
        if (!htmlResources.isEmpty()) {
            if (!response.hasKey(DEPENDENCY_HTML)) {
                response.put(DEPENDENCY_HTML, Json.createArray());
            }
            JsonArray htmlJson = response.getArray(DEPENDENCY_HTML);

            for (String uri : htmlResources) {
                htmlJson.set(htmlJson.length(),
                        manager.registerDependency(uri, cls));
            }
        }

        if (Component.class.isAssignableFrom(cls.getSuperclass())) {
            handleDependencies(ui,
                    (Class<? extends ClientConnector>) cls.getSuperclass(),
                    response);
        }
    }

    public static List<String> getHtmlResources(
            Class<? extends ClientConnector> cls) {

        List<String> resources = new ArrayList<>();

        HTML htmlAnnotation = cls.getAnnotation(HTML.class);
        Bower bowerAnnotation = cls.getAnnotation(Bower.class);

        if (htmlAnnotation != null) {
            for (String uri : htmlAnnotation.value()) {
                resources.add(uri);
            }
        }
        if (bowerAnnotation != null) {
            for (String bowerComponent : bowerAnnotation.value()) {
                String uri = Constants.BOWER_RESOURCE.replace("{0}",
                        bowerComponent);
                resources.add(uri);
            }
        }
        return resources;
    }

    private JsonArray toJsonArray(List<String> list) {
        JsonArray result = Json.createArray();
        for (int i = 0; i < list.size(); i++) {
            result.set(i, list.get(i));
        }

        return result;
    }

    /**
     * Adds the performance timing data (used by TestBench 3) to the UIDL
     * response.
     *
     * @throws IOException
     */
    private void writePerformanceData(UI ui, Writer writer) throws IOException {
        writer.write(String.format(", \"timings\":[%d, %d]",
                ui.getSession().getCumulativeRequestDuration(),
                ui.getSession().getLastRequestDuration()));
    }

    @SuppressWarnings("unchecked")
    public String getTag(ClientConnector clientConnector,
            LegacyCommunicationManager manager) {
        Class<? extends ClientConnector> clientConnectorClass = clientConnector
                .getClass();
        while (clientConnectorClass.isAnonymousClass()) {
            clientConnectorClass = (Class<? extends ClientConnector>) clientConnectorClass
                    .getSuperclass();
        }
        Class<?> clazz = clientConnectorClass;
        while (!usedClientConnectors.contains(clazz)
                && clazz.getSuperclass() != null
                && ClientConnector.class.isAssignableFrom(clazz)) {
            usedClientConnectors.add((Class<? extends ClientConnector>) clazz);
            clazz = clazz.getSuperclass();
        }
        return manager.getTagForType(clientConnectorClass);
    }

    public Collection<Class<? extends ClientConnector>> getUsedClientConnectors() {
        return usedClientConnectors;
    }

    private static final Logger getLogger() {
        return Logger.getLogger(UidlWriter.class.getName());
    }
}