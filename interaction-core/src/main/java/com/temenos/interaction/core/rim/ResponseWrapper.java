package com.temenos.interaction.core.rim;

/*
 * #%L
 * interaction-core
 * %%
 * Copyright (C) 2012 - 2016 Temenos Holdings N.V.
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

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.resource.RESTResource;

/**
 * Wraps an internal IRIS Response object with a link to the resolved location
 * and any request parameters resolved in this process.
 *
 * @author dgroves
 *
 */
public final class ResponseWrapper {
    private final Response response;
    private final RESTResource resource;
    private final Link selfLink;
    private final MultivaluedMap<String, String> requestParameters;
    private final ResourceState resolvedState;
    
    public ResponseWrapper(Response response, Link selfLink, 
            MultivaluedMap<String, String> requestParameters, ResourceState resolvedState) {
        this.response = response;
        this.resource = null;
        this.selfLink = selfLink;
        this.requestParameters = requestParameters;
        this.resolvedState = resolvedState;
    }
    
    public ResponseWrapper(Response response, RESTResource resource, Link selfLink, 
            MultivaluedMap<String, String> requestParameters, ResourceState resolvedState) {
        this.response = response;
        this.resource = resource;
        this.selfLink = selfLink;
        this.requestParameters = requestParameters;
        this.resolvedState = resolvedState;
    }
    
    /**
     * Obtain the response object stored in this wrapper.
     * @return The response object associated with this wrapper.
     */
    public Response getResponse(){
        return response;
    }
    
    /**
     * Obtain a link to the fully resolved resource from this wrapper.
     * @return The Link object associated with this wrapper.
     */
    public Link getSelfLink(){
        return selfLink;
    }
    
    /**
     * Obtain query parameters resolved during internal request processing. 
     * @return A MultivaluedMap object with query parameters associated
     * to this internal request. 
     */
    public MultivaluedMap<String, String> getRequestParameters(){
        return requestParameters;
    }
    
    /**
     * Obtain the ResourceState that was resolved during the request.
     * @return The ResourceState object associated with this wrapper.
     */
    public ResourceState getResolvedState(){
        return resolvedState;
    }

    /**
     * @return the resource
     */
    public RESTResource getResource() {
        return resource;
    }
}
