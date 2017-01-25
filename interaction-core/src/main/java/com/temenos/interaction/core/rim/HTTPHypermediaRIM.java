package com.temenos.interaction.core.rim;

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

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.apache.wink.common.model.multipart.InMultiPart;
import org.apache.wink.common.model.multipart.InPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.cache.Cache;
import com.temenos.interaction.core.command.CommandController;
import com.temenos.interaction.core.command.HttpStatusTypes;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionCommand.Result;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityProperties;
import com.temenos.interaction.core.entity.EntityProperty;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.entity.StreamingInput;
import com.temenos.interaction.core.hypermedia.Action;
import com.temenos.interaction.core.hypermedia.DynamicResourceState;
import com.temenos.interaction.core.hypermedia.Event;
import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.LinkGenerator;
import com.temenos.interaction.core.hypermedia.LinkGeneratorImpl;
import com.temenos.interaction.core.hypermedia.LinkHeader;
import com.temenos.interaction.core.hypermedia.ParameterAndValue;
import com.temenos.interaction.core.hypermedia.ResourceLocatorProvider;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateAndParameters;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.hypermedia.TransitionCommandSpec;
import com.temenos.interaction.core.hypermedia.expression.Expression;
import com.temenos.interaction.core.hypermedia.validation.HypermediaValidator;
import com.temenos.interaction.core.hypermedia.validation.LogicalConfigurationListener;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.RESTResource;

/**
 * <P>
 * Implement HTTP interactions for resources using an hypermedia driven command
 * controller. This model for resource interaction can be used for individual
 * (item) or collection resources who conform to the HTTP generic uniform
 * interface and the Hypermedia As The Engine Of Application State (HATEOAS)
 * constraints. HTTP provides one operation to view the resource (GET), one
 * operation to create a new resource (POST) and two operations to change an
 * individual resources state (PUT and DELETE).
 * </P>
 *
 * @author aphethean
 *
 */
public class HTTPHypermediaRIM implements HTTPResourceInteractionModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(HTTPHypermediaRIM.class);

    private static boolean skipValidation = System.getProperty("iris.skip.validation") != null;

    private final HTTPHypermediaRIM parent;
    private final CommandController commandController;
    private final ResourceStateMachine hypermediaEngine;
    private final ResourceRequestHandler resourceRequestHandler;
    private final Metadata metadata;
    private final String resourcePath;

    /**
     * <p>
     * Create a new resource for HTTP interaction.
     * </p>
     *
     * @param commandController
     *            All commands for all resources.
     * @param hypermediaEngine
     *            All application states, responsible for creating links from
     *            one state to another.
     * @param currentState
     *            The current application state when accessing this resource.
     */
    public HTTPHypermediaRIM(CommandController commandController, ResourceStateMachine hypermediaEngine,
            Metadata metadata) {
        this(null, commandController, hypermediaEngine, metadata, hypermediaEngine.getInitial().getResourcePath(), true);
    }

    /**
     * <p>
     * Create a new resource for HTTP interaction.
     * </p>
     *
     * @param commandController
     *            All commands for all resources.
     * @param hypermediaEngine
     *            All application states, responsible for creating links from
     *            one state to another.
     * @param currentState
     *            The current application state when accessing this resource.
     */
    public HTTPHypermediaRIM(CommandController commandController, ResourceStateMachine hypermediaEngine,
            Metadata metadata, ResourceLocatorProvider resourceLocatorProvider) {
        this(null, commandController, hypermediaEngine, metadata, hypermediaEngine.getInitial().getResourcePath(), true);
    }

    /*
     * Create a child resource. This constructor is used to create resources
     * where there are sub states of the same entity.
     *
     * @param parent This resources parent interaction model.
     *
     * @param commandController All commands for all resources.
     *
     * @param hypermediaEngine All application states, responsible for creating
     * links from one state to another.
     *
     * @param currentState The current application state when accessing this
     * resource.
     */
    protected HTTPHypermediaRIM(HTTPHypermediaRIM parent, CommandController commandController,
            ResourceStateMachine hypermediaEngine, ResourceState currentState, Metadata metadata) {
        this(parent, commandController, hypermediaEngine, metadata, currentState.getResourcePath(), false);
    }

    public HTTPHypermediaRIM(HTTPHypermediaRIM parent, CommandController commandController,
            ResourceStateMachine hypermediaEngine, Metadata metadata, String currentPath, boolean printGraph) {
        this.parent = parent;
        this.resourceRequestHandler = new SequentialResourceRequestHandler();
        this.commandController = commandController;
        this.hypermediaEngine = hypermediaEngine;
        this.metadata = metadata;
        this.resourcePath = currentPath;
        assert (commandController != null);
        assert (hypermediaEngine != null);
        assert (metadata != null);
        assert (resourcePath != null);
        hypermediaEngine.setCommandController(commandController);
        if (!skipValidation) {
            HypermediaValidator validator = HypermediaValidator.createValidator(hypermediaEngine, metadata);
            validator.setLogicalConfigurationListener(new LogicalConfigurationListener() {

                @Override
                public void noMetadataFound(ResourceStateMachine rsm, ResourceState state) {
                    throw new RuntimeException("Invalid configuration of resource state [" + state
                            + "] - no metadata for entity [" + state.getEntityName() + "]");
                }

                @Override
                public void noActionsConfigured(ResourceStateMachine rsm, ResourceState state) {
                    throw new RuntimeException("Invalid configuration of resource state [" + state
                            + "] - no actions configured");
                }

                @Override
                public void viewActionNotSeen(ResourceStateMachine rsm, ResourceState state) {
                    if (!state.isPseudoState()) {
                        LOGGER.warn("Invalid configuration of resource state [{}] - no view command", state);
                    }
                }

                @Override
                public void actionNotAvailable(ResourceStateMachine rsm, ResourceState state, Action action) {
                    throw new RuntimeException("Invalid configuration of resource state [" + state
                            + "] - no command for action [" + action + "]");
                }
            });

            if (printGraph && hypermediaEngine.getInitial() != null) {
                LOGGER.info("State graph for [{}] [{}]", this.toString(), validator.graph());
            }

            validator.validate();
        }
    }

    public ResourceStateMachine getHypermediaEngine() {
        return hypermediaEngine;
    }

    public ResourceRequestHandler getResourceRequestHandler() {
        return resourceRequestHandler;
    }

    public String getResourcePath() {
        return resourcePath;
    }

    /*
     * TODO: shouldn't this return the parent's fully qualified resource path
     * with the current's resource path as a suffix?
     */
    public String getFQResourcePath() {
	    
		String result = getResourcePath();

        if (getParent() != null) {
			result = getParent().getResourcePath() + result;
        }

		return result;
    }

    @Override
    public ResourceInteractionModel getParent() {
        return parent;
    }

    @Override
    public Collection<ResourceInteractionModel> getChildren() {
        List<ResourceInteractionModel> result = new ArrayList<ResourceInteractionModel>();

        for (ResourceState s : hypermediaEngine.getResourceStatesForPath(this.resourcePath)) {
            Map<String, Set<ResourceState>> resourceStates = hypermediaEngine.getResourceStatesByPath(s);

            for (String childPath : resourceStates.keySet()) {
                // get the sub states
                HTTPHypermediaRIM child = null;

                if (childPath.equals(s.getResourcePath())) {
                    continue;
                }

                child = new HTTPHypermediaRIM(null, getCommandController(), hypermediaEngine, metadata, childPath,
                        false);
                result.add(child);
            }
        }

        return result;
    }

    /*
     * The map of all commands for http methods, paths, and media types.
     */
    protected CommandController getCommandController() {
        return commandController;
    }

    /**
     * GET a resource representation.
     *
     * @precondition a valid GET command for this resourcePath + id must be
     *               registered with the command controller
     * @postcondition a Response with non null Status must be returned
     * @invariant resourcePath not null
     * @see com.temenos.interaction.core.rim.HTTPResourceInteractionModel#get(javax.ws.rs.core.HttpHeaders,
     *      java.lang.String)
     */
    @Override
    public Response get(@Context HttpHeaders headers, @PathParam("id") String id, @Context UriInfo uriInfo) {
        assert (getResourcePath() != null);
        Event event = new Event("GET", HttpMethod.GET);

        // handle request
        return handleRequest(headers, uriInfo, event, null);
    }    

    private Response handleRequest(@Context HttpHeaders headers, @Context UriInfo uriInfo, Event event,
            EntityResource<?> resource) {
        long begin = System.nanoTime();
        // determine action
        InteractionCommand action = hypermediaEngine.determineAction(event, getFQResourcePath());

        // create the interaction context
        InteractionContext ctx = buildInteractionContext(headers, uriInfo, event);

        // look for cached response
        Cache cache = hypermediaEngine.getCache();
        Response.ResponseBuilder cached = null;
        if (cache != null && event.isSafe()) {
            cached = cache.get(ctx.getUriInfo().getRequestUri().toString());
        } else {
            LOGGER.debug("Cannot cache {}", uriInfo.getRequestUri());
        }
        Response response = null;
        if (cached != null) {
            response = cached.build();
        } else {
            response = handleRequest(headers, ctx, event, action, resource, null);
        }
        
        long end = System.nanoTime();
        long totalTime = end - begin;

        LOGGER.info(
                "iris_request IRIS Service RequestTime(ns)={} startTime(ns)={} endTime(ns)={} EntityName={} MethodType={} URI={} {}",
                totalTime, begin, end, getFQResourcePath(), event.getMethod(), uriInfo.getRequestUri(),
                cached != null ? " (cached response)" : "");

        return response;
    }
    
    protected Response handleRequest(@Context HttpHeaders headers, InteractionContext ctx, Event event,
            InteractionCommand action, EntityResource<?> resource, ResourceRequestConfig config) {
        return handleRequest(headers, ctx, event, action, resource, config, false);
    }


    protected Response handleRequest(@Context HttpHeaders headers, InteractionContext ctx, Event event,
            InteractionCommand action, EntityResource<?> resource, ResourceRequestConfig config, boolean ignoreAutoTransitions) {
        assert (event != null);
        StatusType status = Status.NOT_FOUND;

        if (action == null) {
            if (event.isUnSafe()) {
                status = HttpStatusTypes.METHOD_NOT_ALLOWED;
            }

            return buildResponse(headers, ctx.getPathParameters(), status, null, getInteractions(), null,
                    event.isSafe());
        }

        // determine current state, target state, and link used
        initialiseInteractionContext(headers, event, ctx, resource);
        // execute action
        InteractionCommand.Result result = null;
        try {
            long begin = System.nanoTime();
            result = action.execute(ctx);
            
            long end = System.nanoTime();
            long totalTime = end - begin;

            LOGGER.info(
                    "iris_request_command CommandExecution RequestTime(ns)={} startTime(ns)={} endTime(ns)={} EntityName={}",
                    totalTime, begin, end, getFQResourcePath());
            
            assert (result != null) : "InteractionCommand must return a result";
            status = determineStatus(headers, event, ctx, result);
        } catch (InteractionException ie) {
            LOGGER.error("Interaction command on state [{}] failed with error [{} - {}]: ", 
                    ctx.getCurrentState().getId(), ie.getHttpStatus(), ie.getHttpStatus().getReasonPhrase(), ie);
            status = ie.getHttpStatus();
            ctx.setException(ie);
        }

        if (ctx.getResource() != null && !"Errors".equals(ctx.getResource().getEntityName())) {
            /*
             * Add entity information to this resource
             */
            ctx.getResource().setEntityName(ctx.getCurrentState().getEntityName());
        }

        // determine status
        if (ctx.getResource() != null && status.getFamily() == Status.Family.SUCCESSFUL) {
            /*
             * How should we handle the representation of this resource
             */
            Transition selfTransition = null;
            boolean injectLinks = true;
            boolean embedResources = true;

            if (config != null) {
                selfTransition = config.getSelfTransition();
                injectLinks = config.isInjectLinks();
                embedResources = config.isEmbedResources();
            }

            if (injectLinks) {
                /*
                 * Add hypermedia information to this resource
                 */
                hypermediaEngine.injectLinks(this, ctx, ctx.getResource(), selfTransition, headers, metadata);
            }

            if (embedResources) {
                /*
                 * Add embedded resources this resource
                 */
                hypermediaEngine.embedResources(this, headers, ctx, ctx.getResource());
            }

        }

        // build response
        return buildResponse(headers, ctx.getPathParameters(), status, ctx.getResource(), null, ctx, event.isSafe(), ignoreAutoTransitions); 
    }

    private ResourceState initialiseInteractionContext(HttpHeaders headers, Event event, InteractionContext ctx,
            EntityResource<?> resource) {
        // set the resource for the commands to access
        if (resource != null) {
            ctx.setResource(resource);
        }

        ResourceState targetState = null;

        if (headers != null) {
            // Apply the etag on the If-Match header if available
            ctx.setPreconditionIfMatch(HeaderHelper.getFirstHeader(headers, HttpHeaders.IF_MATCH));

            ctx.setAcceptLanguage(HeaderHelper.getFirstHeader(headers, HttpHeaders.ACCEPT_LANGUAGE));
            // work out the target state and link used
            LinkHeader linkHeader = null;
            List<String> linkHeaders = headers.getRequestHeader("Link");

            if (linkHeaders != null && linkHeaders.size() > 0) {
                // there must be only one Link header
                assert (linkHeaders.size() == 1);
                linkHeader = LinkHeader.valueOf(linkHeaders.get(0));
            }

            Link linkUsed = hypermediaEngine.getLinkFromRelations(ctx.getPathParameters(), null, linkHeader);
            ctx.setLinkUsed(linkUsed);

            if (linkUsed != null) {
                targetState = linkUsed.getTransition().getTarget();
            }
        }

        if (targetState == null) {
            targetState = ctx.getCurrentState();
        }

        ctx.setTargetState(targetState);

        return targetState;
    }

    private StatusType determineStatus(HttpHeaders headers, Event event, InteractionContext ctx,
            InteractionCommand.Result result) {
        assert (event != null);
        assert (ctx != null);

        ResourceState currentState = ctx.getCurrentState();
        StatusType status = null;

        switch (result) {
            case INVALID_REQUEST:
                status = Status.BAD_REQUEST;
                break;
            case FAILURE: {
                if (event.getMethod().equals(HttpMethod.GET) || event.getMethod().equals(HttpMethod.DELETE)) {
                    status = Status.NOT_FOUND;
                    break;
                } else {
                    status = Status.INTERNAL_SERVER_ERROR;
                    break;
                }
            }
            case CONFLICT:
                status = Status.PRECONDITION_FAILED;
                break;
            case CREATED:
            	if (currentState.getTransitions().isEmpty() && ctx.getResource() == null) {
                    status = Status.NO_CONTENT;
                } else {
                    status = Status.CREATED;
                }
                break;
            case SUCCESS: {

                status = Status.INTERNAL_SERVER_ERROR;

                if (event.getMethod().equals(HttpMethod.GET)) {
                    String ifNoneMatch = HeaderHelper.getFirstHeader(headers, HttpHeaders.IF_NONE_MATCH);
                    String etag = ctx.getResource() != null ? ctx.getResource().getEntityTag() : null;
                    List<Transition> redirectTransitions = getTransitions(ctx, currentState, Transition.REDIRECT);
                    if (result == Result.SUCCESS) {
                        if (etag != null && etag.equals(ifNoneMatch)) {
                            // Response etag matches IfNoneMatch precondition
                            status = Status.NOT_MODIFIED;
                        } else if (!redirectTransitions.isEmpty()) {
                        	status = Status.SEE_OTHER;
                        } else if (currentState.getTransitions().isEmpty() && ctx.getResource() == null) {
                        	status = Status.NO_CONTENT;
                        } else {
                        	status = Status.OK;
                        }
                    }
                } else if (event.getMethod().equals(HttpMethod.POST)) {
                    // TODO need to add support for differed create (ACCEPTED)
                    if (result == Result.SUCCESS) {
                    	/*
                    	 * use this condition to attempt to maintain some backward compatibility.  
                    	 * Several RIMs in the 'wild' have returned a CREATED response when an 
                    	 * auto transition occurs.  e.g. 'new' resources would often auto transition
                    	 * to the created entity and that was signalling the 201 CREATED response
                        if (!autoTransitions.isEmpty() && ctx.getResource() != null) {
                            status = Status.CREATED;
                    	 */
                       	if (currentState.getTransitions().isEmpty() && ctx.getResource() == null) {
                            status = Status.NO_CONTENT;
                        } else {
                            status = Status.OK;
                        }
                    }
                } else if (event.getMethod().equals(HttpMethod.PUT)) {
                /*
                 * The resource manager must return an error result code or have
                 * stored this resource in a consistent state (conceptually a
                 * transaction)
                 */
                	if (result == Result.SUCCESS) {
                        if (currentState.getTransitions().isEmpty() && ctx.getResource() == null) {
                            status = Status.NO_CONTENT;
                        } else {
                            status = Status.OK;
                        }
                	}
                } else if (event.getMethod().equals(HttpMethod.DELETE)) {
                    if (result == Result.SUCCESS) {
                        // We do not support a delete command that returns a
                        // resource (HTTP does permit this)
                        assert (ctx.getResource() == null);
                        ResourceState targetState = ctx.getTargetState();
                        Link linkUsed = ctx.getLinkUsed();
                        if (targetState.isTransientState()) {
                            Transition autoTransition = targetState.getRedirectTransition();
                            if (autoTransition.getTarget().getPath().equals(ctx.getCurrentState().getPath())
                                    || (linkUsed != null && autoTransition.getTarget() == linkUsed.getTransition()
                                    .getSource())) {
                                // this transition has been configured to reset
                                // content
                                status = HttpStatusTypes.RESET_CONTENT;
                            } else {
                                status = Status.SEE_OTHER;
                            }
                        } else if (targetState.isPseudoState()
                                || targetState.getPath().equals(ctx.getCurrentState().getPath())) {
                            // did we delete ourselves or pseudo final state, both
                            // are transitions to No Content
                            status = Response.Status.NO_CONTENT;
                        } else {
                            throw new IllegalArgumentException("Resource interaction exception, should not be "
                                    + "possible to use a link where target state is not our current state");
                        }
                    } else {
                        assert (false) : "Unhandled result from Command";
                    }
                }
            }
        }

        return status;
    }

    private InteractionContext buildInteractionContext(HttpHeaders headers, UriInfo uriInfo, Event event) {
        ResourceState currentState = hypermediaEngine.determineState(event, getFQResourcePath());
        
        if(uriInfo.getPath() != null && currentState != null) {        	
            // Extract values of placeholders defined in the resource state's path from the uri, such as id
	        String[] uriSegments = uriInfo.getPath().split("/");        
	        String[] pathSegments = currentState.getPath().substring(1).split("/");
	
	        new URLHelper().extractPathParameters(uriInfo, uriSegments, pathSegments);
        }

        /*
         * Wink passes query parameters without decoding them. So we have to
         * decode them here. Note call to decodeQueryParameters().
         *
         * However wink is found to have already decoded path parameters
         * (possibly because it uses them internally. So we do NOT have to
         * decode them again here. If we did two levels of decoding would be
         * done and, for example, 'ab%2530' would end up as 'ab0' instead of the
         * expected 'ab%30'.
         */
        MultivaluedMap<String, String> queryParameters = uriInfo != null ? uriInfo.getQueryParameters(false) : null;
        MultivaluedMap<String, String> pathParameters = uriInfo != null ? uriInfo.getPathParameters(false) : null;
        // work around an issue in wink, wink does not decode query parameters
        // in 1.1.3
        decodeQueryParams(queryParameters);

        // create the interaction context
        InteractionContext ctx = new InteractionContext(uriInfo, headers, pathParameters, queryParameters,
                currentState, metadata);
        return ctx;
    }
    
    private Response buildResponse(HttpHeaders headers, MultivaluedMap<String, String> pathParameters,
            StatusType status, RESTResource resource, Set<String> interactions, InteractionContext ctx,
            boolean cacheable) {
        return buildResponse(headers, pathParameters, status, resource, interactions, ctx, cacheable, false);
    }

    // param cacheable true if this response is to an in-principle cacheable
    // request (i.e. a GET). This method will
    // determine itself whether the particular resource returned can be
    // considered for caching
    private Response buildResponse(HttpHeaders headers, MultivaluedMap<String, String> pathParameters,
            StatusType status, RESTResource resource, Set<String> interactions, InteractionContext ctx,
            boolean cacheable, boolean ignoreAutoTransitions) {
        assert (status != null); // not a valid get command

        // The key that this should be cached under, if any
        Object cacheKey = null;
        int cacheMaxAge = 0;

        // Build the Response (representation will be created by the jax-rs
        // Provider)
        ResponseBuilder responseBuilder = Response.status(status);

        if (status.equals(HttpStatusTypes.RESET_CONTENT)) {
            responseBuilder = HeaderHelper.allowHeader(responseBuilder, interactions);
        } else if (status.equals(HttpStatusTypes.METHOD_NOT_ALLOWED)) {
            assert (interactions != null);
            responseBuilder = HeaderHelper.allowHeader(responseBuilder, interactions);
        } else if (status.equals(Response.Status.NO_CONTENT)) {
            responseBuilder = HeaderHelper.allowHeader(responseBuilder, interactions);
        } else if (status.equals(Response.Status.SEE_OTHER)) {
            ResourceState currentState = ctx.getCurrentState();
            Object entity = null;
            if (resource != null) {
                entity = ((EntityResource<?>) resource).getEntity();
            }
            List<Transition> autoTransitions = getTransitions(ctx, currentState, Transition.AUTO);
            Transition autoTransition = autoTransitions.size() > 0 ? autoTransitions.iterator().next() : null;
            if (autoTransition != null) {
                if (autoTransitions.size() > 1)
                    LOGGER.warn("Resource state [{}] has multiple auto-transitions. Using [{}].", currentState.getName(), autoTransition.getId());
                ResponseWrapper autoResponse = getResource(headers, autoTransition, ctx);
                if (autoResponse.getResponse().getStatus() != Status.OK.getStatusCode()) {
                    LOGGER.warn("Auto transition target did not return HttpStatus.OK status [{}]", autoResponse.getResponse().getStatus());
                    
                    responseBuilder.status(autoResponse.getResponse().getStatus());
                }
                resource = autoResponse.getRESTResource();
                assert (resource != null);
                responseBuilder.entity(resource.getGenericEntity());
                responseBuilder = HeaderHelper.etagHeader(responseBuilder, resource.getEntityTag());
            } else {
                ResourceState targetState = ctx.getTargetState();
                Transition redirectTransition = targetState.getRedirectTransition();

                LinkGenerator linkGenerator = new LinkGeneratorImpl(hypermediaEngine, redirectTransition, null).setAllQueryParameters(true);
                Collection<Link> links = linkGenerator.createLink(pathParameters, ctx.getQueryParameters(), entity);
                Link target = (!links.isEmpty()) ? links.iterator().next() : null;
                responseBuilder = setLocationHeader(responseBuilder, target.getHref(), null);
            }
        } else if (status.equals(Response.Status.NOT_MODIFIED)) {
            responseBuilder = HeaderHelper.allowHeader(responseBuilder, interactions);
        } else if (status.getFamily() == Response.Status.Family.SUCCESSFUL) {
            ResourceState currentState = ctx.getCurrentState();
            if(!ignoreAutoTransitions){
                List<Transition> autoTransitions = getTransitions(ctx, currentState, Transition.AUTO);
                if (!autoTransitions.isEmpty()) {
                    ResponseWrapper autoResponse = resolveAutomaticTransitions(headers, ctx, responseBuilder, currentState, autoTransitions);
                    responseBuilder = setLocationHeader(responseBuilder, autoResponse.getSelfLink().getHref(), autoResponse.getRequestParameters());
                    if (autoResponse.getResponse().getEntity() != null) {
                        resource = (RESTResource) ((GenericEntity<?>) autoResponse.getResponse().getEntity()).getEntity();
                    }
                }
            }
            if (resource != null) {
                StreamingOutput streamEntity = null;
                if (resource instanceof EntityResource<?>) {
                    Object entity = ((EntityResource<?>) resource).getEntity();
                    if (entity instanceof StreamingOutput) {
                        streamEntity = (StreamingOutput) entity;
                    }
                }
                /*
                 * Streaming or Wrap response into a JAX-RS GenericEntity object to
                 * ensure we have the type information available to the Providers
                 */
                if (streamEntity != null) {
                    responseBuilder.entity(streamEntity);
                } else {
                    responseBuilder.entity(resource.getGenericEntity());
                }
                responseBuilder = HeaderHelper.etagHeader(responseBuilder, resource.getEntityTag());
            }
            responseBuilder = HeaderHelper.allowHeader(responseBuilder, interactions);

            // If this was for a safe event, and there is a maxAge, apply it.
            cacheMaxAge = currentState.getMaxAge();
            if (cacheMaxAge > 0 && cacheable) {
                cacheKey = ctx.getRequestUri();
                LOGGER.info("Setting maxAge header {} for {} in state {}", currentState.getMaxAge(), cacheKey, currentState.getName());
                
                responseBuilder = HeaderHelper.maxAgeHeader(responseBuilder, cacheMaxAge);
            }

        } else if ((status.getFamily() == Response.Status.Family.CLIENT_ERROR || status.getFamily() == Response.Status.Family.SERVER_ERROR)
                && ctx != null) {
            if (ctx.getCurrentState().getErrorState() != null) {
                // Resource has an onerror handler
                ResourceState errorState = ctx.getCurrentState().getErrorState();
                Transition resourceTransition = new Transition.Builder().method("GET").source(errorState)
                        .target(errorState).build();
                ResponseWrapper errorResponse = getResource(headers, resourceTransition, ctx);
                RESTResource errorResource = (RESTResource) ((GenericEntity<?>) errorResponse.getResponse().getEntity()).getEntity();
                responseBuilder.entity(errorResource.getGenericEntity());
            } else if (hypermediaEngine.getException() != null && ctx.getException() != null) {
                // Resource state machine has an exception handler
                ResourceState exceptionState = hypermediaEngine.getException();
                Transition resourceTransition = new Transition.Builder().method("GET").source(exceptionState)
                        .target(exceptionState).build();
                ResponseWrapper exceptionResponse = getResource(headers, resourceTransition, ctx);
                RESTResource exceptionResource = (RESTResource) ((GenericEntity<?>) exceptionResponse.getResponse().getEntity())
                        .getEntity();
                responseBuilder.entity(exceptionResource.getGenericEntity());
            } else if (resource != null) {
                // Just return the resource entity
                responseBuilder.entity(resource.getGenericEntity());
            }
            responseBuilder = HeaderHelper.allowHeader(responseBuilder, interactions);
        }

        // add any headers added in the commands
        if (ctx != null && ctx.getResponseHeaders() != null) {
            Map<String, String> responseHeaders = ctx.getResponseHeaders();
            for (String name : responseHeaders.keySet()) {
                responseBuilder.header(name, responseHeaders.get(name));
            }
        }

        // cache the response if it is valid to do so
        Cache cache = hypermediaEngine.getCache();
        if (cache != null && cacheKey != null && cacheMaxAge > 0) {
            LOGGER.info("Cache {}", cacheKey);
            
            cache.put(cacheKey, responseBuilder, cacheMaxAge);
        }

        LOGGER.info("Building response {} {}", status.getStatusCode(), status.getReasonPhrase());
        
        Response response = responseBuilder.build();

        return response;
    }

    private ResponseWrapper resolveAutomaticTransitions(HttpHeaders headers,
            InteractionContext ctx, ResponseBuilder responseBuilder, ResourceState currentState,
            List<Transition> autoTransitions) {
        Transition autoTransition;
        ResponseWrapper autoResponse;
        Map<ResourceState, ResponseWrapper> resolvedDynamicResourceStates = new HashMap<ResourceState, ResponseWrapper>();
        do {
            autoTransition = autoTransitions.get(0);
            if (autoTransitions.size() > 1)
                LOGGER.warn("Resource state [{}] has multiple auto-transitions. Using [{}].", currentState.getName(), autoTransition.getId());
            autoResponse = getResource(headers, autoTransition, ctx);
            if(autoTransition.getTarget() instanceof DynamicResourceState){
                if(resolvedDynamicResourceStates.get(autoResponse.getResolvedState()) == null){
                    resolvedDynamicResourceStates.put(autoResponse.getResolvedState(), autoResponse);
                }else{ //we have visited this resource before
                    autoResponse = resolvedDynamicResourceStates.get(autoResponse.getResolvedState());
                    break;
                }
            }
            
            if (autoResponse.getRESTResource() != null) {
                ctx.setResource(autoResponse.getRESTResource());
            }
            
            InteractionContext newCtx = new InteractionContext(ctx, headers, null, null, autoResponse.getResolvedState());
            newCtx.setResource(autoResponse.getRESTResource());
            autoTransitions = getTransitions(newCtx, autoResponse.getResolvedState(), Transition.AUTO);
        }while(!autoTransitions.isEmpty() && autoTransition.isType(Transition.AUTO));
        if (autoResponse.getResponse().getStatus() != Status.OK.getStatusCode()) {
            LOGGER.warn("Auto transition target did not return HttpStatus.OK status [{}]", autoResponse.getResponse().getStatus());
            responseBuilder.status(autoResponse.getResponse().getStatus());
        }
        return autoResponse;
    }

    private List<Transition> getTransitions(InteractionContext ctx, ResourceState state, int transitionType) {
        List<Transition> result = new ArrayList<Transition>();

        List<Transition> transitions = state.getTransitions();

        boolean continueSeaching = true;

        if (transitions != null) {
            for (int i = 0; i < transitions.size() && continueSeaching; i++) {
                Transition transition = transitions.get(i);
                TransitionCommandSpec commandSpec = transition.getCommand();

                if ((commandSpec.getFlags() & transitionType) == transitionType) {

                    // evaluate the conditional expression
                    Expression conditionalExp = commandSpec.getEvaluation();

                    if (conditionalExp != null) {
                        // There is a conditional expression

                        if (!conditionalExp.evaluate(this, ctx, null)) {
                            // Expression is not satisfied so skip it
                            continue;
                        }

                        if (Transition.AUTO == transitionType) {
                            // Auto transition expression satisfied - Short
                            // circuit, we are only interested in this
                            // transition
                            result.clear();
                            continueSeaching = false;
                        }
                    }

                    result.add(transition);
                }
            }
        }

        return result;
    }

    /*
     * Returns the resource on the specified resource state. NB - the one
     * essential difference between this getResource method and the
     * ResourceRequestHandler is that the target here expects InteractionContext
     * to be populated with the previous commands RESTResource i.e. {@link
     * InteractionContext#getResource}
     */
    private ResponseWrapper getResource(HttpHeaders headers, Transition resourceTransition, InteractionContext ctx) {
        ResourceState targetState = resourceTransition.getTarget();
        MultivaluedMap<String, String> newQueryParameters = copyParameters(ctx.getQueryParameters());
        MultivaluedMap<String, String> newPathParameters = buildPathParameters(resourceTransition, ctx);
        if (targetState instanceof DynamicResourceState) {
            ResourceStateAndParameters stateAndParams = hypermediaEngine.resolveDynamicState((DynamicResourceState) targetState, new HashMap<String, Object>(), ctx);
            MultivaluedMap<String, String> stateParameters = getStateParameters(stateAndParams);
            targetState = stateAndParams.getState();
            newQueryParameters = stateParameters;
            newPathParameters.putAll(filterParameters(stateParameters, newPathParameters.keySet()));
        }
        try {
            ResourceRequestConfig config = new ResourceRequestConfig.Builder().transition(resourceTransition).build();
            Event event = new Event("", "GET");
            InteractionCommand action = hypermediaEngine.buildWorkflow(event, targetState.getActions());

            InteractionContext newCtx = new InteractionContext(ctx, headers, newPathParameters, newQueryParameters, targetState);
            Response response = handleRequest(headers, newCtx, event, action, (EntityResource<?>) ctx.getResource(), config, true);
            
            //forward any parameters set by the executed InteractionCommand to the InteractionContext
            ctx.getQueryParameters().putAll(newCtx.getQueryParameters());
            ctx.getOutQueryParameters().putAll(newCtx.getOutQueryParameters());
            ctx.getPathParameters().putAll(filterParameters(newPathParameters, ctx.getPathParameters().keySet()));

            return new ResponseWrapper(response, new ArrayList<Link>(
                    new LinkGeneratorImpl(hypermediaEngine, targetState.getSelfTransition(), newCtx
                    ).createLink(newPathParameters, newQueryParameters, response.getEntity())
                ).get(0), 
                newQueryParameters,
                targetState
            );

        } catch (Exception ie) {
            LOGGER.error("Failed to access resource [{}] with error:", targetState.getId(), ie);
            throw new RuntimeException(ie);
        }
    }

    protected MultivaluedMap<String, String> filterParameters(MultivaluedMap<String, String> parameters, Set<String> filterKeys) {
        MultivaluedMap<String, String> filteredParameters = new MultivaluedMapImpl<>();
        if (filterKeys == null) {
            return filteredParameters;
        }
        for (String filterKey : filterKeys) {
            if (parameters.containsKey(filterKey)) {
                filteredParameters.put(filterKey, parameters.get(filterKey));
            }
        }
        return filteredParameters;
    }

    protected MultivaluedMap<String, String> getStateParameters(ResourceStateAndParameters stateAndParams) {
        return ParameterAndValue.getParamAndValueAsMultiValueMap(stateAndParams.getParams());
    }

    protected MultivaluedMap<String, String> buildPathParameters(Transition resourceTransition, InteractionContext ctx) {
        MultivaluedMap<String, String> pathParameters = copyParameters(ctx.getPathParameters());
        if (ctx.getResource() != null) {
            Map<String, Object> transitionProperties = hypermediaEngine.getTransitionProperties(resourceTransition,
                    getEntityResource(ctx.getResource()), ctx.getPathParameters(), ctx.getQueryParameters());
            for (Entry<String, Object> entry : transitionProperties.entrySet()) {
                if (transitionProperties.get(entry.getKey()) != null)
                    pathParameters.add(entry.getKey(), entry.getValue().toString());
            }
        }
        return pathParameters;
    }

    protected MultivaluedMap<String, String> copyParameters(MultivaluedMap<String, String> parameters) {
        MultivaluedMap<String, String> parametersCopy = new MultivaluedMapImpl<>();
        parametersCopy.putAll(parameters);
        return parametersCopy;
    }

    // helper function
    private Object getEntityResource(RESTResource currentResource) {
        try {
            // sometime some resource throw ClassCastException
            return ((EntityResource<?>) currentResource).getEntity();
        } catch (ClassCastException e) {
            LOGGER.error("Failed to get entity resource", e);
        }

        EntityResource<?> er = new EntityResource<RESTResource>(currentResource);
        return er.getEntity();
    }

    @SuppressWarnings("static-access")
    private void decodeQueryParams(MultivaluedMap<String, String> queryParameters) {

        try {

            if (queryParameters == null) {
                return;
            }

            URLDecoder ud = new URLDecoder();

            for (String key : queryParameters.keySet()) {
                List<String> values = queryParameters.get(key);
                if (values != null) {
                    List<String> newValues = new ArrayList<String>();
                    for (String value : values) {
                        if (value != null)
                            newValues.add(ud.decode(value, "UTF-8"));
                    }
                    queryParameters.put(key, newValues);
                }
            }

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Response put(@Context HttpHeaders headers, @Context UriInfo uriInfo, InMultiPart inMP) {
        Event event = new Event("PUT", HttpMethod.PUT);

        return handleMultipartRequest(headers, uriInfo, inMP, event);
    }

    @Override
    public Response post(HttpHeaders headers, UriInfo uriInfo, InMultiPart inMP) {
        Event event = new Event("POST", HttpMethod.POST);

        return handleMultipartRequest(headers, uriInfo, inMP, event);
    }

    private Response handleMultipartRequest(HttpHeaders headers, UriInfo uriInfo, InMultiPart inMP, Event event) {
        InteractionContext ctx = buildInteractionContext(headers, uriInfo, event);
        String entityName = ctx.getCurrentState().getEntityName();

        Response result = null;

        while (inMP.hasNext()) {
            InPart part = inMP.next();

            StreamingInput streamingInput = new StreamingInput(entityName, part.getInputStream(), part.getHeaders());
            EntityResource<StreamingInput> resource = new EntityResource<StreamingInput>(entityName, streamingInput);

            result = handleRequest(headers, uriInfo, event, resource);

            if (!isSuccessful(result)) {
                break; // The result HTTP status code was not in the 2XX range
            }
        }

        return result;
    }

    private boolean isSuccessful(Response result) {
        return result.getStatus() / 100 == 2; // Work out whether the result
        // HTTP status code was within the
        // 2XX range
    }

    /**
     * Handle a POST from a regular html form.
     */
    @Override
    public Response post(@Context HttpHeaders headers, @PathParam("id") String id, @Context UriInfo uriInfo,
            MultivaluedMap<String, String> formParams) {
        assert (getResourcePath() != null);
        Event event = new Event("POST", HttpMethod.POST);
        // handle request
        InteractionContext ctx = buildInteractionContext(headers, uriInfo, event);
        initialiseInteractionContext(headers, event, ctx, null);
        String entityName = ctx.getCurrentState().getEntityName();
        EntityResource<Entity> resource = new EntityResource<Entity>(entityName, createEntity(entityName, formParams));

        return handleRequest(headers, uriInfo, event, resource);
    }

    private Entity createEntity(String entityName, MultivaluedMap<String, String> formParams) {
        EntityProperties fields = new EntityProperties();

        for (String key : formParams.keySet()) {
            fields.setProperty(new EntityProperty(key, formParams.getFirst(key)));
        }

        return new Entity(entityName, fields);
    }

    /**
     * POST a document to a resource.
     *
     * @precondition a valid POST command for this resourcePath + id must be
     *               registered with the command controller
     * @postcondition a Response with non null Status must be returned
     * @invariant resourcePath not null
     */
    @Override
    public Response post(@Context HttpHeaders headers, @PathParam("id") String id, @Context UriInfo uriInfo,
            EntityResource<?> resource) {
        LOGGER.info("POST {}", getFQResourcePath());
        assert (getResourcePath() != null);
        Event event = new Event("POST", HttpMethod.POST);

        // handle request
        return handleRequest(headers, uriInfo, event, resource);
    }

    /**
     * PUT a resource.
     *
     * @precondition a valid PUT command for this resourcePath + id must be
     *               registered with the command controller
     * @postcondition a Response with non null Status must be returned
     * @invariant resourcePath not null
     * @see com.temenos.interaction.core.rim.HTTPResourceInteractionModel#put(javax.ws.rs.core.HttpHeaders,
     *      java.lang.String, com.temenos.interaction.core.EntityResource)
     */
    @Override
    public Response put(@Context HttpHeaders headers, @PathParam("id") String id, @Context UriInfo uriInfo,
            EntityResource<?> resource) {
        LOGGER.info("PUT {}", getFQResourcePath());
        assert (getResourcePath() != null);
        Event event = new Event("PUT", HttpMethod.PUT);

        // handle request
        return handleRequest(headers, uriInfo, event, resource);
    }

    /**
     * DELETE a resource.
     *
     * @precondition a valid DELETE command for this resourcePath + id must be
     *               registered with the command controller
     * @postcondition a Response with non null Status must be returned
     * @invariant resourcePath not null
     * @see com.temenos.interaction.core.rim.HTTPResourceInteractionModel#delete(javax.ws.rs.core.HttpHeaders,
     *      java.lang.String)
     */
    @Override
    public Response delete(@Context HttpHeaders headers, @PathParam("id") String id, @Context UriInfo uriInfo) {
        LOGGER.info("DELETE {}", getFQResourcePath());
        assert (getResourcePath() != null);
        Event event = new Event("DELETE", HttpMethod.DELETE);

        // handle request
        return handleRequest(headers, uriInfo, event, null);
    }

    /**
     * OPTIONS for a resource.
     *
     * @precondition a valid GET command for this resourcePath must be
     *               registered with the command controller
     * @postcondition a Response with non null Status must be returned
     * @invariant resourcePath not null
     */
    @Override
    public Response options(@Context HttpHeaders headers, @PathParam("id") String id, @Context UriInfo uriInfo) {
        LOGGER.info("OPTIONS {}", getFQResourcePath());
        assert (getResourcePath() != null);
        Event event = new Event("OPTIONS", HttpMethod.GET);
        // create the interaction context
        InteractionContext ctx = buildInteractionContext(headers, uriInfo, event);

        // TODO add support for OPTIONS /resource/* which will provide
        // information about valid interactions for any entity
        return buildResponse(headers, ctx.getPathParameters(), Status.NO_CONTENT, null, getInteractions(), ctx, false, true);
    }

    /**
     * Get the valid methods for interacting with this resource.
     *
     * @return
     */
    public Set<String> getInteractions() {
        Set<String> interactions = new HashSet<String>();
        interactions.addAll(hypermediaEngine.getInteractionByPath().get(getFQResourcePath()));
        interactions.add("HEAD");
        interactions.add("OPTIONS");

        return interactions;
    }

    @Override
    public ResourceState getCurrentState() {
        // TODO, need to figure out how to pass event in where required
        return hypermediaEngine.determineState(new Event("GET", HttpMethod.GET), getFQResourcePath());
    }

    public boolean equals(Object other) {
        // check for self-comparison
        if (this == other) {
            return true;
        }
        if (!(other instanceof HTTPHypermediaRIM)) {
            return false;
        }
        HTTPHypermediaRIM otherResource = (HTTPHypermediaRIM) other;

        return getFQResourcePath().equals(otherResource.getFQResourcePath());
    }

    public int hashCode() {
        return getFQResourcePath().hashCode();
    }

    public String toString() {
        return ("HTTPHypermediaRIM [" + getFQResourcePath() + "]");
    }
    
    protected ResponseBuilder setLocationHeader(ResponseBuilder builder, 
            String dest, MultivaluedMap<String, String> param){
        return HeaderHelper.locationHeader(builder, dest, param);
    }

}