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


import java.io.IOException;
import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;

/**
 * @author Mattias Hellborg Arthursson
 * @author Kalle Stenflo
 */
public class RequestContextFilter implements Filter {
	
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {

        long requestTime = System.currentTimeMillis();
        final HttpServletRequest servletRequest = (HttpServletRequest) request;

        String requestURI = servletRequest.getRequestURI();
        requestURI = StringUtils.removeStart(requestURI, servletRequest.getContextPath() + servletRequest.getServletPath());
        String baseURL = StringUtils.removeEnd(servletRequest.getRequestURL().toString(), requestURI);

        Map<String, List<String>> headersMap = new HashMap<>();
        Enumeration<String> headerNames = servletRequest.getHeaderNames();
        if(headerNames != null) {
            while(headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                List<String> valuesList = Collections.list(servletRequest.getHeaders(headerName));
                headersMap.put(headerName, valuesList);
            }
        }

        RequestContext.Builder reqCtxBuilder = new RequestContext.Builder()
                                                .setBasePath(baseURL)
                                                .setRequestUri(servletRequest.getRequestURI())
                                                .setVerbosityHeader(servletRequest.getHeader(RequestContext.HATEOAS_OPTIONS_HEADER))
                                                .setHeaders(headersMap)
                                                .setRequestTime(requestTime);
        Principal userPrincipal = servletRequest.getUserPrincipal();
        if (userPrincipal != null) {
            reqCtxBuilder.setUserPrincipal(userPrincipal);
        }
        RequestContext.setRequestContext(reqCtxBuilder.build());
        
        try {
            chain.doFilter(request, response);
        } finally {
            RequestContext.clearRequestContext();
        }
    }

    @Override
    public void destroy() {

    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }
}
