/*
 * Copyright 2011 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.temenos.interaction.core.web;

/*
 * #%L
 * interaction-core
 * %%
 * Copyright (C) 2012 - 2013 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Mattias Hellborg Arthursson
 * @author Kalle Stenflo
 */
public final class RequestContext {

    public static final String HATEOAS_OPTIONS_HEADER = "x-jax-rs-hateoas-options";
    
    private final static ThreadLocal<RequestContext> currentContext = new ThreadLocal<RequestContext>();

    public static void setRequestContext(RequestContext context) {
        currentContext.set(context);
    }

    public static RequestContext getRequestContext() {
        return currentContext.get();
    }

    public static void clearRequestContext() {
        currentContext.remove();
    }


    private final String basePath;
    private final String requestUri;
    private final String verbosityHeader;
    private final Principal userPrincipal;
    private final Map<String, List<String>> headers = new HashMap<>();
    private final long requestTime;

    public RequestContext(String basePath, String requestUri, String verbosityHeader) {
        this.basePath = basePath;
        this.requestUri = requestUri;
        this.verbosityHeader = verbosityHeader;
        this.userPrincipal = null;
        this.requestTime = System.currentTimeMillis();
    }

    public RequestContext(String basePath, String requestUri, String verbosityHeader, Principal userPrincipal) {
        this.basePath = basePath;
        this.requestUri = requestUri;
        this.verbosityHeader = verbosityHeader;
        this.userPrincipal = userPrincipal;
        this.requestTime = System.currentTimeMillis();
    }

    public RequestContext(String basePath, String requestUri, String verbosityHeader, Map<String, List<String>> headers) {
        this.basePath = basePath;
        this.requestUri = requestUri;
        this.verbosityHeader = verbosityHeader;
        this.userPrincipal = null;
        this.headers.putAll(headers);
        this.requestTime = System.currentTimeMillis();
    }
    
    public RequestContext(String basePath, String requestUri, String verbosityHeader, Principal userPrincipal, Map<String, List<String>> headers) {
        this.basePath = basePath;
        this.requestUri = requestUri;
        this.verbosityHeader = verbosityHeader;
        this.userPrincipal = userPrincipal;
        this.headers.putAll(headers);
        this.requestTime = System.currentTimeMillis();
    }

    /**
     * Construct an object using Builder
     * @param builder
     */
    private RequestContext(Builder builder){
        this.basePath = builder._basePath;
        this.requestUri = builder._requestUri;
        this.verbosityHeader = builder._verbosityHeader;
        this.userPrincipal = builder._userPrincipal;
        this.headers.putAll(builder._headers);
        this.requestTime = builder._requestTime;
    }
    
    /**
     * RequestContext builder 
     *
     * @author sjunejo
     * @author clopes
     *
     */
    public static class Builder {
        private String _basePath, _requestUri, _verbosityHeader;
        private Principal _userPrincipal;
        private Map<String, List<String>> _headers;
        private long _requestTime;

        public Builder setBasePath(String basePath) {
            _basePath = basePath;
            return this;
        }

        public Builder setRequestUri(String requestUri) {
            _requestUri = requestUri;
            return this;
        }

        public Builder setVerbosityHeader(String verbosityHeader) {
            _verbosityHeader = verbosityHeader;
            return this;
        }

        public Builder setUserPrincipal(Principal userPrincipal) {
            _userPrincipal = userPrincipal;
            return this;
        }
        
        public Builder setHeaders(Map<String, List<String>> headers) {
            _headers = headers;
            return this;
        }

        public Builder setRequestTime(long requestTime) {
            _requestTime = requestTime;
            return this;
        }
        public RequestContext build() {
            return new RequestContext(this);
        }
    }

    public String getBasePath() {
        return basePath;
    }

    public String getRequestUri() {
        return requestUri;
    }

    public String getVerbosityHeader() {
        return verbosityHeader;
    }
    
    public Principal getUserPrincipal(){
    	return this.userPrincipal;
    }

    /**
     * Returns header entries for the specified header name
     * using case sensitive search.
     *
     * @param headerName header name to search
     * @return first header entry
     */
    public List<String> getHeaders(String headerName) {
        if (headers.containsKey(headerName)) {
            return Collections.unmodifiableList(headers.get(headerName));
        } else {
            return Collections.emptyList();
        }
    }

    /**
     * Returns header entries for the specified header name
     * ignoring case, that is, it does a case insensitive search.
     *
     * @param headerName header name to search
     * @return first header entry
     */
    public List<String> getHeadersCaseInsensitive(String headerName) {
        for (String key : headers.keySet()) {
            if (key.equalsIgnoreCase(headerName)) {
                return Collections.unmodifiableList(headers.get(key));
            }
        }
        return Collections.emptyList();
    }

    /**
     * Returns the first header entry for the specified header name
     * using case sensitive search.
     *
     * @param headerName header name to search
     * @return first header entry
     */
    public String getFirstHeader(String headerName) {
        List<String> headerValues = getHeaders(headerName);
        return headerValues.isEmpty() ? null : headerValues.get(0);
    }

    /**
     * Returns the first header entry for the specified header name
     * ignoring case, that is, it does a case insensitive search.
     *
     * @param headerName header name to search
     * @return first header entry
     */
    public String getFirstHeaderCaseInsensitive(String headerName) {
        List<String> headerValues = getHeadersCaseInsensitive(headerName);
        return headerValues.isEmpty() ? null : headerValues.get(0);
    }

    /**
     * @return the requestTime
     */
    public long getRequestTime() {
        return requestTime;
    }

}
