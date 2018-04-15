/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Licensed to the Apache Software Foundation (ASF) under one
 ~ or more contributor license agreements.  See the NOTICE file
 ~ distributed with this work for additional information
 ~ regarding copyright ownership.  The ASF licenses this file
 ~ to you under the Apache License, Version 2.0 (the
 ~ "License"); you may not use this file except in compliance
 ~ with the License.  You may obtain a copy of the License at
 ~
 ~   http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing,
 ~ software distributed under the License is distributed on an
 ~ "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 ~ KIND, either express or implied.  See the License for the
 ~ specific language governing permissions and limitations
 ~ under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package org.apache.sling.scripting.resolver.internal;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.script.ScriptContext;
import javax.script.ScriptException;
import javax.servlet.GenericServlet;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingConstants;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.request.RequestDispatcherOptions;
import org.apache.sling.api.request.RequestPathInfo;
import org.apache.sling.api.scripting.ScriptEvaluationException;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleWiring;

class BundledScriptServlet extends GenericServlet {

    private final Bundle m_bundle;
    private final BundledScriptFinder m_bundledScriptFinder;
    private final ScriptContextProvider m_scriptContextProvider;

    private Map<String, Script> scriptsMap = new HashMap<>();
    private ReadWriteLock lock = new ReentrantReadWriteLock();

    BundledScriptServlet(BundledScriptFinder bundledScriptFinder, Bundle bundle, ScriptContextProvider scriptContextProvider) {
        m_bundle = bundle;
        m_bundledScriptFinder = bundledScriptFinder;
        m_scriptContextProvider = scriptContextProvider;
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) throws ServletException, IOException {
        if ((req instanceof SlingHttpServletRequest) && (res instanceof SlingHttpServletResponse)) {
            SlingHttpServletRequest request = (SlingHttpServletRequest) req;
            SlingHttpServletResponse response = (SlingHttpServletResponse) res;

            if (request.getAttribute(SlingConstants.ATTR_INCLUDE_SERVLET_PATH) == null) {
                final String contentType = request.getResponseContentType();
                if (contentType != null) {
                    response.setContentType(contentType);
                    if (contentType.startsWith("text/")) {
                        response.setCharacterEncoding("UTF-8");
                    }
                }
            }

            String scriptsMapKey = getScriptsMapKey(request);
            Script script = scriptsMap.get(scriptsMapKey);
            lock.readLock().lock();
            try {
                if (script == null) {
                    lock.readLock().unlock();
                    lock.writeLock().lock();
                    try {
                        script = scriptsMap.get(getScriptsMapKey(request));
                        if (script == null) {
                            script = m_bundledScriptFinder.getScript(request, m_bundle);
                            if (script != null) {
                                scriptsMap.put(scriptsMapKey, script);
                            }
                            lock.readLock().lock();
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                }
            } finally {
                lock.readLock().unlock();
            }
            if (script != null) {
                ScriptContext scriptContext = m_scriptContextProvider.prepareScriptContext(request, response, script);
                try {
                    script.eval(scriptContext);
                } catch (ScriptException se) {
                    Throwable cause = (se.getCause() == null) ? se : se.getCause();
                    throw new ScriptEvaluationException(script.getName(), se.getMessage(), cause);
                }
            } else {
                /*
                 * The Script does not seem to belong to the capability for which this servlet has been registered. If this capability
                 * extends another capability, the request should be dispatched to that capability.
                 *
                 * BIG FAT NOTE:
                 * - we need to figure out how to map to the correct version of a wired resource type
                 */
                String currentResourceType = request.getResource().getResourceType();
                if (currentResourceType.indexOf('/') > 0) {
                    currentResourceType = currentResourceType.substring(0, currentResourceType.lastIndexOf('/'));
                }
                for (BundleCapability bundleCapability : m_bundle.adapt(BundleWiring.class).getCapabilities(BundledScriptTracker
                        .NS_SLING_RESOURCE_TYPE)) {

                    String capabilityRT = (String) bundleCapability.getAttributes().get(BundledScriptTracker
                            .NS_SLING_RESOURCE_TYPE);
                    if (currentResourceType.equals(capabilityRT)) {
                        String extendsRT = (String) bundleCapability.getAttributes().get("extends");
                        if (StringUtils.isNotEmpty(extendsRT)) {
                            RequestDispatcherOptions options = new RequestDispatcherOptions();
                            options.setForceResourceType(extendsRT + "/1.0.0");
                            RequestDispatcher dispatcher = request.getRequestDispatcher(request.getResource(), options);
                            if (dispatcher != null) {
                                dispatcher.include(request, response);
                                return;
                            } else {
                                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                            }
                        }
                    }
                }
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);


            }
        } else {
            throw new ServletException("Not a Sling HTTP request/response");
        }
    }

    private String getScriptsMapKey(SlingHttpServletRequest request) {
        RequestPathInfo requestPathInfo = request.getRequestPathInfo();
        String selectorString = requestPathInfo.getSelectorString();
        String requestExtension = requestPathInfo.getExtension();
        return request.getResource().getResourceType() + (StringUtils.isNotEmpty(selectorString) ? ":" + selectorString : "") +
                (StringUtils.isNotEmpty(requestExtension) ? ":" + requestExtension : "");
    }
}