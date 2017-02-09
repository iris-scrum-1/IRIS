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

import static junit.framework.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.apache.wink.common.model.multipart.InMultiPart;
import org.apache.wink.common.model.multipart.InPart;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.CommandController;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionCommand.Result;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.command.MapBasedCommandController;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityMetadata;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.Action;
import com.temenos.interaction.core.hypermedia.BeanTransformer;
import com.temenos.interaction.core.hypermedia.ParameterAndValue;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateAndParameters;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.web.RequestContext;

public class TestHTTPHypermediaRIM {

    @Before
    public void setup() {
        // initialise the thread local request context with requestUri and
        // baseUri
        RequestContext ctx = new RequestContext("http://localhost/myservice.svc", "/baseuri/", null);
        RequestContext.setRequestContext(ctx);
    }

    // create command returning the supplied entity
    private InteractionCommand createCommand(final String entityName, final Entity entity, final InteractionCommand.Result result) {
        InteractionCommand command = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
            	if (entity == null) {
            		ctx.setResource(null);
            	} else {
            		ctx.setResource(new EntityResource<Entity>(entityName, entity));
            	}
                return result;
            }
        };
        return command;
    }
    
    private List<Action> mockActions() {
        return mockActions(new Action("GET", Action.TYPE.VIEW), 
        		new Action("PUT", Action.TYPE.ENTRY), 
        		new Action("POST", Action.TYPE.ENTRY),
        		new Action("DELETE", Action.TYPE.ENTRY));
    }

    private List<Action> mockActions(Action...actions) {
        List<Action> actionsList = new ArrayList<Action>();
        for (Action a : actions) {
        	actionsList.add(a);
        }
    	return actionsList;
    }
    
    private CommandController mockCommandController() {
    	return mockCommandController(createCommand("entity", null, Result.FAILURE));
    }

    private CommandController mockCommandController(InteractionCommand mockCommand) {
    	MapBasedCommandController cc = new MapBasedCommandController();
    	cc.getCommandMap().put("GET", mockCommand);
    	cc.getCommandMap().put("PUT", mockCommand);
    	cc.getCommandMap().put("POST", mockCommand);
    	cc.getCommandMap().put("DELETE", mockCommand);
        return cc;
    }

    @Test
    public void testResourcePath() throws InteractionException {
        String ENTITY_NAME = "NOTE";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", mockActions(), "/notes/{id}");
        HTTPHypermediaRIM resource = new HTTPHypermediaRIM(mockCommandController(), new ResourceStateMachine(initial),
                createMockMetadata());
        assertEquals("/notes/{id}", resource.getResourcePath());
    }

    /* We decode the query parameters to workaround an issue in Wink */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDecodeQueryParameters() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        // this test simply mocks a command to test the context query parameters
        // is initialised properly
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.FAILURE);
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        MultivaluedMap<String, String> queryMap = new MultivaluedMapImpl<String>();
        queryMap.add("$filter", "this+that");
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(queryMap);

        rim.get(mock(HttpHeaders.class), "id", uriInfo);
        verify(mockCommand).execute((InteractionContext) argThat(new InteractionContextArgumentMatcher()));
    }

    class InteractionContextArgumentMatcher extends ArgumentMatcher<InteractionContext> {
        public boolean matches(Object o) {
            if (o instanceof InteractionContext) {
                InteractionContext ctx = (InteractionContext) o;
                MultivaluedMap<String, String> mvmap = ctx.getQueryParameters();
                if (!mvmap.getFirst("$filter").equals("this that")) {
                    return false;
                }
                return true;
            }
            return false;
        }
    }

    /*
     * We decode the query parameters containing escaped '%' to workaround an
     * issue in Wink
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDecodeQueryParametersPercent() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        // this test simply mocks a command to test the context query parameters
        // is initialised properly
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.FAILURE);
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        MultivaluedMap<String, String> queryMap = new MultivaluedMapImpl<String>();
        queryMap.add("$filter", "this%25that");
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(queryMap);

        rim.get(mock(HttpHeaders.class), "id", uriInfo);
        verify(mockCommand).execute((InteractionContext) argThat(new InteractionContextArgumentMatcherPercent()));
    }

    class InteractionContextArgumentMatcherPercent extends ArgumentMatcher<InteractionContext> {
        public boolean matches(Object o) {
            if (o instanceof InteractionContext) {
                InteractionContext ctx = (InteractionContext) o;
                MultivaluedMap<String, String> mvmap = ctx.getQueryParameters();
                if (!mvmap.getFirst("$filter").equals("this%that")) {
                    return false;
                }
                return true;
            }
            return false;
        }
    }

    /*
     * We decode the path parameters containing escaped '%' to workaround an
     * issue in Wink.
     * 
     * Because Wink itself decodes path parameters in the UriInfo we do NOT want
     * to decode a second time. Expect the 'encoded' value back.
     */
    @SuppressWarnings({ "unchecked" })
    @Test
    public void testDecodePathParametersPercent() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        // this test simply mocks a command to test the context query parameters
        // is initialised properly
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.FAILURE);
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        // Mock decoded path map.
        MultivaluedMap<String, String> decodedPathMap = new MultivaluedMapImpl<String>();
        decodedPathMap.add("id", "ab%cd");
        when(uriInfo.getPathParameters(true)).thenReturn(decodedPathMap);

        // Mock encoded path map.
        MultivaluedMap<String, String> encodedPathMap = new MultivaluedMapImpl<String>();
        encodedPathMap.add("id", "ab%25cd");
        when(uriInfo.getPathParameters(false)).thenReturn(encodedPathMap);

        rim.get(mock(HttpHeaders.class), "id", uriInfo);
        verify(mockCommand).execute((InteractionContext) argThat(new InteractionContextArgumentPathMatcherPercent()));
    }
    
    @Test
    public void testExtractDecodedUriSegments() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.FAILURE);
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath(anyBoolean())).thenReturn("notes/id('id%2FwithSlash')/view");
        String[] expectedResultArray = new String[]{"notes", "id('id/withSlash')", "view"};
        
        String[] resultArray =  rim.extractDecodedUriSegments(uriInfo);
        assertEquals(3, resultArray.length);
        assertArrayEquals(expectedResultArray, resultArray);
    }
    
    /*
     * testing method extractDecodedUriSegment with null and empty input
     * 
     * 
     */
    @Test
    public void testExtractDecodedUriSegmentsWithNullAndEmptyInput() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.FAILURE);
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());
        String[] resultArray =  rim.extractDecodedUriSegments(null);
        if(resultArray.length != 0) {
            fail("When passing null input method extractDecodedUriSegments return some invalid result");
        }
        //Empty input String test 
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPath(anyBoolean())).thenReturn("");
        
        String[] resultArrayforEmptyPath =  rim.extractDecodedUriSegments(uriInfo);
        if(resultArrayforEmptyPath.length != 0) {
            fail("When passing empty input method extractDecodedUriSegments return some invalid result");
        }
    }
    
    

    class InteractionContextArgumentPathMatcherPercent extends ArgumentMatcher<InteractionContext> {
        public boolean matches(Object o) {
            if (o instanceof InteractionContext) {
                InteractionContext ctx = (InteractionContext) o;
                MultivaluedMap<String, String> mvmap = ctx.getPathParameters();
                if (!mvmap.getFirst("id").equals("ab%25cd")) {
                    return false;
                }
                return true;
            }
            return false;
        }
    }

    /* We decode the query parameters to workaround an issue in Wink */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testDecodeQueryParametersNullValue() {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        // RIM with command controller that issues commands that always return
        // FAILURE
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(), new ResourceStateMachine(initialState),
                createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        MultivaluedMap<String, String> queryMap = new MultivaluedMapImpl();
        queryMap.add(null, null);
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(queryMap);

        // should get past here without a NullPointerException
        rim.get(mock(HttpHeaders.class), "id", uriInfo);
    }

    /*
     * This test is for a GET request where the command succeeds, but does not
     * return a resource. A successful GET command should set the requested
     * resource onto the InteractionContext; if it does not expect NO_CONTENT.
     */
    @Test
    public void testSuccessfulGETCommandNoResourceShouldFail() throws Exception {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("GET", Action.TYPE.VIEW)), "/path");

        InteractionCommand mockCommand = createCommand("entity", null, Result.SUCCESS);
        // create mock command controller
        CommandController mockCommandController = mockCommandController(mockCommand);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState),
                createMockMetadata());
        Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    /*
     * This test is for a GET request where the command does not return a
     * result.
     */
    @Test(expected = AssertionError.class)
    public void testGETCommandNoResultShouldFail() throws Exception {
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action("GET", Action.TYPE.VIEW));
        ResourceState initialState = new ResourceState("entity", "state", actions, "/path");

        // this test mocks a command that incorrectly returns no result
        InteractionCommand mockCommand = mock(InteractionCommand.class);

        // create mock command controller
        CommandController mockCommandController = mock(CommandController.class);
        when(mockCommandController.fetchCommand("GET")).thenReturn(mockCommand);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState),
                createMockMetadata());
        rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
    }

    /*
     * This test is for a GET request where the command does not return a
     * result.
     */
    @Test(expected = AssertionError.class)
    public void testDELETECommandNoResultShouldFail() throws Exception {
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action("DELETE", Action.TYPE.ENTRY));
        ResourceState initialState = new ResourceState("entity", "state", actions, "/path");
        initialState.addTransition(new Transition.Builder().method("DELETE").target(initialState).build());

        // this test mocks a command that incorrectly returns no result
        InteractionCommand mockCommand = mock(InteractionCommand.class);

        // create mock command controller
        CommandController mockCommandController = mock(CommandController.class);
        when(mockCommandController.fetchCommand("DELETE")).thenReturn(mockCommand);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState),
                createMockMetadata());
        rim.delete(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
    }

    @Test
    public void testGETCommandInvalidRequest() throws Exception {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");

        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                ctx.setResource(null);
                return Result.INVALID_REQUEST;
            }
        };

        // create mock command controller
        CommandController mockCommandController = mockCommandController(mockCommand);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState),
                createMockMetadata());
        Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
        assertEquals(Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    /*
     * This test is for a GET request where the command succeeds. A successful
     * GET command should set the requested resource onto the
     * InteractionContext.
     */
    public void testSuccessfulGETCommand() throws Exception {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");

        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                ctx.setResource(new EntityResource<Object>());
                return Result.SUCCESS;
            }
        };

        // create mock command controller
        CommandController mockCommandController = mock(CommandController.class);
        when(mockCommandController.fetchCommand("DO")).thenReturn(mockCommand);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState),
                mock(Metadata.class));
        Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
        assertNotNull(response.getEntity());
    }

    /*
     * This test is for a DELETE request where the command returns a resource. A
     * successful DELETE command should not return a new resource and we test
     * this with an assertion.
     */
    @Test(expected = AssertionError.class)
    public void testDeleteCommandReturnsResourceShouldFail() throws Exception {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
        initialState.addTransition(new Transition.Builder().method("DELETE").target(initialState).build());

        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                ctx.setResource(new EntityResource<Object>());
                return Result.SUCCESS;
            }
        };

        // create mock command controller
        CommandController mockCommandController = mockCommandController(mockCommand);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState),
                createMockMetadata());
        rim.delete(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
    }

    /*
     * Test the contract for PUT commands. A PUT command should should receive
     * an InteractionContext that has the new resource set; enabling the command
     * to getResource.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testPutCommandReceivesResource() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("PUT", Action.TYPE.ENTRY)), "/test");
        initialState.addTransition(new Transition.Builder().method("PUT").target(initialState).build());
        // create a mock command to test the context is initialised correctly
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.SUCCESS);
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        rim.put(mock(HttpHeaders.class), "id", uriInfo, new EntityResource<Object>("test resource"));
        verify(mockCommand).execute((InteractionContext) argThat(new CommandReceivesResourceArgumentMatcher()));
    }

    /*
     * Test the contract for multipart PUT commands. A PUT command should should
     * receive an InteractionContext that has the new resource set; enabling the
     * command to process the resource contained in the current part of the
     * multipart request
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMultipartPutCommandReceivesResource() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("PUT", Action.TYPE.ENTRY)), "/test");
        initialState.addTransition(new Transition.Builder().method("PUT").target(initialState).build());
        // create a mock command to test the context is initialised correctly
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.SUCCESS);
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        InMultiPart inMP = mock(InMultiPart.class);
        when(inMP.hasNext()).thenReturn(true, false);
        when(inMP.next()).thenReturn(mock(InPart.class));

        rim.put(mock(HttpHeaders.class), uriInfo, inMP);
        verify(mockCommand).execute((InteractionContext) argThat(new CommandReceivesResourceArgumentMatcher()));
    }

    /*
     * Test the contract for multipart POST commands. A POST command should
     * should receive an InteractionContext that has the new resource set;
     * enabling the command to process the resource contained in the current
     * part of the multipart request
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testMultipartPostCommandReceivesResource() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("PUT", Action.TYPE.ENTRY)), "/test");
        initialState.addTransition(new Transition.Builder().method("POST").target(initialState).build());
        // create a mock command to test the context is initialised correctly
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.SUCCESS);
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        InMultiPart inMP = mock(InMultiPart.class);
        when(inMP.hasNext()).thenReturn(true, false);
        when(inMP.next()).thenReturn(mock(InPart.class));

        rim.post(mock(HttpHeaders.class), uriInfo, inMP);
        verify(mockCommand).execute((InteractionContext) argThat(new CommandReceivesResourceArgumentMatcher()));
    }

    class CommandReceivesResourceArgumentMatcher extends ArgumentMatcher<InteractionContext> {
        public boolean matches(Object o) {
            if (o instanceof InteractionContext) {
                InteractionContext ctx = (InteractionContext) o;
                if (ctx.getResource() == null)
                    return false;
                return true;
            }
            return false;
        }
    }

    /*
     * Test the contract for POST commands. A POST command should could receive
     * an InteractionContext that has the new resource set; enabling the command
     * to getResource.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testPOSTCommandReceivesResource() throws InteractionException {
        List<Action> actions = mockActions(new Action("POST", Action.TYPE.ENTRY));
        ResourceState initialState = new ResourceState("entity", "state", actions, "/test");
        initialState.addTransition(new Transition.Builder().method("POST").target(initialState).build());
        // create a mock command to test the context is initialised correctly
        InteractionCommand mockCommand = mock(InteractionCommand.class);
        when(mockCommand.execute(any(InteractionContext.class))).thenReturn(Result.SUCCESS);
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        rim.post(mock(HttpHeaders.class), "id", uriInfo, new EntityResource<Object>("test resource"));
        verify(mockCommand).execute((InteractionContext) argThat(new CommandReceivesResourceArgumentMatcher()));
    }

    @Test
    public void testPOSTCommandCreate() throws Exception {
        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                ctx.setResource(mock(EntityResource.class));
                return Result.CREATED;
            }
        };

        // create mock command controller
        CommandController mockCommandController = mockCommandController(mockCommand);

        // create a state machine with a POST interaction
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
        ResourceState newState = new ResourceState("entity", "new", mockActions(), "/path");
        initialState.addTransition(new Transition.Builder().method("POST").target(newState).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);
        
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, stateMachine, createMockMetadata());
        Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPOSTCommandCreateNoContent() throws Exception {
        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                ctx.setResource(null);
                return Result.CREATED;
            }
        };

        // create mock command controller
        CommandController mockCommandController = mockCommandController(mockCommand);

        // create a state machine with a POST interaction
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");
        ResourceState newState = new ResourceState("entity", "new", mockActions(), "/path");
        initialState.addTransition(new Transition.Builder().method("POST").target(newState).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);
        
        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, stateMachine, createMockMetadata());
        Response response = rim.post(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test(expected = RuntimeException.class)
    public void testBootstrapInvalidCommandControllerConfigurationPUT() {
        String resourcePath = "/notes/{id}";
        ResourceState exists = new ResourceState("entity", "exists", mockActions(), resourcePath);
        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());

        CommandController cc = mock(CommandController.class);
        new HTTPHypermediaRIM(cc, new ResourceStateMachine(exists), mock(Metadata.class));
    }

    @Test(expected = RuntimeException.class)
    public void testBootstrapInvalidCommandControllerConfigurationPOST() {
        String resourcePath = "/notes/{id}";
        ResourceState exists = new ResourceState("entity", "exists", mockActions(), resourcePath);
        exists.addTransition(new Transition.Builder().method("POST").target(exists).build());

        CommandController cc = mock(CommandController.class);
        new HTTPHypermediaRIM(cc, new ResourceStateMachine(exists), mock(Metadata.class));
    }

    @Test(expected = RuntimeException.class)
    public void testBootstrapInvalidCommandControllerConfigurationDELETE() {
        String resourcePath = "/notes/{id}";
        ResourceState exists = new ResourceState("entity", "exists", mockActions(), resourcePath);
        exists.addTransition(new Transition.Builder().method("DELETE").target(exists).build());

        CommandController cc = mock(CommandController.class);
        new HTTPHypermediaRIM(cc, new ResourceStateMachine(exists), mock(Metadata.class));
    }

    @Test(expected = RuntimeException.class)
    public void testBootstrapInvalidCommandControllerConfigurationGET() {
        String resourcePath = "/notes/{id}";
        ResourceState exists = new ResourceState("entity", "exists", mockActions(), resourcePath);
        exists.addTransition(new Transition.Builder().method("GET").target(exists).build());

        CommandController cc = mock(CommandController.class);
        new HTTPHypermediaRIM(cc, new ResourceStateMachine(exists), mock(Metadata.class));
    }

    @Test
    public void testChildrenRIMsSubstate() {
        String ENTITY_NAME = "DraftNote";
        String resourcePath = "/notes/{id}";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", mockActions(), resourcePath);
        ResourceState draft = new ResourceState(ENTITY_NAME, "draft", mockActions(), "/draft");

        // create draft
        initial.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // updated draft
        draft.addTransition(new Transition.Builder().method("PUT").target(draft).build());

        // supply a transformer to check that this is copied into child resource
        BeanTransformer transformer = new BeanTransformer();

        ResourceStateMachine stateMachine = new ResourceStateMachine(initial, transformer);
        HTTPHypermediaRIM parent = new HTTPHypermediaRIM(mockCommandController(), stateMachine, createMockMetadata());
        Collection<ResourceInteractionModel> resources = parent.getChildren();
        assertEquals(1, resources.size());
        assertEquals(draft.getPath(), resources.iterator().next().getResourcePath());
        assertEquals(transformer, ((HTTPHypermediaRIM) resources.iterator().next()).getHypermediaEngine()
                .getTransformer());
    }

    @Test
    public void testChildrenRIMsDifferentEntity() {
        ResourceState initial = new ResourceState("Note", "initial", mockActions(), "/note/{id}");
        ResourceState comment = new ResourceState("Comment", "draft", mockActions(), "/comments/{noteid}");

        // example uri linkage uses 'id' from Note entity to transition to
        // 'noteid' of comments resource
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("noteid", "id");
        // create comment for note
        initial.addTransition(new Transition.Builder().method("PUT").target(comment).uriParameters(uriLinkageMap)
                .build());
        // update comment
        comment.addTransition(new Transition.Builder().method("PUT").target(comment).build());

        // supply a transformer to check that this is copied into child resource
        BeanTransformer transformer = new BeanTransformer();

        ResourceStateMachine stateMachine = new ResourceStateMachine(initial, transformer);
        HTTPHypermediaRIM parent = new HTTPHypermediaRIM(mockCommandController(), stateMachine, createMockMetadata());
        Collection<ResourceInteractionModel> resources = parent.getChildren();
        assertEquals(1, resources.size());
        assertEquals(comment.getPath(), resources.iterator().next().getResourcePath());
        assertEquals(transformer, ((HTTPHypermediaRIM) resources.iterator().next()).getHypermediaEngine()
                .getTransformer());
    }

    @Test
    public void testPUTCommandCreate() throws Exception {
        MapBasedCommandController commandController = new MapBasedCommandController();
        commandController.getCommandMap().put("GET", createCommand("entity", new Entity("entity", null), Result.SUCCESS));
        commandController.getCommandMap().put("PUT", createCommand("entity", new Entity("entity", null), Result.CREATED));

        // create a state machine with a POST interaction
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("GET", Action.TYPE.VIEW)), "/path");
        ResourceState newState = new ResourceState("entity", "new", mockActions(new Action("PUT", Action.TYPE.ENTRY)), "/path");
        initialState.addTransition(new Transition.Builder().method("PUT").target(newState).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, stateMachine, createMockMetadata());
        Response response = rim.put(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPUTCommandCreateNoContent() throws Exception {
        MapBasedCommandController commandController = new MapBasedCommandController();
        commandController.getCommandMap().put("GET", createCommand("entity", new Entity("entity", null), Result.SUCCESS));
        // put command returns no resource
        commandController.getCommandMap().put("PUT", createCommand("entity", null, Result.CREATED));

        // create a state machine with a POST interaction
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("GET", Action.TYPE.VIEW)), "/path");
        ResourceState newState = new ResourceState("entity", "new", mockActions(new Action("PUT", Action.TYPE.ENTRY)), "/path");
        initialState.addTransition(new Transition.Builder().method("PUT").target(newState).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, stateMachine, createMockMetadata());
        Response response = rim.put(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPUTCommandCreateNoContentAuto() throws Exception {
        MapBasedCommandController commandController = new MapBasedCommandController();
        // get command returns no resource
        commandController.getCommandMap().put("GET", createCommand("entity", null, Result.SUCCESS));
        commandController.getCommandMap().put("PUT", createCommand("entity", new Entity("entity", null), Result.CREATED));

        // create a state machine with a POST interaction
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("GET", Action.TYPE.VIEW)), "/path");
        ResourceState newState = new ResourceState("entity", "new", mockActions(new Action("PUT", Action.TYPE.ENTRY)), "/path");
        ResourceState entityState = new ResourceState("entity", "entity", mockActions(new Action("GET", Action.TYPE.VIEW)), "/path");
        initialState.addTransition(new Transition.Builder().method("PUT").target(newState).build());
        newState.addTransition(new Transition.Builder().target(entityState).flags(Transition.AUTO).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, stateMachine, createMockMetadata());
        Response response = rim.put(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
        assertEquals(Status.NO_CONTENT.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPUTCommandCreateAuto() throws Exception {
        MapBasedCommandController commandController = new MapBasedCommandController();
        commandController.getCommandMap().put("GET", createCommand("entity", new Entity("entity", null), Result.SUCCESS));
        commandController.getCommandMap().put("PUT", createCommand("entity", new Entity("entity", null), Result.CREATED));

        // create a state machine with a POST interaction
        ResourceState initialState = new ResourceState("entity", "state", mockActions(new Action("GET", Action.TYPE.VIEW)), "/path");
        ResourceState newState = new ResourceState("entity", "new", mockActions(new Action("PUT", Action.TYPE.ENTRY)), "/path");
        ResourceState entityState = new ResourceState("entity", "entity", mockActions(new Action("GET", Action.TYPE.VIEW)), "/path");
        initialState.addTransition(new Transition.Builder().method("PUT").target(newState).build());
        newState.addTransition(new Transition.Builder().target(entityState).flags(Transition.AUTO).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(commandController, stateMachine, createMockMetadata());
        Response response = rim.put(mock(HttpHeaders.class), "id", mockEmptyUriInfo(), mock(EntityResource.class));
        assertEquals(Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void testPUTCommandConflict() throws Exception {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/path");

        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                ctx.setResource(null);
                return Result.CONFLICT;
            }
        };

        // create mock command controller
        CommandController mockCommandController = mockCommandController(mockCommand);

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(initialState),
                createMockMetadata());
        Response response = rim.get(mock(HttpHeaders.class), "id", mockEmptyUriInfo());
        assertEquals(Status.PRECONDITION_FAILED.getStatusCode(), response.getStatus());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPutCommandWithIfMatchHeader() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        initialState.addTransition(new Transition.Builder().method("PUT").target(initialState).build());

        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                assertNotNull(ctx.getResource());
                assertNull(ctx.getResource().getEntityTag()); // Etag is a
                                                              // response header
                                                              // and should be
                                                              // null
                assertNotNull(ctx.getPreconditionIfMatch());
                assertEquals("ABCDEFG", ctx.getPreconditionIfMatch());
                return Result.SUCCESS;
            }
        };

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        // EntityResource without Etag
        EntityResource<Object> er = new EntityResource<Object>("test resource");

        // Apply If-Match header
        HttpHeaders httpHeaders = mock(HttpHeaders.class);
        doAnswer(new Answer<List<String>>() {
            @SuppressWarnings("serial")
            @Override
            public List<String> answer(InvocationOnMock invocation) throws Throwable {
                String headerName = (String) invocation.getArguments()[0];
                if (headerName.equals(HttpHeaders.IF_MATCH)) {
                    return new ArrayList<String>() {
                        {
                            add("ABCDEFG");
                        }
                    };
                }
                return null;
            }
        }).when(httpHeaders).getRequestHeader(any(String.class));

        // execute
        rim.put(httpHeaders, "id", uriInfo, er);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testPutCommandWithEtag() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        initialState.addTransition(new Transition.Builder().method("PUT").target(initialState).build());

        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                assertNotNull(ctx.getResource());
                assertNotNull(ctx.getPreconditionIfMatch());
                assertEquals("ABCDEFG", ctx.getPreconditionIfMatch());
                return Result.SUCCESS;
            }
        };

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        // EntityResource with Etag - etag is a response header and should not
        // be used on requests
        EntityResource<Object> er = new EntityResource<Object>("test resource");
        er.setEntityTag("IJKLMNO"); // This should not override the If-Match
                                    // header

        // Apply If-Match header
        HttpHeaders httpHeaders = mock(HttpHeaders.class);
        doAnswer(new Answer<List<String>>() {
            @SuppressWarnings("serial")
            @Override
            public List<String> answer(InvocationOnMock invocation) throws Throwable {
                String headerName = (String) invocation.getArguments()[0];
                if (headerName.equals(HttpHeaders.IF_MATCH)) {
                    return new ArrayList<String>() {
                        {
                            add("ABCDEFG");
                        }
                    };
                }
                return null;
            }
        }).when(httpHeaders).getRequestHeader(any(String.class));

        // execute
        rim.put(httpHeaders, "id", uriInfo, er);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testDeleteCommandWithIfMatchHeader() throws InteractionException {
        ResourceState initialState = new ResourceState("entity", "state", mockActions(), "/test");
        initialState.addTransition(new Transition.Builder().method("DELETE").target(initialState).build());

        // this test incorrectly supplies a resource as a result of the command.
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                assertNotNull(ctx.getResource());
                assertNotNull(ctx.getPreconditionIfMatch());
                assertNull(ctx.getResource().getEntityTag());
                assertEquals("ABCDEFG", ctx.getPreconditionIfMatch());
                return Result.SUCCESS;
            }
        };

        // RIM with command controller that issues commands that always return
        // SUCCESS
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mockCommandController(mockCommand), new ResourceStateMachine(
                initialState), createMockMetadata());

        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));

        // Apply If-Match header
        HttpHeaders httpHeaders = mock(HttpHeaders.class);
        doAnswer(new Answer<List<String>>() {
            @SuppressWarnings("serial")
            @Override
            public List<String> answer(InvocationOnMock invocation) throws Throwable {
                String headerName = (String) invocation.getArguments()[0];
                if (headerName.equals(HttpHeaders.IF_MATCH)) {
                    return new ArrayList<String>() {
                        {
                            add("ABCDEFG");
                        }
                    };
                }
                return null;
            }
        }).when(httpHeaders).getRequestHeader(any(String.class));

        // execute
        rim.put(httpHeaders, "id", uriInfo, null); // resource is null
    }
    
    @Test
    @SuppressWarnings({ "unchecked" })
    public void testDoubleAutotransitionResolution() throws InteractionException{
        //construct resource states
        ResourceState initialState = new ResourceState("entity", "state", 
                Arrays.asList(new Action[]{new Action("GET", Action.TYPE.VIEW)}), "/test"),
            nextState = new ResourceState("next", "nextState", 
                        Arrays.asList(new Action[]{new Action("GET", Action.TYPE.VIEW)}), "/nextState"),
            postState = new ResourceState("entity", "state_unsafe", 
                        Arrays.asList(new Action[]{new Action("POST", Action.TYPE.ENTRY)}), "/test_unsafe");
        
        //build transitions between resource states
        initialState.addTransition(new Transition.Builder().flags(2).target(nextState).build());
        nextState.addTransition(new Transition.Builder().flags(2).target(postState).build());
        //fake an InteractionCommand that always returns SUCCESS and spy 
        InteractionCommand mockCommand = new InteractionCommand() {
            public Result execute(InteractionContext ctx) {
                ctx.setResource(new EntityResource<Object>());
                return Result.SUCCESS;
            }
        };
        mockCommand = spy(mockCommand);

        //create mock command controller and return the faked InteractionCommand for every command we issue
        CommandController mockCommandController = mock(CommandController.class);
        when(mockCommandController.fetchCommand(anyString())).thenReturn(mockCommand);
        
        //instantiate the class under test using the command controller that we created
        HTTPHypermediaRIM rim = spy(new HTTPHypermediaRIM(mockCommandController, new ResourceStateMachine(
                initialState), createMockMetadata()));
        
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        doAnswer(new Answer<ResponseBuilder>(){
            @Override
            public ResponseBuilder answer(InvocationOnMock invocation) throws Throwable {
                return (ResponseBuilder)invocation.getArguments()[0];
            }
        }).when(rim).setLocationHeader(any(ResponseBuilder.class), anyString(), any(MultivaluedMap.class));
        
        //execute the request and verify that we have executed InteractionCommand 
        //as many times as the number of ResourceState objects that are participating
        Response response = rim.get(mock(HttpHeaders.class), "id", uriInfo);
        assertThat(response.getStatus(), equalTo(200));
        verify(mockCommand, times(3)).execute(any(InteractionContext.class));
        verify(rim, times(1)).setLocationHeader(any(ResponseBuilder.class), eq("http://localhost/myservice.svc/test_unsafe"), any(MultivaluedMap.class));
    }

    @Test
    public void testFilterParameters() {
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mock(CommandController.class), mockResourceStateMachine(), createMockMetadata());
        MultivaluedMap<String, String> params = new MultivaluedMapImpl<>();
        params.putSingle("k1", "v1");
        params.putSingle("k2", "v2");
        params.putSingle("k3", "v3");
        params.putSingle("k4", "v4");
        Set<String> filters = new HashSet<>();
        filters.add("k2");
        filters.add("k4");

        MultivaluedMap<String, String> results = rim.filterParameters(params, filters);

        assertEquals(2, results.size());
        assertEquals(1, results.get("k2").size());
        assertEquals(1, results.get("k4").size());
        assertEquals("v2", results.get("k2").get(0));
        assertEquals("v4", results.get("k4").get(0));
    }

    @Test
    public void testGetStateParameters() {
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mock(CommandController.class), mockResourceStateMachine(), createMockMetadata());
        ResourceStateAndParameters stateAndParams = new ResourceStateAndParameters();
        stateAndParams.setParams(new ParameterAndValue[] {new ParameterAndValue("k1", "v1"), new ParameterAndValue("k2", "v2")});

        MultivaluedMap<String, String> results = rim.getStateParameters(stateAndParams);

        assertEquals(2, results.size());
        assertEquals(1, results.get("k1").size());
        assertEquals(1, results.get("k2").size());
        assertEquals("v1", results.get("k1").get(0));
        assertEquals("v2", results.get("k2").get(0));
    }

    @Test
    public void testBuildPathParameters() {
        Map<String, Object> transitionProperties = new HashMap<>();
        transitionProperties.put("tpk1", "tpv1");
        transitionProperties.put("tpk2", "tpv2");
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mock(CommandController.class), mockResourceStateMachine(transitionProperties), createMockMetadata());
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<>();
        pathParameters.putSingle("pk1", "pv1");
        InteractionContext ctxMock = mockInteractionContext(pathParameters, null);

        MultivaluedMap<String, String> results = rim.buildPathParameters(mock(Transition.class), ctxMock);

        assertEquals(3, results.size());
        assertEquals(1, results.get("tpk1").size());
        assertEquals(1, results.get("tpk2").size());
        assertEquals(1, results.get("pk1").size());
        assertEquals("tpv1", results.get("tpk1").get(0));
        assertEquals("tpv2", results.get("tpk2").get(0));
        assertEquals("pv1", results.get("pk1").get(0));
    }

    @Test
    public void testCopyParameters() {
        HTTPHypermediaRIM rim = new HTTPHypermediaRIM(mock(CommandController.class), mockResourceStateMachine(), createMockMetadata());
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<>();
        pathParameters.putSingle("pk1", "pv1");

        MultivaluedMap<String, String> results = rim.copyParameters(pathParameters);

        assertEquals(1, results.size());
        assertEquals(1, results.get("pk1").size());
        assertEquals("pv1", results.get("pk1").get(0));
        assertTrue(results != pathParameters);
    }

    @SuppressWarnings({ "unchecked" })
    private UriInfo mockEmptyUriInfo() {
        UriInfo uriInfo = mock(UriInfo.class);
        when(uriInfo.getPathParameters(anyBoolean())).thenReturn(mock(MultivaluedMap.class));
        when(uriInfo.getQueryParameters(false)).thenReturn(mock(MultivaluedMap.class));
        return uriInfo;
    }

    private InteractionContext mockInteractionContext(MultivaluedMap<String, String> pathParameters, MultivaluedMap<String, String> queryParameters) {
        InteractionContext ctxMock = mock(InteractionContext.class);
        when(ctxMock.getPathParameters()).thenReturn(pathParameters);
        when(ctxMock.getQueryParameters()).thenReturn(queryParameters);
        when(ctxMock.getResource()).thenReturn(new EntityResource<>());
        return ctxMock;
    }

    private ResourceStateMachine mockResourceStateMachine() {
        return mockResourceStateMachine(new HashMap<String, Object>());
    }

    private ResourceStateMachine mockResourceStateMachine(Map<String, Object> transitionProperties) {
        ResourceStateMachine resourceStateMachineMock = mock(ResourceStateMachine.class);
        when(resourceStateMachineMock.getInitial()).thenReturn(new ResourceState("entity", "state", mockActions(), "/test"));
        when(resourceStateMachineMock.getTransitionProperties(any(Transition.class), any(), any(MultivaluedMap.class), any(MultivaluedMap.class))).thenReturn(transitionProperties);
        return resourceStateMachineMock;
    }

    private Metadata createMockMetadata() {
        Metadata metadata = mock(Metadata.class);
        when(metadata.getEntityMetadata(any(String.class))).thenReturn(mock(EntityMetadata.class));
        return metadata;
    }

}
