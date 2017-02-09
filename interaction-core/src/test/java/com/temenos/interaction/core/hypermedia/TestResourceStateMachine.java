package com.temenos.interaction.core.hypermedia;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

import org.junit.Before;
import org.junit.Test;
import org.odata4j.core.OCollection;
import org.odata4j.core.OCollections;
import org.odata4j.core.OComplexObject;
import org.odata4j.core.OComplexObjects;
import org.odata4j.core.OEntities;
import org.odata4j.core.OEntity;
import org.odata4j.core.OProperties;
import org.odata4j.core.OProperty;
import org.odata4j.edm.EdmComplexType;
import org.odata4j.edm.EdmEntitySet;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.CommandController;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionCommand.Result;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.MapBasedCommandController;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityMetadata;
import com.temenos.interaction.core.entity.EntityProperties;
import com.temenos.interaction.core.entity.EntityProperty;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.Action.TYPE;
import com.temenos.interaction.core.hypermedia.expression.Expression;
import com.temenos.interaction.core.hypermedia.expression.ResourceGETExpression;
import com.temenos.interaction.core.hypermedia.expression.ResourceGETExpression.Function;
import com.temenos.interaction.core.hypermedia.expression.SimpleLogicalExpressionEvaluator;
import com.temenos.interaction.core.hypermedia.validation.HypermediaValidator;
import com.temenos.interaction.core.resource.CollectionResource;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.RESTResource;
import com.temenos.interaction.core.rim.HTTPHypermediaRIM;
import com.temenos.interaction.core.web.RequestContext;


public class TestResourceStateMachine {

    @Before
    public void setup() {
        // initialise the thread local request context with requestUri and baseUri
        RequestContext ctx = new RequestContext("/baseuri", "/requesturi", null);
        RequestContext.setRequestContext(ctx);
    }

    private HTTPHypermediaRIM mockRIMHandler(ResourceStateMachine rsm) {
        CommandController mockCommandController = mockCommandController();
        Metadata mockMetadata = mock(Metadata.class);
        when(mockMetadata.getEntityMetadata(anyString())).thenReturn(mock(EntityMetadata.class));
        HTTPHypermediaRIM rimHandler = new HTTPHypermediaRIM(mockCommandController, rsm, mockMetadata);
        return rimHandler;
    }

    /*
     * Evaluate custom link relation, via the Link header. See (see rfc5988) We return a Link if the header is set and
     * the @{link Transition} can be found.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLinkForCustomLinkRelation() {
        ResourceState existsState = new ResourceState("toaster", "exists", new ArrayList<Action>(), "/machines/toaster");
        ResourceState cookingState = new ResourceState("toaster", "cooking", new ArrayList<Action>(), "/machines/toaster/cooking");

        // view the resource if the toaster is cooking (could be time remaining)
        existsState.addTransition(new Transition.Builder().method("GET").target(cookingState).build());
        // stop the toast cooking
        cookingState.addTransition(new Transition.Builder().method("DELETE").target(existsState).build());
        // the entity for linkage mapping
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
        // initialise application state
        ResourceStateMachine stateMachine = new ResourceStateMachine(existsState);

        // mock the Link header
        LinkHeader linkHeader = LinkHeader.valueOf("</path>; rel=\"toaster.cooking>DELETE>toaster.exists\"");

        Link targetLink = stateMachine.getLinkFromRelations(mock(MultivaluedMap.class), testResponseEntity, linkHeader);

        assertNotNull(targetLink);
        assertEquals("/baseuri/machines/toaster", targetLink.getHref());
        assertEquals("toaster.cooking>DELETE>toaster.exists", targetLink.getId());
    }

    /*
     * We return a Link if a @{link Transition} for supplied method can be found.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLinkForTargetState() {
        ResourceState existsState = new ResourceState("toaster", "exists", new ArrayList<Action>(), "/machines/toaster");
        ResourceState cookingState = new ResourceState("toaster", "cooking", new ArrayList<Action>(), "/machines/toaster/cooking");

        // view the resource if the toaster is cooking (could be time remaining)
        existsState.addTransition(new Transition.Builder().method("GET").target(cookingState).build());
        // stop the toast cooking
        cookingState.addTransition(new Transition.Builder().method("DELETE").target(existsState).build());
        // the entity for linkage mapping
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
        // initialise application state
        ResourceStateMachine stateMachine = new ResourceStateMachine(existsState);

        Link targetLink = stateMachine.getLinkFromMethod(mock(MultivaluedMap.class), testResponseEntity, cookingState, "DELETE");

        assertNotNull(targetLink);
        assertEquals("/baseuri/machines/toaster", targetLink.getHref());
        assertEquals("toaster.cooking>DELETE>toaster.exists", targetLink.getId());
    }

    /*
     * We return a Link if a @{link Transition} for supplied method can be found.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLinkForSelfState() {
        CollectionResourceState collectionState = new CollectionResourceState("machines", "MachineView", new ArrayList<Action>(), "/machines");

        // create machines
        collectionState.addTransition(new Transition.Builder().method("POST").target(collectionState).build());
        // the entity for linkage mapping
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
        // initialise application state
        ResourceStateMachine stateMachine = new ResourceStateMachine(collectionState);

        Link targetLink = stateMachine.getLinkFromMethod(mock(MultivaluedMap.class), testResponseEntity, collectionState, "POST");

        assertNotNull(targetLink);
        // a target link the same as our current state equates to 205 Reset Content
        assertEquals("/baseuri/machines", targetLink.getHref());
        // we use createSelfLink under the covers so link id looks like transition to self
        assertEquals("machines.MachineView>POST>machines.MachineView", targetLink.getId());
    }

    /*
     * We return a Link if a @{link Transition} for supplied method can be found. When the target state is a pseudo
     * final state, no link will be returned.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLinkForFinalPseudoState() {
        ResourceState existsState = new ResourceState("toaster", "exists", new ArrayList<Action>(), "/machines/toaster/{id}");
        ResourceState deletedState = new ResourceState(existsState, "deleted", new ArrayList<Action>());

        // delete the toaster
        existsState.addTransition(new Transition.Builder().method("DELETE").target(deletedState).build());
        // the entity for linkage mapping
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);
        // initialise application state
        ResourceStateMachine stateMachine = new ResourceStateMachine(existsState);

        Link targetLink = stateMachine.getLinkFromMethod(mock(MultivaluedMap.class), testResponseEntity, existsState, "DELETE");

        // no target link equates to 204 No Content
        assertNull(targetLink);
    }

    @Test
    public void testStates() {
        String ENTITY_NAME = "T24CONTRACT";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "{id}");
        ResourceState unauthorised = new ResourceState(ENTITY_NAME, "INAU", new ArrayList<Action>(), "unauthorised/{id}");
        ResourceState authorised = new ResourceState(ENTITY_NAME, "LIVE", new ArrayList<Action>(), "authorised/{id}");
        ResourceState reversed = new ResourceState(ENTITY_NAME, "RNAU", new ArrayList<Action>(), "reversed/{id}");
        ResourceState history = new ResourceState(ENTITY_NAME, "REVE", new ArrayList<Action>(), "history/{id}");
        ResourceState end = new ResourceState(ENTITY_NAME, "end", new ArrayList<Action>(), "{id}");

        begin.addTransition(new Transition.Builder().method("PUT").target(unauthorised).build());

        unauthorised.addTransition(new Transition.Builder().method("PUT").target(unauthorised).build());
        unauthorised.addTransition(new Transition.Builder().method("PUT").target(authorised).build());
        unauthorised.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        authorised.addTransition(new Transition.Builder().method("PUT").target(history).build());

        history.addTransition(new Transition.Builder().method("PUT").target(reversed).build());

        ResourceStateMachine sm = new ResourceStateMachine(begin);
        assertEquals(6, sm.getStates().size());

        Set<ResourceState> testStates = new HashSet<ResourceState>();
        testStates.add(begin);
        testStates.add(unauthorised);
        testStates.add(authorised);
        testStates.add(reversed);
        testStates.add(history);
        testStates.add(end);
        for (ResourceState s : testStates) {
            assertTrue(sm.getStates().contains(s));
        }

        Metadata metadata = new Metadata("");
        metadata.setEntityMetadata(new EntityMetadata("T24CONTRACT"));
        System.out.println(HypermediaValidator.createValidator(sm, metadata).graph());
    }

    /**
     * Test {@link ResourceStateMachine#getStates() should return all states.}
     */
    @Test
    public void testGetStates() {
        String ENTITY_NAME = "";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "{id}");
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "{id}");
        ResourceState end = new ResourceState(exists, "end", new ArrayList<Action>());

        begin.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        ResourceStateMachine sm = new ResourceStateMachine(begin);
        Collection<ResourceState> states = sm.getStates();
        assertEquals("Number of states", 3, states.size());
        assertTrue(states.contains(begin));
        assertTrue(states.contains(exists));
        assertTrue(states.contains(end));
    }

    @Test
    public void testGetStatesWithStateNameCollisions() {
        String ENTITY_NAME_1 = "Person";
        String ENTITY_NAME_2 = "Note";

        ResourceState state_1 = new ResourceState(ENTITY_NAME_1, "state", new ArrayList<Action>(), "{id}");
        ResourceState state_2 = new ResourceState(ENTITY_NAME_2, "state", new ArrayList<Action>(), "{id}");

        state_1.addTransition(new Transition.Builder().method("PUT").target(state_2).build());

        ResourceStateMachine sm = new ResourceStateMachine(state_1);

        // there should only be ONE state, which one is undefined
        Collection<ResourceState> states = sm.getStates();
        assertEquals("Number of states", 1, states.size());
    }

    @Test
    public void testGetTransitions() {
        String ENTITY_NAME = "";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "{id}");
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "{id}");
        ResourceState end = new ResourceState(exists, "end", new ArrayList<Action>());

        begin.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        ResourceStateMachine sm = new ResourceStateMachine(begin);
        Map<String, Transition> transitions = sm.getTransitionsById();
        assertEquals("Number of transitions", 3, transitions.size());
        assertNotNull(transitions.get(".begin>PUT>.exists"));
        assertNotNull(transitions.get(".exists>PUT>.exists"));
        assertNotNull(transitions.get(".exists>DELETE>.end"));
    }

    @Test
    public void testInteractionByPath() {
        String ENTITY_NAME = "";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "{id}");
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "{id}");
        ResourceState end = new ResourceState(ENTITY_NAME, "end", new ArrayList<Action>(), "{id}");

        begin.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        ResourceStateMachine sm = new ResourceStateMachine(begin);

        Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
        assertEquals("Number of resources", 1, interactionMap.size());
        Set<String> entrySet = interactionMap.keySet();
        assertTrue(entrySet.contains("{id}"));
        Collection<String> interactions = interactionMap.get("{id}");
        assertEquals("Number of interactions", 3, interactions.size());
        assertTrue(interactions.contains("GET"));
        assertTrue(interactions.contains("PUT"));
        assertTrue(interactions.contains("DELETE"));
    }

    @Test
    public void testInteractionByPathSingle() {
        String ENTITY_NAME = "";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "/root");
        begin.addTransition(new Transition.Builder().method("GET").target(begin).build());

        ResourceStateMachine sm = new ResourceStateMachine(begin);

        Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
        assertEquals("Number of resources", 1, interactionMap.size());
        Set<String> entrySet = interactionMap.keySet();
        assertTrue(entrySet.contains("/root"));
        Collection<String> interactions = interactionMap.get("/root");
        assertEquals("Number of interactions", 1, interactions.size());
        assertTrue(interactions.contains("GET"));

    }

    @Test
    public void testInteractionByPathPsuedo() {
        String ENTITY_NAME = "";
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "{id}");
        ResourceState end = new ResourceState(exists, "end", new ArrayList<Action>());

        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        ResourceStateMachine sm = new ResourceStateMachine(exists);

        Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
        assertEquals("Number of resources", 1, interactionMap.size());
        Set<String> entrySet = interactionMap.keySet();
        assertTrue(entrySet.contains("{id}"));
        Collection<String> interactions = interactionMap.get("{id}");
        assertEquals("Number of interactions", 3, interactions.size());
        assertTrue(interactions.contains("GET"));
        assertTrue(interactions.contains("PUT"));
        assertTrue(interactions.contains("DELETE"));
    }

    @Test
    public void testInteractionByPathUriLinkage() {
        String ENTITY_NAME = "";
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "/test({id})");
        ResourceState end = new ResourceState(exists, "end", new ArrayList<Action>());

        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("id", "entityPropertyToUse");
        exists.addTransition(new Transition.Builder().method("PUT").target(exists).uriParameters(uriLinkageMap).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        ResourceStateMachine sm = new ResourceStateMachine(exists);

        Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
        assertEquals("Number of resources", 1, interactionMap.size());
        Set<String> entrySet = interactionMap.keySet();
        assertTrue(entrySet.contains("/test({id})"));
        Collection<String> interactions = interactionMap.get("/test({id})");
        assertEquals("Number of interactions", 3, interactions.size());
        assertTrue(interactions.contains("GET"));
        assertTrue(interactions.contains("PUT"));
        assertTrue(interactions.contains("DELETE"));
    }

    @Test
    public void testInteractionByPathTransient() {
        String ENTITY_NAME = "";
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "{id}");
        ResourceState deleted = new ResourceState(ENTITY_NAME, "end", new ArrayList<Action>(), "{id}");

        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(deleted).build());
        // auto transition
        deleted.addTransition(new Transition.Builder().flags(Transition.AUTO).target(exists).build());

        ResourceStateMachine sm = new ResourceStateMachine(exists);

        Map<String, Set<String>> interactionMap = sm.getInteractionByPath();
        assertEquals("Number of resources", 1, interactionMap.size());
        Set<String> entrySet = interactionMap.keySet();
        assertTrue(entrySet.contains("{id}"));
        Collection<String> interactions = interactionMap.get("{id}");
        assertEquals("Number of interactions", 3, interactions.size());
        assertTrue(interactions.contains("GET"));
        assertTrue(interactions.contains("PUT"));
        assertTrue(interactions.contains("DELETE"));
    }

    @Test
    public void testInteractions() {
        String ENTITY_NAME = "";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "{id}");
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "{id}");
        ResourceState end = new ResourceState(ENTITY_NAME, "end", new ArrayList<Action>(), "{id}");

        begin.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        ResourceStateMachine sm = new ResourceStateMachine(begin);

        Set<String> interactions = sm.getInteractions(begin);
        assertEquals("Number of interactions", 3, interactions.size());
        assertTrue(interactions.contains("GET"));
        assertTrue(interactions.contains("PUT"));
        assertTrue(interactions.contains("DELETE"));
    }

    @Test
    public void testSubstateInteractions() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        ResourceState published = new ResourceState(ENTITY_NAME, "published", new ArrayList<Action>(), "/published");
        ResourceState publishedDeleted = new ResourceState(published, "publishedDeleted", new ArrayList<Action>());
        ResourceState draft = new ResourceState(ENTITY_NAME, "draft", new ArrayList<Action>(), "/draft");
        ResourceState draftDeleted = new ResourceState(draft, "draftDeleted", new ArrayList<Action>());

        // create draft
        initial.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // updated draft
        draft.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // publish
        draft.addTransition(new Transition.Builder().method("PUT").target(published).build());
        // delete draft
        draft.addTransition(new Transition.Builder().method("DELETE").target(draftDeleted).build());
        // delete published
        published.addTransition(new Transition.Builder().method("DELETE").target(publishedDeleted).build());

        ResourceStateMachine sm = new ResourceStateMachine(initial);

        Set<String> initialInteractions = sm.getInteractions(initial);
        assertEquals("Number of interactions", 1, initialInteractions.size());
        assertTrue(initialInteractions.contains("GET"));

        Set<String> draftInteractions = sm.getInteractions(draft);
        assertEquals("Number of interactions", 2, draftInteractions.size());
        assertTrue(draftInteractions.contains("PUT"));
        assertTrue(draftInteractions.contains("DELETE"));

        Set<String> publishInteractions = sm.getInteractions(published);
        assertEquals("Number of interactions", 2, publishInteractions.size());
        assertTrue(publishInteractions.contains("PUT"));
        assertTrue(publishInteractions.contains("DELETE"));

        Set<String> deletedInteractions = sm.getInteractions(draftDeleted);
        assertEquals("Number of interactions", 2, deletedInteractions.size());
        assertTrue(deletedInteractions.contains("PUT"));
        assertTrue(deletedInteractions.contains("DELETE"));

        Set<String> publishedDeletedInteractions = sm.getInteractions(publishedDeleted);
        assertEquals("Number of interactions", 2, publishedDeletedInteractions.size());
        assertTrue(publishedDeletedInteractions.contains("PUT"));
        assertTrue(publishedDeletedInteractions.contains("DELETE"));
    }

    @Test
    public void testStateByPath() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        ResourceState published = new ResourceState(initial, "published", new ArrayList<Action>(), "/published");
        ResourceState publishedDeleted = new ResourceState(published, "publishedDeleted", new ArrayList<Action>());
        ResourceState draft = new ResourceState(initial, "draft", new ArrayList<Action>(), "/draft");
        ResourceState draftDeleted = new ResourceState(draft, "draftDeleted", new ArrayList<Action>());

        // create draft
        initial.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // updated draft
        draft.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // publish
        draft.addTransition(new Transition.Builder().method("PUT").target(published).build());
        // delete draft
        draft.addTransition(new Transition.Builder().method("DELETE").target(draftDeleted).build());
        // delete published
        published.addTransition(new Transition.Builder().method("DELETE").target(publishedDeleted).build());

        ResourceStateMachine sm = new ResourceStateMachine(initial);

        Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath();
        assertEquals("Number of states", 3, stateMap.size());
        Set<String> entrySet = stateMap.keySet();
        assertTrue(entrySet.contains("/entity"));
        assertTrue(entrySet.contains("/entity/published"));
        assertTrue(entrySet.contains("/entity/draft"));
        assertEquals(1, stateMap.get("/entity").size());
        assertEquals(initial, stateMap.get("/entity").iterator().next());
        assertEquals(2, stateMap.get("/entity/published").size());
        assertTrue(stateMap.get("/entity/published").contains(published));
        assertTrue(stateMap.get("/entity/published").contains(publishedDeleted));
        assertEquals(2, stateMap.get("/entity/draft").size());
        assertTrue(stateMap.get("/entity/draft").contains(draft));
        assertTrue(stateMap.get("/entity/draft").contains(draftDeleted));
    }

    @Test
    public void testStateByPathSingle() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        ResourceStateMachine sm = new ResourceStateMachine(initial);
        Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath();
        assertEquals("Number of states", 1, stateMap.size());
        Set<String> entrySet = stateMap.keySet();
        assertTrue(entrySet.contains("/entity"));
    }

    @Test
    public void testStateByPathPseudo() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        ResourceState draft = new ResourceState(initial, "draft", new ArrayList<Action>(), "/draft");
        ResourceState deleted = new ResourceState(initial, "deleted", null);

        // create draft
        initial.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // updated draft
        draft.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // delete draft
        draft.addTransition(new Transition.Builder().method("DELETE").target(deleted).build());
        // delete entity
        initial.addTransition(new Transition.Builder().method("DELETE").target(deleted).build());

        ResourceStateMachine sm = new ResourceStateMachine(initial);

        Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath();
        assertEquals("Number of states", 3, sm.getStates().size());
        assertEquals("Number of real states", 2, stateMap.size());
        Set<String> entrySet = stateMap.keySet();
        assertTrue(entrySet.contains("/entity"));
        assertEquals(2, stateMap.get("/entity").size());
        assertTrue(stateMap.get("/entity").contains(initial));
        assertTrue(stateMap.get("/entity").contains(deleted));
        assertTrue(entrySet.contains("/entity/draft"));
        assertEquals(1, stateMap.get("/entity/draft").size());
        assertTrue(stateMap.get("/entity/draft").contains(draft));
    }

    @Test
    public void testGetResourceStatesByPath() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        ResourceState published = new ResourceState(initial, "published", new ArrayList<Action>(), "/published");
        ResourceState publishedDeleted = new ResourceState(published, "publishedDeleted", new ArrayList<Action>());
        ResourceState draft = new ResourceState(initial, "draft", new ArrayList<Action>(), "/draft");
        ResourceState draftDeleted = new ResourceState(draft, "draftDeleted", new ArrayList<Action>());

        // create draft
        initial.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // updated draft
        draft.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // publish
        draft.addTransition(new Transition.Builder().method("PUT").target(published).build());
        // delete draft
        draft.addTransition(new Transition.Builder().method("DELETE").target(draftDeleted).build());
        // delete published
        published.addTransition(new Transition.Builder().method("DELETE").target(publishedDeleted).build());

        ResourceStateMachine sm = new ResourceStateMachine(initial);

        Map<String, Set<ResourceState>> stateMap = sm.getResourceStatesByPath(draft);
        assertEquals("Number of states", 3, stateMap.size());
        Set<String> entrySet = stateMap.keySet();
        assertTrue(entrySet.contains("/entity"));
        assertTrue(entrySet.contains("/entity/published"));
        assertTrue(entrySet.contains("/entity/draft"));
        assertEquals(2, stateMap.get("/entity/published").size());
        assertTrue(stateMap.get("/entity/published").contains(published));
        assertTrue(stateMap.get("/entity/published").contains(publishedDeleted));
        assertEquals(2, stateMap.get("/entity/draft").size());
        assertTrue(stateMap.get("/entity/draft").contains(draft));
        assertTrue(stateMap.get("/entity/draft").contains(draftDeleted));
    }

    @Test
    public void testGetInteractionsByState() {
        String entityName = "Note";
        ResourceState initialState = new ResourceState(entityName, "notes", new ArrayList<Action>(), "/notes");
        initialState.setInitial(true);

        ResourceState noteState = new ResourceState(entityName, "note", new ArrayList<Action>(), "/notes('{id}')");
        initialState.addTransition(new Transition.Builder().method("GET").target(noteState).build());

        ResourceState noteCreateState = new ResourceState(entityName, "note_create", new ArrayList<Action>(), "/note('{id}')/create");
        noteState.addTransition(new Transition.Builder().method("PUT").target(noteCreateState).build());

        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        ResourceState noteDeleteState = new ResourceState(entityName, "note_delete", new ArrayList<Action>(), "/note('{id}')/delete");
        noteState.addTransition(new Transition.Builder().method("DELETE").target(noteDeleteState).build());
        ResourceState noteRestoreState = new ResourceState(entityName, "note_restore", new ArrayList<Action>(), "/note('{id}')/restore");
        noteDeleteState.addTransition(new Transition.Builder().method("POST").target(noteRestoreState).build());
        stateMachine.register(noteDeleteState, "DELETE");

        Map<String, Set<String>> interactionsMap = stateMachine.getInteractionByState();
        assertEquals("Number of interactions", 5, interactionsMap.size());
        // notes interactions
        Set<String> methods = interactionsMap.get("notes");
        assertEquals("Number of methods", 1, methods.size());
        assertTrue(methods.contains("GET"));
        // note interactions
        methods = interactionsMap.get("note");
        assertEquals("Number of methods", 1, methods.size());
        assertTrue(methods.contains("GET"));
        // note_new interactions
        methods = interactionsMap.get("note_create");
        assertEquals("Number of methods", 1, methods.size());
        assertTrue(methods.contains("PUT"));
        // note_delete interactions
        methods = interactionsMap.get("note_delete");
        assertEquals("Number of methods", 1, methods.size());
        assertTrue(methods.contains("DELETE"));
    }

    /*
     * The state machine is built with a default GET interaction for the initial state.
     */
    @Test
    public void testDefaultMethodForInitState() {
        String entityName = "EN";
        ResourceState A = new ResourceState(entityName, "A", new ArrayList<Action>(), "/A");
        ResourceState B = new ResourceState(entityName, "B", new ArrayList<Action>(), "/B");
        ResourceState C = new ResourceState(entityName, "C", new ArrayList<Action>(), "/C");
        B.addTransition(new Transition.Builder().method("POST").target(C).build());

        // initialised with state A
        ResourceStateMachine smInitA = new ResourceStateMachine(A);

        // only the one state is present
        assertEquals("Number of states", 1, smInitA.getStates().size());
        // GET interaction is registered by default
        assertEquals("Number of interactions for A", 1, smInitA.getInteractions(A).size());
        assertTrue("GET interactions for A", smInitA.getInteractions(A).contains("GET"));

        // initialised with state B
        ResourceStateMachine smInitB = new ResourceStateMachine(B);

        // states B and C are present
        assertEquals("Number of states", 2, smInitB.getStates().size());
        // GET interaction is registered by default
        assertEquals("Number of interactions for B", 1, smInitB.getInteractions(B).size());
        assertTrue("GET interactions for B", smInitB.getInteractions(B).contains("GET"));
        // check tha no GET interaction was added for state C
        assertEquals("Number of interactions for C", 1, smInitB.getInteractions(C).size());
        assertTrue("POST interactions for C", smInitB.getInteractions(C).contains("POST"));
    }

    /*
     * Create state machine with transitions: B>POST>A; C>POST>A The pair (A,POST) should be registered only once (the
     * resource state machine doesn't care from which resource it comes from).
     */
    @Test
    public void testRegisterDuplicateMethodForState() {
        String entityName = "EN";
        ResourceState A = new ResourceState(entityName, "A", new ArrayList<Action>(), "/A");
        ResourceState B = new ResourceState(entityName, "B", new ArrayList<Action>(), "/B");
        ResourceState C = new ResourceState(entityName, "C", new ArrayList<Action>(), "/C");

        ResourceStateMachine stateMachine = new ResourceStateMachine(A);

        // only the one state is present
        assertEquals("Number of states", 1, stateMachine.getStates().size());
        // GET interaction is registered by default
        assertEquals("Number of interactions for A", 1, stateMachine.getInteractions(A).size());

        B.addTransition(new Transition.Builder().method("POST").target(A).build());
        C.addTransition(new Transition.Builder().method("POST").target(A).build());

        // B should not be registered
        assertFalse(stateMachine.getResourceStateByName().containsKey(B.getName()));
        // C should not be registered
        assertFalse(stateMachine.getResourceStateByName().containsKey(C.getName()));

        // now register the state with the method
        stateMachine.register(A, "POST");

        // we still have the same number of states
        assertEquals("Number of states", 1, stateMachine.getStates().size());
        assertTrue(stateMachine.getResourceStateByName().containsKey(A.getName()));
        // A can be reached by the default GET method and the registered POST method
        assertEquals("Number of interactions for A", 2, stateMachine.getInteractions(A).size());
    }

    /*
     * Create state machine with transitions: D>GET>B; D>GET>C; B>POST>A; C>POST>A Unregister (A,POST) and check that it
     * is no longer possible to reach A with a POST method.
     */
    @Test
    public void testUnregisterDuplicateMethodForState() {
        String entityName = "EN";
        ResourceState A = new ResourceState(entityName, "A", new ArrayList<Action>(), "/A");
        ResourceState B = new ResourceState(entityName, "B", new ArrayList<Action>(), "/B");
        ResourceState C = new ResourceState(entityName, "C", new ArrayList<Action>(), "/C");
        // we use D to have all states registered by initialising the state machine
        ResourceState D = new ResourceState(entityName, "D", new ArrayList<Action>(), "/D");

        B.addTransition(new Transition.Builder().method("POST").target(A).build());
        C.addTransition(new Transition.Builder().method("POST").target(A).build());
        D.addTransition(new Transition.Builder().method("GET").target(B).build());
        D.addTransition(new Transition.Builder().method("GET").target(B).build());

        ResourceStateMachine stateMachine = new ResourceStateMachine(D);

        // A can be reached by the POST method
        assertEquals("Number of interactions for A", 1, stateMachine.getInteractions(A).size());
        assertTrue("State A can be reached by the POST interaction", stateMachine.getInteractions(A).contains("POST"));

        // now unregistered POST for A
        stateMachine.unregister(A, "POST");

        // state A is not present anymore
        assertFalse(stateMachine.getResourceStateByName().containsKey(A.getName()));
    }

    @Test
    public void testRegisterEmbeddedTransitions() {
        String entityName = "Note";
        ResourceState initialState = new ResourceState(entityName, "notes", new ArrayList<Action>(), "/notes");
        ResourceState noteState = new ResourceState(entityName, "note", new ArrayList<Action>(), "/notes('{id}')");
        initialState.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("GET").target(noteState).build());

        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        assertEquals("Number of states", 2, stateMachine.getStates().size());
        assertEquals("Number of interactions for noteState", 1, stateMachine.getInteractions(noteState).size());
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());

        // add a transition for a new method without registering it
        initialState.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("POST").target(noteState).build());

        // all results should be the same
        assertEquals("Number of states", 2, stateMachine.getStates().size());
        assertEquals("Number of interactions for noteState", 1, stateMachine.getInteractions(noteState).size());
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());

        // now register the state with the new method
        stateMachine.register(noteState, "POST");

        // one interaction should have been added for noteState
        assertEquals("Number of states", 2, stateMachine.getStates().size());
        assertEquals("Number of interactions for noteState", 2, stateMachine.getInteractions(noteState).size());
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());

        // create a state with the same path as noteState
        ResourceState samePathState = new ResourceState(entityName, "noteCopy", new ArrayList<Action>(), "/notes('{id}')");
        samePathState.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("GET").target(noteState).build());

        // no changes YET when getting the resource states by path
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        // only one resource registered under the path
        assertEquals("Number of states under \"/notes('{id}')\" path", 1, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
        // the new state should NOT be found in the resource state machine
        assertFalse(stateMachine.getResourceStateByName().containsKey(samePathState.getName()));

        // now register
        stateMachine.register(samePathState, "GET");

        // check that a new state has been registered
        assertEquals("Number of states", 3, stateMachine.getStates().size());
        assertEquals("Number of interactions for noteState", 2, stateMachine.getInteractions(noteState).size());
        // the path of the latest added state is the same as that of noteState
        assertEquals("Number of states for noteState's path", 2, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        // now two resources registered under the path
        assertEquals("Number of states under \"/notes('{id}')\" path", 2, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
        // check that the new state is found in the resource state machine
        assertTrue(stateMachine.getResourceStateByName().containsKey(samePathState.getName()));
    }

    @Test
    public void testRegisterRegularTransitions() {
        String entityName = "Note";
        ResourceState initialState = new ResourceState(entityName, "notes", new ArrayList<Action>(), "/notes");

        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        // only the initial state is present
        assertEquals("Number of states", 1, stateMachine.getStates().size());
        // GET interaction is registered by default
        assertEquals("Number of interactions for initialStates", 1, stateMachine.getInteractions(initialState).size());
        assertEquals("Number of states for initialState's path", 1, stateMachine.getResourceStatesByPath().get(initialState.getPath()).size());

        ResourceState noteState = new ResourceState(entityName, "note", new ArrayList<Action>(), "/notes('{id}')");
        initialState.addTransition(new Transition.Builder().method("GET").target(noteState).build());

        // noteState should not be registered
        assertFalse(stateMachine.getResourceStateByName().containsKey(noteState.getName()));

        // now register the state with the method
        stateMachine.register(noteState, "GET");

        // noteState is added
        assertEquals("Number of states", 2, stateMachine.getStates().size());
        assertTrue(stateMachine.getResourceStateByName().containsKey(noteState.getName()));
        // check number of interactions
        assertEquals("Number of interactions for initialStates", 1, stateMachine.getInteractions(noteState).size());
        assertEquals("Number of interactions for noteState", 1, stateMachine.getInteractions(noteState).size());
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
    }

    @Test
    public void testRegisterNullState() {
        String entityName = "Note";
        ResourceState initialState = new ResourceState(entityName, "notes", new ArrayList<Action>(), "/notes");

        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        // state machine should contain only one state
        assertEquals("Number of states", 1, stateMachine.getStates().size());
        assertTrue(stateMachine.getResourceStateByName().containsKey(initialState.getName()));

        // try to register a null state
        stateMachine.register(null, "GET");

        // state machine should contain only one state
        assertEquals("Number of states", 1, stateMachine.getStates().size());
        assertTrue(stateMachine.getResourceStateByName().containsKey(initialState.getName()));
    }

    @Test
    public void testRegisterAllStates() {
        String entityName = "Note";
        ResourceState initialState = new ResourceState(entityName, "notes", new ArrayList<Action>(), "/notes");

        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        ResourceState noteState = new ResourceState(entityName, "note", new ArrayList<Action>(), "/notes('{id}')");
        initialState.addTransition(new Transition.Builder().method("GET").target(noteState).build());
        initialState.addTransition(new Transition.Builder().method("POST").target(noteState).build());
        ResourceState samePathState = new ResourceState(entityName, "noteCopy", new ArrayList<Action>(), "/notes('{id}')");
        noteState.addTransition(new Transition.Builder().method("GET").target(samePathState).build());

        // only the initial state is present
        assertEquals("Number of states", 1, stateMachine.getStates().size());
        assertTrue(stateMachine.getResourceStateByName().containsKey(initialState.getName()));

        // register all states starting with initial, BUT violating the function's precondition
        stateMachine.registerAllStartingFromState(initialState, "GET");

        // this shouldn't add any state as initialState/GET is already registered when the state machine is built
        assertEquals("Number of states", 1, stateMachine.getStates().size());

        // first we unregister the initial state
        stateMachine.unregister(initialState, "GET");

        // state machine should be emtpy
        assertEquals("Number of states", 0, stateMachine.getStates().size());

        // register all states starting with initial
        stateMachine.registerAllStartingFromState(initialState, "GET");

        // all states should be registered
        assertEquals("Number of states", 3, stateMachine.getStates().size());
        assertTrue(stateMachine.getResourceStateByName().containsKey(initialState.getName()));
        assertTrue(stateMachine.getResourceStateByName().containsKey(noteState.getName()));
        assertTrue(stateMachine.getResourceStateByName().containsKey(samePathState.getName()));
    }

    @Test
    public void testRegisterAllStatesWithLoops() {
        String ENTITY_NAME = "";
        ResourceState A = new ResourceState(ENTITY_NAME, "A", new ArrayList<Action>(), "{id}");
        ResourceState J = new ResourceState(ENTITY_NAME, "J", new ArrayList<Action>(), "{id}");
        ResourceState E = new ResourceState(ENTITY_NAME, "E", new ArrayList<Action>(), "{id}");
        ResourceState F = new ResourceState(ENTITY_NAME, "F", new ArrayList<Action>(), "{id}");
        ResourceState K = new ResourceState(ENTITY_NAME, "K", new ArrayList<Action>(), "{id}");
        ResourceState B = new ResourceState(ENTITY_NAME, "B", new ArrayList<Action>(), "{id}");

        A.addTransition(new Transition.Builder().method("GET").target(E).build());
        A.addTransition(new Transition.Builder().method("POST").target(J).build());
        J.addTransition(new Transition.Builder().method(null).target(E).build());
        E.addTransition(new Transition.Builder().method("PUT").target(F).build());
        E.addTransition(new Transition.Builder().method("DELETE").target(K).build());
        E.addTransition(new Transition.Builder().method("GET").target(B).build());

        ResourceStateMachine sm = new ResourceStateMachine(A);

        Collection<ResourceState> states = sm.getStates();
        assertEquals("Number of states", 6, states.size());
    }

    @Test
    public void testUnregister() {
        String entityName = "Note";
        ResourceState initialState = new ResourceState(entityName, "notes", new ArrayList<Action>(), "/notes");
        ResourceState noteState = new ResourceState(entityName, "note", new ArrayList<Action>(), "/notes('{id}')");
        initialState.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("GET").target(noteState).build());
        initialState.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("POST").target(noteState).build());
        ResourceState samePathState = new ResourceState(entityName, "noteCopy", new ArrayList<Action>(), "/notes('{id}')");
        noteState.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("GET").target(samePathState).build());

        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        assertEquals("Number of states", 3, stateMachine.getStates().size());
        assertEquals("Number of interactions for noteState", 2, stateMachine.getInteractions(noteState).size());
        assertEquals("Number of states for noteState's path", 2, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        // two resources registered under the path
        assertEquals("Number of states under \"/notes('{id}')\" path", 2, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
        // check that noteState is present in the resource state machine
        assertTrue(stateMachine.getResourceStateByName().containsKey(noteState.getName()));
        assertEquals("Number of interactions for initialState", 1, stateMachine.getInteractions(initialState).size());

        // unregister the POST method
        stateMachine.unregister(noteState, "POST");

        // no changes in the number of states, since noteState still has one method registered
        assertEquals("Number of states", 3, stateMachine.getStates().size());
        assertEquals("Number of interactions for noteState", 1, stateMachine.getInteractions(noteState).size());
        assertEquals("Number of states for noteState's path", 2, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        // two resources registered under the path
        assertEquals("Number of states under \"/notes('{id}')\" path", 2, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
        // check that noteState is STILL present in the resource state machine
        assertTrue(stateMachine.getResourceStateByName().containsKey(noteState.getName()));

        // unregister the GET method for noteState
        stateMachine.unregister(noteState, "GET");

        // noteState should be unregistered now
        assertEquals("Number of states", 2, stateMachine.getStates().size());
        // one resource registered under the path
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        assertEquals("Number of states under \"/notes('{id}')\" path", 1, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
        // check that noteState is NOT present in the resource state machine
        assertFalse(stateMachine.getResourceStateByName().containsKey(noteState.getName()));

        // try to unregister a resource with an inexistent method
        stateMachine.unregister(initialState, "POST");

        // we expect no changes
        assertEquals("Number of states", 2, stateMachine.getStates().size());
        assertEquals("Number of interactions for initialState", 1, stateMachine.getInteractions(initialState).size());
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        assertEquals("Number of states under \"/notes('{id}')\" path", 1, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
        assertTrue(stateMachine.getResourceStateByName().containsKey(initialState.getName()));

        // unregister initial state
        stateMachine.unregister(initialState, "GET");

        // check for changes
        assertEquals("Number of states", 1, stateMachine.getStates().size());
        assertEquals("Number of states for noteState's path", 1, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        assertEquals("Number of states under \"/notes('{id}')\" path", 1, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
        assertFalse(stateMachine.getResourceStateByName().containsKey(initialState.getName()));

        // clean-up
        stateMachine.unregister(samePathState, "GET");

        assertEquals("Number of states", 0, stateMachine.getStates().size());
        assertEquals("Number of states for noteState's path", 0, stateMachine.getResourceStatesByPath().get(noteState.getPath()).size());
        assertEquals("Number of states under \"/notes('{id}')\" path", 0, stateMachine.getResourceStatesForPath("/notes('{id}')").size());
    }

    /*
     * Unregistering a null state shouldn't change the state of the machine
     */
    @Test
    public void testUnregisterNullState() {
        String entityName = "EN";
        ResourceState A = new ResourceState(entityName, "A", new ArrayList<Action>(), "/A");
        ResourceState B = new ResourceState(entityName, "B", new ArrayList<Action>(), "/B");

        A.addTransition(new Transition.Builder().method("POST").target(B).build());

        ResourceStateMachine stateMachine = new ResourceStateMachine(A);

        // A can be reached by the POST method
        assertEquals("Number of states", 2, stateMachine.getStates().size());
        assertEquals("Number of transitions by id", 1, stateMachine.getTransitionsById().size());
        assertEquals("Number of interactions by path", 2, stateMachine.getInteractionByPath().size());
        assertEquals("Number of interactions by path A", 1, stateMachine.getInteractionByPath().get(A.getPath()).size());
        assertEquals("Number of interactions by path B", 1, stateMachine.getInteractionByPath().get(B.getPath()).size());
        assertEquals("Number of interactions by state", 2, stateMachine.getInteractionByState().size());
        assertEquals("Number of interactions for state A", 1, stateMachine.getInteractionByState().get(A.getName()).size());
        assertEquals("Number of interactions for state B", 1, stateMachine.getInteractionByState().get(B.getName()).size());

        // try to unregister a null state
        stateMachine.unregister(null, "GET");

        // no changes should be detected
        assertEquals("Number of states", 2, stateMachine.getStates().size());
        assertEquals("Number of transitions by id", 1, stateMachine.getTransitionsById().size());
        assertEquals("Number of interactions by path", 2, stateMachine.getInteractionByPath().size());
        assertEquals("Number of interactions by path A", 1, stateMachine.getInteractionByPath().get(A.getPath()).size());
        assertEquals("Number of interactions by path B", 1, stateMachine.getInteractionByPath().get(B.getPath()).size());
        assertEquals("Number of interactions by state", 2, stateMachine.getInteractionByState().size());
        assertEquals("Number of interactions for state A", 1, stateMachine.getInteractionByState().get(A.getName()).size());
        assertEquals("Number of interactions for state B", 1, stateMachine.getInteractionByState().get(B.getName()).size());
    }

    @Test
    public void testGetResourceStatesByPathRegex() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/notes");
        ResourceState notesRegex = new ResourceState(ENTITY_NAME, "notesRegex", new ArrayList<Action>(), "/notes()");
        ResourceState notesEntity = new ResourceState(ENTITY_NAME, "notesEntity", new ArrayList<Action>(), "/notes({id})");
        ResourceState notesEntityQuoted = new ResourceState(ENTITY_NAME, "notesEntityQuoted", new ArrayList<Action>(), "/notes('{id}')");
        ResourceState notesNavProperty = new ResourceState(ENTITY_NAME, "notesNavProperty", new ArrayList<Action>(), "/notes({id})/{navproperty}");
        ResourceState duffnotes = new ResourceState(ENTITY_NAME, "duffnotes", new ArrayList<Action>(), "/duff/notes");

        // create transitions
        initial.addTransition(new Transition.Builder().method("GET").target(notesRegex).build());
        initial.addTransition(new Transition.Builder().method("GET").target(notesEntity).build());
        initial.addTransition(new Transition.Builder().method("GET").target(notesEntityQuoted).build());
        initial.addTransition(new Transition.Builder().method("GET").target(notesNavProperty).build());
        initial.addTransition(new Transition.Builder().method("GET").target(duffnotes).build());

        ResourceStateMachine sm = new ResourceStateMachine(initial);

        assertEquals("Number of states: initial", 1, sm.getResourceStatesForPathRegex("/notes").size());
        assertEquals("Number of states: notesRegex", 1, sm.getResourceStatesForPathRegex("/notes()").size());
        assertEquals("Number of states: initial", 1, sm.getResourceStatesForPathRegex("^/notes").size());
        assertEquals("Number of states: notesRegex", 1, sm.getResourceStatesForPathRegex("^/notes(\\(\\))").size());
        assertEquals("Number of states: initial, notesRegex", 2, sm.getResourceStatesForPathRegex("^/notes(|\\(\\))").size());
        assertEquals("Number of states: notesEntity", 1, sm.getResourceStatesForPathRegex("^/notes(|[\\(.\\)])").size());
        assertEquals("Number of states: initial, notesRegex, notesEntityQuoted, and notesEntity", 4, sm.getResourceStatesForPathRegex("^/notes(|\\(.*\\))").size());
        assertEquals("Number of states: initial, duffnotes", 2, sm.getResourceStatesForPathRegex(".*notes").size());
        assertEquals("Number of states: all", 6, sm.getResourceStatesForPathRegex(".*notes.*").size());
    }

    @Test
    public void testGetState() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        ResourceState published = new ResourceState(initial, "published", new ArrayList<Action>(), "/published");
        ResourceState draft = new ResourceState(initial, "draft", new ArrayList<Action>(), "/draft");
        ResourceState deleted = new ResourceState(initial, "deleted", new ArrayList<Action>());

        // create draft
        initial.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // updated draft
        draft.addTransition(new Transition.Builder().method("PUT").target(draft).build());
        // publish
        draft.addTransition(new Transition.Builder().method("PUT").target(published).build());
        // delete draft
        draft.addTransition(new Transition.Builder().method("DELETE").target(deleted).build());
        // delete published
        published.addTransition(new Transition.Builder().method("DELETE").target(deleted).build());

        ResourceStateMachine sm = new ResourceStateMachine(initial);

        assertEquals(2, sm.getResourceStatesForPath(null).size());
        assertTrue(sm.getResourceStatesForPath(null).contains(initial));
        assertTrue(sm.getResourceStatesForPath(null).contains(deleted));
        assertEquals(1, sm.getResourceStatesForPath("/entity/published").size());
        assertTrue(sm.getResourceStatesForPath("/entity/published").contains(published));
        assertEquals(1, sm.getResourceStatesForPath("/entity/draft").size());
        assertTrue(sm.getResourceStatesForPath("/entity/draft").contains(draft));
    }

    @Test(expected = AssertionError.class)
    public void testInteractionsInvalidState() {

        String ENTITY_NAME = "";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "{id}");
        ResourceState exists = new ResourceState(ENTITY_NAME, "exists", new ArrayList<Action>(), "{id}");
        ResourceState end = new ResourceState(ENTITY_NAME, "end", new ArrayList<Action>(), "{id}");

        begin.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("PUT").target(exists).build());
        exists.addTransition(new Transition.Builder().method("DELETE").target(end).build());

        ResourceStateMachine sm = new ResourceStateMachine(begin);

        ResourceState other = new ResourceState("other", "initial", new ArrayList<Action>(), "/other");
        sm.getInteractions(other);
    }

    @Test
    public void testInteractionsFromDifferentStatesWithSameMethod() {

        String ENTITY_NAME = "";
        ResourceState begin = new ResourceState(ENTITY_NAME, "begin", new ArrayList<Action>(), "{id}");
        ResourceState source1 = new ResourceState(ENTITY_NAME, "source1", new ArrayList<Action>(), "{id}");
        ResourceState source2 = new ResourceState(ENTITY_NAME, "source2", new ArrayList<Action>(), "{id}");

        begin.addTransition(new Transition.Builder().method(null).target(source1).build());
        begin.addTransition(new Transition.Builder().method(null).target(source2).build());
        source1.addTransition(new Transition.Builder().method(null).target(source2).build());

        ResourceStateMachine stateMachine = new ResourceStateMachine(begin);

        // all results should be the same
        assertEquals("Number of states", 3, stateMachine.getStates().size());
        assertEquals("Number of interactions for begin", 1, stateMachine.getInteractions(begin).size());
        assertEquals("Number of interactions for source1", 1, stateMachine.getInteractions(source1).size());
        assertEquals("Number of interactions for source2", 1, stateMachine.getInteractions(source2).size());
    }

    @Test
    public void testTransitionToStateMachine() {
        String PROCESS_ENTITY_NAME = "process";
        String TASK_ENTITY_NAME = "task";

        // process behaviour
        ResourceState processes = new ResourceState(PROCESS_ENTITY_NAME, "processes", new ArrayList<Action>(), "/processes");
        ResourceState newProcess = new ResourceState(PROCESS_ENTITY_NAME, "new", new ArrayList<Action>(), "/new");
        // create new process
        processes.addTransition(new Transition.Builder().method("POST").target(newProcess).build());

        // Process states
        ResourceState processInitial = new ResourceState(PROCESS_ENTITY_NAME, "initialProcess", new ArrayList<Action>(), "/processes/{id}");
        ResourceState processStarted = new ResourceState(processInitial, "started", new ArrayList<Action>());
        ResourceState nextTask = new ResourceState(PROCESS_ENTITY_NAME, "taskAvailable", new ArrayList<Action>(), "/nextTask");
        ResourceState processCompleted = new ResourceState(processInitial, "completedProcess", new ArrayList<Action>());
        // start new process
        newProcess.addTransition(new Transition.Builder().method("PUT").target(processInitial).build());
        processInitial.addTransition(new Transition.Builder().method("PUT").target(processStarted).build());
        // do a task
        processStarted.addTransition(new Transition.Builder().method("GET").target(nextTask).build());
        // finish the process
        processStarted.addTransition(new Transition.Builder().method("DELETE").target(processCompleted).build());

        ResourceStateMachine processSM = new ResourceStateMachine(processes);

        // Task states
        ResourceState taskAcquired = new ResourceState(TASK_ENTITY_NAME, "acquired", new ArrayList<Action>(), "/acquired");
        ResourceState taskComplete = new ResourceState(TASK_ENTITY_NAME, "complete", new ArrayList<Action>(), "/completed");
        ResourceState taskAbandoned = new ResourceState(taskAcquired, "abandoned", new ArrayList<Action>());
        // abandon task
        taskAcquired.addTransition(new Transition.Builder().method("DELETE").target(taskAbandoned).build());
        // complete task
        taskAcquired.addTransition(new Transition.Builder().method("PUT").target(taskComplete).build());

        ResourceStateMachine taskSM = new ResourceStateMachine(taskAcquired);
        /*
         * acquire task by a PUT to the initial state of the task state machine (acquired)
         */
        nextTask.addTransition("PUT", taskSM);

        ResourceState home = new ResourceState("", "home", new ArrayList<Action>(), "/");
        home.addTransition("GET", processSM);
        ResourceStateMachine serviceDocumentSM = new ResourceStateMachine(home);

        Map<String, Set<String>> interactionMap = serviceDocumentSM.getInteractionByPath();
        assertEquals(7, interactionMap.size());

        // all target states, including states not for this entity (application states)
        Collection<ResourceState> targetStates = serviceDocumentSM.getInitial().getAllTargets();
        assertEquals(1, targetStates.size());
        assertEquals(10, serviceDocumentSM.getStates().size());
    }

    @SuppressWarnings("unchecked")
    private InteractionContext createMockInteractionContext(ResourceState state) {
        return new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), mock(MultivaluedMap.class), mock(MultivaluedMap.class), state, mock(Metadata.class));
    }

    /*
     * Test we do not return any links if our entity is null (not found).
     */
    @Test
    public void testGetLinksEntityNotFound() {
        ResourceState initial = new ResourceState("NOTE", "initial", new ArrayList<Action>(), "/note/{id}");

        // the null entity for our test
        EntityResource<Object> testResponseEntity = null;

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> links = stateMachine.injectLinks(rimHandler, createMockInteractionContext(initial), testResponseEntity, headers, metadata);
        assertNotNull(links);
        assertTrue(links.isEmpty());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the link to 'self'
     * correctly for our test resource.
     */
    @Test
    public void testGetLinksSelf() {
        String ENTITY_NAME = "NOTE";
        String resourcePath = "/notes/new";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), resourcePath);
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> links = stateMachine.injectLinks(rimHandler, createMockInteractionContext(initial), testResponseEntity, headers, metadata);

        assertNotNull(links);
        assertFalse(links.isEmpty());
        assertEquals(1, links.size());
        Link link = (Link) links.toArray()[0];
        assertEquals("self", link.getRel());
        assertEquals("/baseuri/notes/new", link.getHref());
        assertEquals("NOTE.initial>GET>NOTE.initial", link.getId());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the link to 'self'
     * correctly for our test resource; in this self link we have used a path parameter.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLinksSelfPathParameters() {
        String ENTITY_NAME = "NOTE";
        String resourcePath = "/notes/{id}/reviewers";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), resourcePath);
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);

        /*
         * Mock the path parameters with the default 'id' element
         */
        MultivaluedMap<String, String> mockPathparameters = new MultivaluedMapImpl<String>();
        mockPathparameters.add("id", "123");

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> links = stateMachine.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), mockPathparameters, mock(MultivaluedMap.class), initial, mock(Metadata.class)), testResponseEntity, headers, metadata);

        assertNotNull(links);
        assertFalse(links.isEmpty());
        assertEquals(1, links.size());
        Link link = (Link) links.toArray()[0];
        assertEquals("self", link.getRel());
        assertEquals("/baseuri/notes/123/reviewers", link.getHref());
        assertEquals("NOTE.initial>GET>NOTE.initial", link.getId());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the link to 'self'
     * correctly for our test resource.
     */
    @SuppressWarnings("unchecked")
    @Test
    public void testGetLinksSelfTemplate() {
        String ENTITY_NAME = "NOTE";
        String resourcePath = "/notes/{id}";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), resourcePath);
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(null);

        /*
         * Mock the path parameters with default 'id' path element
         */
        MultivaluedMap<String, String> mockPathparameters = new MultivaluedMapImpl<String>();
        mockPathparameters.add("id", "123");

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> links = stateMachine.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), mockPathparameters, mock(MultivaluedMap.class), initial, mock(Metadata.class)), testResponseEntity, headers, metadata);

        assertNotNull(links);
        assertFalse(links.isEmpty());
        assertEquals(1, links.size());
        Link link = (Link) links.toArray()[0];
        assertEquals("self", link.getRel());
        assertEquals("/baseuri/notes/123", link.getHref());
        assertEquals("NOTE.initial>GET>NOTE.initial", link.getId());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the links to other
     * resource in our state machine.
     */
    @Test
    public void testGetLinksOtherResources() {
        String rootResourcePath = "/";
        ResourceState initial = new ResourceState("root", "initial", new ArrayList<Action>(), rootResourcePath);
        String NOTE_ENTITY = "NOTE";
        String notesResourcePath = "/notes";
        CollectionResourceState notesResource = new CollectionResourceState(NOTE_ENTITY, "collection", new ArrayList<Action>(), notesResourcePath);
        String PERSON_ENTITY = "PERSON";
        String personResourcePath = "/persons";
        CollectionResourceState personsResource = new CollectionResourceState(PERSON_ENTITY, "collection", new ArrayList<Action>(), personResourcePath);

        // create the transitions (links)
        initial.addTransition(new Transition.Builder().method("GET").target(notesResource).build());
        initial.addTransition(new Transition.Builder().method("GET").target(personsResource).build());

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> unsortedLinks = stateMachine.injectLinks(rimHandler, createMockInteractionContext(initial), new EntityResource<Object>(null), headers, metadata);

        assertNotNull(unsortedLinks);
        assertFalse(unsortedLinks.isEmpty());
        assertEquals(3, unsortedLinks.size());
        /*
         * expect 3 links 'self' 'collection notes' 'colleciton persons'
         */
        List<Link> links = new ArrayList<Link>(unsortedLinks);
        // sort the links so we have a predictable order for this test
        Collections.sort(links, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        // notes
        assertEquals("collection", links.get(0).getRel());
        assertEquals("/baseuri/notes", links.get(0).getHref());
        assertEquals("root.initial>GET>NOTE.collection", links.get(0).getId());
        // persons
        assertEquals("collection", links.get(1).getRel());
        assertEquals("/baseuri/persons", links.get(1).getHref());
        assertEquals("root.initial>GET>PERSON.collection", links.get(1).getId());
        // service root
        assertEquals("self", links.get(2).getRel());
        assertEquals("/baseuri/", links.get(2).getHref());
        assertEquals("root.initial>GET>root.initial", links.get(2).getId());
    }

    private CommandController mockCommandController() {
    	MapBasedCommandController cc = new MapBasedCommandController();
        cc.getCommandMap().put("notfound", createCommand("Customer", new Entity("Customer", new EntityProperties()), Result.FAILURE));
        cc.getCommandMap().put("found", createCommand("Customer", new Entity("Customer", new EntityProperties()), Result.SUCCESS));
        return cc;
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

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the conditional links to
     * other resource in our state machine.
     */
    @Test
    public void testShowConditionalLinks() {
        String rootResourcePath = "/bookings/{bookingId}";
        ResourceState initial = new ResourceState("BOOKING", "initial", new ArrayList<Action>(), rootResourcePath);
        // room reserved for the booking
        ResourceState room = new ResourceState(initial, "room", new ArrayList<Action>(), "/room");
        // booking cancelled
        ResourceState cancelled = new ResourceState(initial, "cancelled", new ArrayList<Action>(), "/cancelled", "cancelled".split(" "));
        ResourceState paid = new ResourceState(initial, "paid", new ArrayList<Action>(), "/payment", "pay".split(" "));
        List<Action> mockNotFound = new ArrayList<Action>();
        mockNotFound.add(new Action("notfound", TYPE.VIEW));
        ResourceState pwaiting = new ResourceState(paid, "pwaiting", mockNotFound, "/pwaiting", "wait".split(" "));
        ResourceState pconfirmed = new ResourceState(paid, "pconfirmed", mockNotFound, "/pconfirmed", "confirmed".split(" "));

        // create transitions that indicate state
        initial.addTransition(new Transition.Builder().target(room).build());
        initial.addTransition(new Transition.Builder().target(cancelled).build());
        initial.addTransition(new Transition.Builder().flags(Transition.AUTO).target(paid).build());
        // expressions can be added to the resource with the condition or anywhere in the resource state graph
        initial.addTransition(new Transition.Builder().target(pwaiting).build());
        initial.addTransition(new Transition.Builder().target(pconfirmed).build());

        // pseudo states that do the processing
        ResourceState cancel = new ResourceState(cancelled, "psuedo_cancel", new ArrayList<Action>(), null, "cancel".split(" "));
        ResourceState assignRoom = new ResourceState(room, "psuedo_assignroom", new ArrayList<Action>());
        ResourceState paymentDetails = new ResourceState(paid, "psuedo_setcarddetails", new ArrayList<Action>(), null, "pay".split(" "));

        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        int transitionFlags = 0; // regular transition
        // create the transitions (links)
        initial.addTransition(new Transition.Builder().method("POST").target(cancel).build());
        initial.addTransition(new Transition.Builder().method("PUT").target(assignRoom).build());

        List<Expression> expressions = new ArrayList<Expression>();
        expressions.add(new ResourceGETExpression(pconfirmed, Function.NOT_FOUND));
        expressions.add(new ResourceGETExpression(pwaiting, Function.NOT_FOUND));
        initial.addTransition(new Transition.Builder().method("PUT").target(paymentDetails).uriParameters(uriLinkageMap).flags(transitionFlags).evaluation(new SimpleLogicalExpressionEvaluator(expressions)).label("Make a payment").build());

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> unsortedLinks = stateMachine.injectLinks(rimHandler, createMockInteractionContext(initial), new EntityResource<Object>(new Booking("123")), headers, metadata);

        assertNotNull(unsortedLinks);
        assertFalse(unsortedLinks.isEmpty());
        assertEquals(8, unsortedLinks.size());
        /*
         * expect 4 links 'self' GET room GET cancelled GET paid GET pwaiting GET pconfirmed POST cancellation PUT room
         * & link to PUT pwaiting (as the booking has not been paid 'pconfirmed' and is not waiting 'pwaiting')
         */
        List<Link> links = new ArrayList<Link>(unsortedLinks);
        // sort the links so we have a predictable order for this test
        Collections.sort(links, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        // booking
        assertEquals("self", links.get(0).getRel());
        assertEquals("/baseuri/bookings/123", links.get(0).getHref());
        assertEquals("BOOKING.initial>GET>BOOKING.initial", links.get(0).getId());
        // cancel
        assertEquals("cancel", links.get(1).getRel());
        assertEquals("/baseuri/bookings/123/cancelled", links.get(1).getHref());
        assertEquals("BOOKING.initial>POST>BOOKING.psuedo_cancel", links.get(1).getId());
        // make payment
        assertEquals("pay", links.get(2).getRel());
        assertEquals("/baseuri/bookings/123/payment", links.get(2).getHref());
        assertEquals("BOOKING.initial>PUT(Make a payment)>BOOKING.psuedo_setcarddetails", links.get(2).getId());
        // set room
        assertEquals("item", links.get(3).getRel());
        assertEquals("/baseuri/bookings/123/room", links.get(3).getHref());
        assertEquals("BOOKING.initial>PUT>BOOKING.psuedo_assignroom", links.get(3).getId());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the conditional links to
     * other resource in our state machine; in this scenario the conditional link is only available as a transition from
     * the resource with the condition
     */
    @Test
    public void testShowConditionalLinksTargetOnlyExistsInExpression() {
        String rootResourcePath = "/bookings/{bookingId}";
        ResourceState initial = new ResourceState("BOOKING", "initial", new ArrayList<Action>(), rootResourcePath);
        // room reserved for the booking
        ResourceState room = new ResourceState(initial, "room", new ArrayList<Action>(), "/room");
        ResourceState paid = new ResourceState(initial, "paid", new ArrayList<Action>(), "/payment", "pay".split(" "));
        List<Action> mockNotFound = new ArrayList<Action>();
        mockNotFound.add(new Action("notfound", TYPE.VIEW));
        ResourceState pwaiting = new ResourceState(paid, "pwaiting", mockNotFound, "/pwaiting", "wait".split(" "));
        ResourceState pconfirmed = new ResourceState(paid, "pconfirmed", mockNotFound, "/pconfirmed", "confirmed".split(" "));

        // create transitions that indicate state
        initial.addTransition(new Transition.Builder().flags(Transition.AUTO).target(room).build());

        // pseudo states that do the processing
        ResourceState paymentDetails = new ResourceState(paid, "psuedo_setcarddetails", new ArrayList<Action>(), null, "pay".split(" "));

        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        int transitionFlags = 0; // regular transition

        List<Expression> expressions = new ArrayList<Expression>();
        expressions.add(new ResourceGETExpression(pconfirmed, Function.NOT_FOUND));
        expressions.add(new ResourceGETExpression(pwaiting, Function.NOT_FOUND));
        room.addTransition(new Transition.Builder().method("PUT").target(paymentDetails).uriParameters(uriLinkageMap).flags(transitionFlags).evaluation(new SimpleLogicalExpressionEvaluator(expressions)).label("Make a payment").build());

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> unsortedLinks = stateMachine.injectLinks(rimHandler, createMockInteractionContext(room), new EntityResource<Object>(new Booking("123")), headers, metadata);

        assertNotNull(unsortedLinks);
        assertFalse(unsortedLinks.isEmpty());
        assertEquals(2, unsortedLinks.size());
        /*
         * expect 2 links 'self' (the room) & link to PUT pwaiting (as the booking has not been paid 'pconfirmed' and is
         * not waiting 'pwaiting')
         */
        List<Link> links = new ArrayList<Link>(unsortedLinks);
        // sort the links so we have a predictable order for this test
        Collections.sort(links, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });

        // booking
        assertEquals("</baseuri/bookings/123/room>; rel=\"self\"; title=\"room\"", links.get(0).toString());
        // make a payment
        assertEquals("</baseuri/bookings/123/payment>; rel=\"pay\"; title=\"Make a payment\"", links.get(1).toString());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the conditional links to
     * other resource in our state machine.
     */
    @Test
    public void testDontShowConditionalLinks() {
        String rootResourcePath = "/bookings/{bookingId}";
        ResourceState initial = new ResourceState("BOOKING", "initial", new ArrayList<Action>(), rootResourcePath);
        // room reserved for the booking
        ResourceState room = new ResourceState(initial, "room", new ArrayList<Action>(), "/room");
        // booking cancelled
        ResourceState cancelled = new ResourceState(initial, "cancelled", new ArrayList<Action>(), "/cancelled", "cancel".split(" "));
        ResourceState paid = new ResourceState(initial, "paid", new ArrayList<Action>(), "/payment", "pay".split(" "));
        List<Action> mockNotFound = new ArrayList<Action>();
        mockNotFound.add(new Action("notfound", TYPE.VIEW));
        ResourceState pwaiting = new ResourceState(paid, "pwaiting", mockNotFound, "/pwaiting", "wait".split(" "));
        List<Action> mockFound = new ArrayList<Action>();
        mockFound.add(new Action("found", TYPE.VIEW));
        ResourceState pconfirmed = new ResourceState(paid, "pconfirmed", mockFound, "/pconfirmed", "confirmed".split(" "));

        // create transitions that indicate state
        initial.addTransition(new Transition.Builder().target(room).build());
        initial.addTransition(new Transition.Builder().target(cancelled).build());
        initial.addTransition(new Transition.Builder().flags(Transition.AUTO).target(paid).build());
        // TODO, expressions should also be followed in determining resource state graph
        initial.addTransition(new Transition.Builder().target(pwaiting).build());
        initial.addTransition(new Transition.Builder().target(pconfirmed).build());

        // pseudo states that do the processing
        ResourceState cancel = new ResourceState(cancelled, "psuedo_cancel", new ArrayList<Action>(), null, "cancel".split(" "));
        ResourceState assignRoom = new ResourceState(room, "psuedo_assignroom", new ArrayList<Action>());
        ResourceState paymentDetails = new ResourceState(paid, "psuedo_setcarddetails", new ArrayList<Action>(), null, "pay".split(" "));

        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        int transitionFlags = 0; // regular transition
        // create the transitions (links)
        initial.addTransition(new Transition.Builder().method("POST").target(cancel).build());
        initial.addTransition(new Transition.Builder().method("PUT").target(assignRoom).build());

        /*
         * In this test case we are mocking that the 'pwaiting' resource was actually found or OK, rather then NOT_FOUND
         */
        List<Expression> expressions = new ArrayList<Expression>();
        expressions.add(new ResourceGETExpression(pconfirmed, Function.NOT_FOUND));
        expressions.add(new ResourceGETExpression(pwaiting, Function.NOT_FOUND));
        initial.addTransition(new Transition.Builder().method("PUT").target(paymentDetails).uriParameters(uriLinkageMap).flags(transitionFlags).evaluation(new SimpleLogicalExpressionEvaluator(expressions)).label("Make a payment").build());

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(initial, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> unsortedLinks = stateMachine.injectLinks(rimHandler, createMockInteractionContext(initial), new EntityResource<Object>(new Booking("123")), headers, metadata);

        assertNotNull(unsortedLinks);
        assertFalse(unsortedLinks.isEmpty());
        assertEquals(7, unsortedLinks.size());
        /*
         * expect 3 links 'self' GET room GET cancelled GET paid GET pwaiting GET pconfirmed POST cancellation PUT room
         * & DO NOT show the payment link, payment should already be confirmed
         */
        List<Link> links = new ArrayList<Link>(unsortedLinks);
        // sort the links so we have a predictable order for this test
        Collections.sort(links, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        // booking
        assertEquals("self", links.get(0).getRel());
        assertEquals("/baseuri/bookings/123", links.get(0).getHref());
        assertEquals("BOOKING.initial>GET>BOOKING.initial", links.get(0).getId());
        // cancel
        assertEquals("cancel", links.get(1).getRel());
        assertEquals("/baseuri/bookings/123/cancelled", links.get(1).getHref());
        assertEquals("BOOKING.initial>POST>BOOKING.psuedo_cancel", links.get(1).getId());
        // set room
        assertEquals("item", links.get(2).getRel());
        assertEquals("/baseuri/bookings/123/room", links.get(2).getHref());
        assertEquals("BOOKING.initial>PUT>BOOKING.psuedo_assignroom", links.get(2).getId());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the links for the
     * collection itself.
     */
    @Test
    public void testGetLinksCollection() {
        String NOTE_ENTITY = "NOTE";
        String notesResourcePath = "/notes";
        CollectionResourceState notesResource = new CollectionResourceState(NOTE_ENTITY, "collection", new ArrayList<Action>(), notesResourcePath);
        String noteItemResourcePath = "/notes/{noteId}";
        ResourceState noteResource = new ResourceState(NOTE_ENTITY, "item", new ArrayList<Action>(), noteItemResourcePath, "item".split(" "));
        /* create the transitions (links) */
        // link to form to create new note
        notesResource.addTransition(new Transition.Builder().method("POST").target(new ResourceState("stack", "new", new ArrayList<Action>(), "/notes/new", "new".split(" "))).build());
        /*
         * define transition to view each item of the note collection no linkage map as target URI element (self) must
         * exist in source entity element (also self)
         */
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        notesResource.addTransition(new Transition.Builder().flags(Transition.FOR_EACH).method("GET").target(noteResource).uriParameters(uriLinkageMap).build());

        // the items of the collection
        List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
        entities.add(new EntityResource<Object>(createTestNote("1")));
        entities.add(new EntityResource<Object>(createTestNote("2")));
        entities.add(new EntityResource<Object>(createTestNote("6")));
        CollectionResource<Object> testResponseEntity = new CollectionResource<Object>("notes", entities);

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(notesResource, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> unsortedLinks = stateMachine.injectLinks(rimHandler, createMockInteractionContext(notesResource), testResponseEntity, headers, metadata);

        assertNotNull(unsortedLinks);
        assertFalse(unsortedLinks.isEmpty());
        assertEquals(2, unsortedLinks.size());
        /*
         * expect 2 links - self and one to form to create new note
         */
        List<Link> links = new ArrayList<Link>(unsortedLinks);
        // sort the links so we have a predictable order for this test
        Collections.sort(links, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }
        });
        // notes resource (self)
        assertEquals("GET", links.get(0).getMethod());
        assertEquals("self", links.get(0).getRel());
        assertEquals("/baseuri/notes", links.get(0).getHref());
        assertEquals("NOTE.collection>GET>NOTE.collection", links.get(0).getId());
        // new notes
        assertEquals("POST", links.get(1).getMethod());
        assertEquals("new", links.get(1).getRel());
        assertEquals("/baseuri/notes/new", links.get(1).getHref());
        assertEquals("NOTE.collection>POST>stack.new", links.get(1).getId());

        /* collect the links defined in each entity */
        List<Link> itemLinks = new ArrayList<Link>();
        for (EntityResource<Object> entity : entities) {
            assertNotNull(entity.getLinks());
            itemLinks.addAll(entity.getLinks());
        }

        /*
         * expect 3 links - one to each note for 'collection notes'
         */
        assertFalse(itemLinks.isEmpty());
        assertEquals(3, itemLinks.size());
        // sort the links so we have a predictable order for this test
        Collections.sort(itemLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        // link to note '1'
        // TODO with better rel support we should have self and NOTE.item
        // assertEquals("self NOTE.item", links.get(0).getRel());
        assertEquals("item", itemLinks.get(0).getRel());
        assertEquals("/baseuri/notes/1", itemLinks.get(0).getHref());
        assertEquals("NOTE.collection>GET>NOTE.item", itemLinks.get(0).getId());
        // link to note '2'
        assertEquals("item", itemLinks.get(1).getRel());
        assertEquals("/baseuri/notes/2", itemLinks.get(1).getHref());
        assertEquals("NOTE.collection>GET>NOTE.item", itemLinks.get(1).getId());
        // link to note '6'
        assertEquals("item", itemLinks.get(2).getRel());
        assertEquals("/baseuri/notes/6", itemLinks.get(2).getHref());
        assertEquals("NOTE.collection>GET>NOTE.item", itemLinks.get(2).getId());

    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the links for items in
     * the collection.
     */
    @Test
    public void testGetLinksCollectionItems() {
        String NOTE_ENTITY = "NOTE";
        String notesResourcePath = "/notes";
        CollectionResourceState notesResource = new CollectionResourceState(NOTE_ENTITY, "collection", new ArrayList<Action>(), notesResourcePath);
        String noteItemResourcePath = "/notes/{noteId}";
        ResourceState noteResource = new ResourceState(NOTE_ENTITY, "item", new ArrayList<Action>(), noteItemResourcePath, "item".split(" "));
        ResourceState noteFinalState = new ResourceState(NOTE_ENTITY, "final", new ArrayList<Action>(), noteItemResourcePath, "final".split(" "));
        /* create the transitions (links) */
        /*
         * define transition to view each item of the note collection no linkage map as target URI element (self) must
         * exist in source entity element (also self)
         */
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        notesResource.addTransition(new Transition.Builder().flags(Transition.FOR_EACH).method("GET").target(noteResource).uriParameters(uriLinkageMap).build());
        notesResource.addTransition(new Transition.Builder().flags(Transition.FOR_EACH).method("DELETE").target(noteFinalState).uriParameters(uriLinkageMap).build());
        // the items of the collection
        List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
        entities.add(new EntityResource<Object>(createTestNote("1")));
        entities.add(new EntityResource<Object>(createTestNote("2")));
        entities.add(new EntityResource<Object>(createTestNote("6")));
        CollectionResource<Object> testResponseEntity = new CollectionResource<Object>("notes", entities);

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(notesResource, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> baseLinks = stateMachine.injectLinks(rimHandler, createMockInteractionContext(notesResource), testResponseEntity, headers, metadata);
        // just one link to self, not really testing that here
        assertEquals(1, baseLinks.size());

        /* collect the links defined in each entity */
        List<Link> links = new ArrayList<Link>();
        for (EntityResource<Object> entity : entities) {
            assertNotNull(entity.getLinks());
            links.addAll(entity.getLinks());
        }

        /*
         * expect 6 links - one to self for each note for 'collection notes', one to DELETE each note
         */
        assertFalse(links.isEmpty());
        assertEquals(6, links.size());
        // sort the links so we have a predictable order for this test
        Collections.sort(links, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        // link to DELETE note '1'
        assertEquals("final", links.get(0).getRel());
        assertEquals("/baseuri/notes/1", links.get(0).getHref());
        assertEquals("NOTE.collection>DELETE>NOTE.final", links.get(0).getId());
        assertEquals("DELETE", links.get(0).getMethod());
        // link to DELETE note '2'
        assertEquals("final", links.get(1).getRel());
        assertEquals("/baseuri/notes/2", links.get(1).getHref());
        assertEquals("NOTE.collection>DELETE>NOTE.final", links.get(1).getId());
        assertEquals("DELETE", links.get(1).getMethod());
        // link to DELETE note '6'
        assertEquals("final", links.get(2).getRel());
        assertEquals("/baseuri/notes/6", links.get(2).getHref());
        assertEquals("NOTE.collection>DELETE>NOTE.final", links.get(2).getId());
        assertEquals("DELETE", links.get(0).getMethod());
        // link to GET note '1'
        assertEquals("item", links.get(3).getRel());
        assertEquals("/baseuri/notes/1", links.get(3).getHref());
        assertEquals("NOTE.collection>GET>NOTE.item", links.get(3).getId());
        assertEquals("GET", links.get(3).getMethod());
        // link to GET note '2'
        assertEquals("item", links.get(4).getRel());
        assertEquals("/baseuri/notes/2", links.get(4).getHref());
        assertEquals("NOTE.collection>GET>NOTE.item", links.get(4).getId());
        assertEquals("GET", links.get(4).getMethod());
        // link to GET note '6'
        assertEquals("item", links.get(5).getRel());
        assertEquals("/baseuri/notes/6", links.get(5).getHref());
        assertEquals("NOTE.collection>GET>NOTE.item", links.get(5).getId());
        assertEquals("GET", links.get(5).getMethod());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetLinkToCollectionResource() {
        ResourceState airport = new ResourceState("Airport", "airport", new ArrayList<Action>(), "/Airports('{id}')", null, new UriSpecification("airport", "/Airports('{id}')"));
        CollectionResourceState flights = new CollectionResourceState("Flight", "Flights", new ArrayList<Action>(), "/Airports('{id}')/Flights", null, null);

        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("filter", "arrivalAirportCode eq '{code}'");
        uriLinkageMap.put("id", "{code}");
        airport.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkageMap).build());

        // initialise and get the application state (links)
        ResourceStateMachine rsm = new ResourceStateMachine(airport, new BeanTransformer());

        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
        pathParameters.add("id", "123");
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> links = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), airport, mock(Metadata.class)), new EntityResource<Object>(createAirport("123", "BA")), headers, metadata);

        assertNotNull(links);
        assertFalse(links.isEmpty());
        assertEquals(2, links.size());

        assertTrue(containsLink(links, "Airport.airport>GET>Airport.airport", "/baseuri/Airports('123')"));
        assertTrue(containsLink(links, "Airport.airport>GET>Flight.Flights", "/baseuri/Airports('123')/Flights?filter=arrivalAirportCode+eq+'123'"));
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testGetMultipleLinksToCollectionResourceWithTokenInQueryParams() {
        ResourceState airport = new ResourceState("Airport", "airport", new ArrayList<Action>(), "/Airports('{id}')", null, new UriSpecification("airport", "/Airports('{id}')"));
        CollectionResourceState flights = new CollectionResourceState("Flight", "Flights", new ArrayList<Action>(), "/Flights()", null, null);

        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("$filter", "arrivalAirportCode eq '{code}'");
        airport.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkageMap).build());
        uriLinkageMap.put("$filter", "departureAirportCode eq '{code}'");
        airport.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkageMap).build());

        // initialise and get the application state (links)
        ResourceStateMachine rsm = new ResourceStateMachine(airport, new BeanTransformer());

        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl();
        pathParameters.add("id", "123");
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> links = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), airport, mock(Metadata.class)), new EntityResource<Object>(createAirport("London Luton", "LTN")), headers, metadata);

        assertNotNull(links);
        assertFalse(links.isEmpty());

        // sort the links so we have a predictable order for this test
        List<Link> sortedLinks = new ArrayList<Link>();
        sortedLinks.addAll(links);
        Collections.sort(sortedLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        assertEquals("Airport.airport>GET>Airport.airport", sortedLinks.get(0).getId());
        assertEquals("/baseuri/Airports('123')", sortedLinks.get(0).getHref());
        assertEquals("Airport.airport>GET>Flight.Flights", sortedLinks.get(1).getId());
        assertEquals("/baseuri/Flights()?$filter=arrivalAirportCode+eq+'London+Luton'", sortedLinks.get(1).getHref());
        assertEquals("Airport.airport>GET>Flight.Flights", sortedLinks.get(2).getId());
        assertEquals("/baseuri/Flights()?$filter=departureAirportCode+eq+'London+Luton'", sortedLinks.get(2).getHref());
        assertEquals(3, links.size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testInjectLinksForEachToSameResourceState() {
        CollectionResourceState airports = new CollectionResourceState("Airports", "airports", new ArrayList<Action>(), "/Airports()", null, null);
        CollectionResourceState flights = new CollectionResourceState("Flight", "Flights", new ArrayList<Action>(), "/Flights()", null, null);

        Map<String, String> uriLinkage = new HashMap<String, String>();
        uriLinkage.put("$filter", "arrivalAirportCode eq '{code}'");
        airports.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkage).flags(Transition.FOR_EACH).label("arrival").build());
        uriLinkage.put("$filter", "departureAirportCode eq '{code}'");
        airports.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkage).flags(Transition.FOR_EACH).label("departure").build());

        // initialise and get the application state (links)
        ResourceStateMachine rsm = new ResourceStateMachine(airports, new BeanTransformer());

        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl();
        List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
        entities.add(new EntityResource<Object>(createAirport("London Luton", "LTN")));
        CollectionResource<Object> collectionResource = new CollectionResource<Object>(entities);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> collectionLinks = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), airports, mock(Metadata.class)), collectionResource, headers, metadata);

        assertNotNull(collectionLinks);
        assertFalse(collectionLinks.isEmpty());
        assertEquals(1, collectionLinks.size());

        Collection<Link> links = entities.get(0).getLinks();
        assertNotNull(links);
        assertFalse(links.isEmpty());

        // sort the links so we have a predictable order for this test
        List<Link> sortedLinks = new ArrayList<Link>();
        sortedLinks.addAll(links);
        Collections.sort(sortedLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        assertEquals("Airports.airports>GET(arrival)>Flight.Flights", sortedLinks.get(0).getId());
        assertEquals("/baseuri/Flights()?$filter=arrivalAirportCode+eq+'London+Luton'", sortedLinks.get(0).getHref());
        assertEquals("Airports.airports>GET(departure)>Flight.Flights", sortedLinks.get(1).getId());
        assertEquals("/baseuri/Flights()?$filter=departureAirportCode+eq+'London+Luton'", sortedLinks.get(1).getHref());
        assertEquals(2, links.size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testInjectLinksForEachToSameResourceStateUriLinkage() {
        CollectionResourceState airports = new CollectionResourceState("Airports", "airports", new ArrayList<Action>(), "/Airports()", null, null);
        ResourceState airport = new ResourceState("Airport", "airport", new ArrayList<Action>(), "/Airports('{id}')", null, new UriSpecification("airport", "/Airports('{id}')"));

        Map<String, String> uriLinkage = new HashMap<String, String>();
        // just using code because its available on our mock object, real life this is arrivalAirportCode
        uriLinkage.put("id", "{code}");
        airports.addTransition(new Transition.Builder().method("GET").target(airport).uriParameters(uriLinkage).flags(Transition.FOR_EACH).label("origin").build());
        // just using code because its available on our mock object, real life this is departureAirportCode
        uriLinkage.put("id", "{iata}");
        airports.addTransition(new Transition.Builder().method("GET").target(airport).uriParameters(uriLinkage).flags(Transition.FOR_EACH).label("destination").build());

        // initialise and get the application state (links)
        ResourceStateMachine rsm = new ResourceStateMachine(airports, new BeanTransformer());

        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl();
        List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
        entities.add(new EntityResource<Object>(createAirport("London Luton", "LTN")));
        CollectionResource<Object> collectionResource = new CollectionResource<Object>(entities);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> collectionLinks = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), airports, mock(Metadata.class)), collectionResource, headers, metadata);

        assertNotNull(collectionLinks);
        assertFalse(collectionLinks.isEmpty());
        assertEquals(1, collectionLinks.size());

        Collection<Link> links = entities.get(0).getLinks();
        assertNotNull(links);
        assertFalse(links.isEmpty());

        // sort the links so we have a predictable order for this test
        List<Link> sortedLinks = new ArrayList<Link>();
        sortedLinks.addAll(links);
        Collections.sort(sortedLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        assertEquals("Airports.airports>GET(destination)>Airport.airport", sortedLinks.get(0).getId());
        assertEquals("/baseuri/Airports('LTN')", sortedLinks.get(0).getHref());
        assertEquals("Airports.airports>GET(origin)>Airport.airport", sortedLinks.get(1).getId());
        assertEquals("/baseuri/Airports('London+Luton')", sortedLinks.get(1).getHref());
        assertEquals(2, links.size());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testGetLinkToCollectionResourceWithReferenceToExistingQueryParam() {
        ResourceState airport = new ResourceState("Airport", "airport", new ArrayList<Action>(), "/Airports('{id}')", null, new UriSpecification("airport", "/Airports('{id}')"));
        CollectionResourceState flights = new CollectionResourceState("Flight", "Flights", new ArrayList<Action>(), "/Flights()", null, null);
        CollectionResourceState passengers = new CollectionResourceState("Passenger", "Passengers", new ArrayList<Action>(), "/Passengers()", null, null);

        // Add link to list flights
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("myfilter", "arrivalAirportCode eq '{code}'");
        airport.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkageMap).build());
        uriLinkageMap.put("myfilter", "departureAirportCode eq '{code}'");
        airport.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkageMap).build());

        // Add link to list passengers for all those flights
        uriLinkageMap.clear();
        uriLinkageMap.put("myfilter", "{myfilter}");
        flights.addTransition(new Transition.Builder().method("GET").target(passengers).uriParameters(uriLinkageMap).build());

        // initialise and get the application state (links)
        ResourceStateMachine rsm = new ResourceStateMachine(airport, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);

        // Generate links from airport to flights
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl();
        pathParameters.add("id", "123");
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        Collection<Link> airportLinks = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), airport, mock(Metadata.class)), new EntityResource<Object>(createAirport("London Luton", "LTN")), headers, metadata);
        assertNotNull(airportLinks);
        assertFalse(airportLinks.isEmpty());
        assertEquals(3, airportLinks.size());

        // Generate links from airport to flights
        for (Link airportLink : airportLinks) {
            pathParameters = new MultivaluedMapImpl();

            // Obtain query parameters from link
            MultivaluedMap<String, String> queryParameters = new MultivaluedMapImpl();
            UriBuilder uriBuilder = UriBuilder.fromUri(airportLink.getHref());
            String query = uriBuilder.build(new HashMap<String, Object>()).getQuery();
            if (query != null && !query.isEmpty()) {
                String[] queryParams = query.split("&");
                for (String queryParam : queryParams) {
                    String[] keyValuePair = queryParam.split("=");
                    queryParameters.add(keyValuePair[0], keyValuePair[1]);
                }
            }

            // Create links
            metadata = mock(Metadata.class);
            Collection<Link> flightsLinks = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, queryParameters, flights, mock(Metadata.class)), new EntityResource<Object>(null), headers, metadata);

            if (airportLink.getId().equals("Airport.airport>GET(arrivalAirportCode eq '{code}')>Flight.Flights")) {
                assertTrue(containsLink(flightsLinks, "Flight.Flights>GET({myfilter})>Passenger.Passengers", "/baseuri/Passengers()?myfilter=arrivalAirportCode+eq+'London+Luton'"));
            } else if (airportLink.getId().equals("Airport.airport>GET(departureAirportCode eq '{code}')>Flight.Flights")) {
                assertTrue(containsLink(flightsLinks, "Flight.Flights>GET({myfilter})>Passenger.Passengers", "/baseuri/Passengers()?myfilter=departureAirportCode+eq+'London+Luton'"));
            }
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Test
    public void testGetLinkWithLiteralQueryParams() {
        ResourceState airport = new ResourceState("Airport", "airport", new ArrayList<Action>(), "/Airports('{id}')");
        ResourceState flights = new ResourceState("Operational", "operational", new ArrayList<Action>(), "/FlightStats");

        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("apikey", "Some literal value");
        airport.addTransition(new Transition.Builder().method("GET").target(flights).uriParameters(uriLinkageMap).build());

        // initialise and get the application state (links)
        ResourceStateMachine rsm = new ResourceStateMachine(airport, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);

        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl();
        pathParameters.add("id", "123");
        MultivaluedMap<String, String> queryParameters = new MultivaluedMapImpl();
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        InteractionContext ctx = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, queryParameters, airport, mock(Metadata.class));
        MultivaluedMap<String, String> outQueryParameters = new MultivaluedMapImpl();
        outQueryParameters.add("email", "name@test.com");
        ctx.getOutQueryParameters().putAll(outQueryParameters);
        Collection<Link> links = rsm.injectLinks(rimHandler, ctx, new EntityResource<Object>(createAirport("London Luton", "LTN")), headers, metadata);

        assertNotNull(links);
        assertFalse(links.isEmpty());
        assertEquals(2, links.size());

        // sort the links so we have a predictable order for this test
        List<Link> sortedLinks = new ArrayList<Link>();
        sortedLinks.addAll(links);
        Collections.sort(sortedLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        assertEquals("Airport.airport>GET>Airport.airport", sortedLinks.get(0).getId());
        assertEquals("/baseuri/Airports('123')?email=name%40test.com", sortedLinks.get(0).getHref());
        assertEquals("Airport.airport>GET>Operational.operational", sortedLinks.get(1).getId());
        assertEquals("/baseuri/FlightStats?email=name%40test.com&apikey=Some+literal+value", sortedLinks.get(1).getHref());
    }

    /*
     * We use links (hypermedia) for controlling / describing application state. Test we return the links for the
     * collection itself.
     */
    @Test
    public void testEmbedMultipleResourcesEntityResource() {
        String ENTITY = "ENTITY";
        List<Action> mockActions = new ArrayList<Action>();
        mockActions.add(new Action("found", Action.TYPE.VIEW, null));
        ResourceState parentResource = new ResourceState(ENTITY, "parentResource", new ArrayList<Action>(), "/path");
        ResourceState childResource1 = new ResourceState("PROFILE", "childResource1", mockActions, "/root/profile", "profile".split(" "));
        ResourceState childResource2 = new ResourceState("PREFERENCE", "childResource2", mockActions, "/root/preferences", "preferences".split(" "));
        ResourceState childResource3 = new ResourceState("POSTPREF", "postpref", mockActions, "/root/postpref", "postpref".split(" "));
        /* create the transitions (links) */
        parentResource.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("GET").target(childResource1).build());
        parentResource.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("GET").target(childResource2).build());
        parentResource.addTransition(new Transition.Builder().flags(Transition.EMBEDDED).method("POST").target(childResource3).build());

        // the mock resources
        EntityResource<Object> testResponseEntity = new EntityResource<Object>(ENTITY, createTestNote("rootobject"));

        // initialise and get the application state (links)
        ResourceStateMachine stateMachine = new ResourceStateMachine(parentResource, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        InteractionContext mockCtx = createMockInteractionContext(parentResource);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        stateMachine.injectLinks(rimHandler, mockCtx, testResponseEntity, headers, metadata);
        Map<Transition, RESTResource> embeddedResources = stateMachine.embedResources(rimHandler, mock(HttpHeaders.class), mockCtx, testResponseEntity);

        assertNotNull(embeddedResources);
        assertFalse(embeddedResources.isEmpty());
        assertEquals(3, embeddedResources.size());
        /*
         * expect 2 resources - profile, preferences and postpref
         */
        List<RESTResource> resources = new ArrayList<RESTResource>(embeddedResources.values());
        // sort the resources so we have a predictable order for this test
        Collections.sort(resources, new Comparator<RESTResource>() {

            @Override
            public int compare(RESTResource o1, RESTResource o2) {
                return o1.getEntityName().compareTo(o2.getEntityName());
            }
        });
        // postpref resources
        assertEquals("POSTPREF", resources.get(0).getEntityName());
        // preferences resources
        assertEquals("PREFERENCE", resources.get(1).getEntityName());
        // profile resource
        assertEquals("PROFILE", resources.get(2).getEntityName());
    }

    @Test
    public void testDetermineAction() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action("GETEntities", Action.TYPE.VIEW));
        ResourceState notes = new ResourceState(initial, "notes", actions, "/notes");
        actions = new ArrayList<Action>();
        actions.add(new Action("GETEntity", Action.TYPE.VIEW));
        actions.add(new Action("CreateEntity", Action.TYPE.ENTRY));
        ResourceState created = new ResourceState(initial, "created", actions, "/created");

        initial.addTransition(new Transition.Builder().method("PUT").target(notes).build());
        initial.addTransition(new Transition.Builder().method("POST").target(created).build());

        initial.addTransition(new Transition.Builder().method("GET").target(notes).build());
        initial.addTransition(new Transition.Builder().method("GET").target(created).build());

        // Define resource state machine
        ResourceStateMachine sm = new ResourceStateMachine(initial);
        CommandController mockCommandController = mock(CommandController.class);
        when(mockCommandController.fetchCommand(anyString())).thenReturn(mock(InteractionCommand.class));
        sm.setCommandController(mockCommandController);

        // Ensure the correct actions are used
        sm.determineAction(new Event("GET", "GET"), "/entity/notes");
        verify(mockCommandController).fetchCommand("GETEntities");

        reset();
        sm.determineAction(new Event("GET", "GET"), "/entity/created");
        verify(mockCommandController).fetchCommand("GETEntity");

        reset();
        sm.determineAction(new Event("POST", "POST"), "/entity/created");
        verify(mockCommandController).fetchCommand("CreateEntity");
    }

    @Test
    public void testDetermineActionWorkflow() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action("GETEntity", Action.TYPE.VIEW));
        actions.add(new Action("ValidateWithSomeService", Action.TYPE.ENTRY));
        actions.add(new Action("CreateEntity", Action.TYPE.ENTRY));
        ResourceState created = new ResourceState(initial, "created", actions, "/created");

        initial.addTransition(new Transition.Builder().method("POST").target(created).build());

        // Define resource state machine
        ResourceStateMachine sm = new ResourceStateMachine(initial);
        CommandController mockCommandController = mock(CommandController.class);
        when(mockCommandController.fetchCommand(anyString())).thenReturn(mock(InteractionCommand.class));
        sm.setCommandController(mockCommandController);

        sm.determineAction(new Event("POST", "POST"), "/entity/created");
        verify(mockCommandController).fetchCommand("ValidateWithSomeService");
        verify(mockCommandController).fetchCommand("CreateEntity");

    }

    @Test
    public void testDetermineState() {
        String ENTITY_NAME = "";
        ResourceState initial = new ResourceState(ENTITY_NAME, "initial", new ArrayList<Action>(), "/entity");
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action("GETEntities", Action.TYPE.VIEW));
        ResourceState notes = new ResourceState(initial, "notes", actions, "/notes");
        actions = new ArrayList<Action>();
        actions.add(new Action("GETEntity", Action.TYPE.VIEW));
        actions.add(new Action("CreateEntity", Action.TYPE.ENTRY));
        ResourceState created = new ResourceState(initial, "created", actions, "/created");

        initial.addTransition(new Transition.Builder().method("PUT").target(notes).build());
        initial.addTransition(new Transition.Builder().method("POST").target(created).build());

        initial.addTransition(new Transition.Builder().method("GET").target(notes).build());
        initial.addTransition(new Transition.Builder().method("GET").target(created).build());

        // Define resource state machine
        ResourceStateMachine sm = new ResourceStateMachine(initial);

        // Ensure the correct actions are used
        assertEquals("notes", sm.determineState(new Event("GET", "GET"), "/entity/notes").getName());
        assertEquals("created", sm.determineState(new Event("GET", "GET"), "/entity/created").getName());
        assertEquals("created", sm.determineState(new Event("POST", "POST"), "/entity/created").getName());
    }

    @Test
    public void testGetTransitionProperties() {
        // Create RSM
        ResourceState existsState = new ResourceState("toaster", "exists", new ArrayList<Action>(), "/machines/toaster");
        ResourceState cookingState = new ResourceState("toaster", "cooking", new ArrayList<Action>(), "/machines/toaster/cooking");
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("linkParam", "def");
        existsState.addTransition(new Transition.Builder().method("GET").target(cookingState).uriParameters(uriLinkageMap).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(existsState, new EntityTransformer());

        // Create entity
        EntityProperties customerFields = new EntityProperties();
        customerFields.setProperty(new EntityProperty("name", "Fred"));
        Entity entity = new Entity("Customer", customerFields);

        // Create path params
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
        pathParameters.putSingle("pathParam", "abc");

        // Evaluate test
        Map<String, Object> transProps = stateMachine.getTransitionProperties(existsState.getTransition(cookingState), entity, pathParameters, null);
        assertEquals("abc", transProps.get("pathParam")); // Check path parameter
        assertEquals("def", transProps.get("linkParam")); // Check link parameter
        assertEquals("Fred", transProps.get("name")); // Check entity property
    }

    @Test
    public void testGetTransitionPropertiesWithSameEntityProperty() {
        // Create RSM
        ResourceState customerState = new ResourceState("Customer", "child", new ArrayList<Action>(), "/customers/{id}");
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("id", "{parent}");
        customerState.addTransition(new Transition.Builder().method("GET").target(customerState).uriParameters(uriLinkageMap).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(customerState, new EntityTransformer());

        // Create entity
        EntityProperties customerFields = new EntityProperties();
        customerFields.setProperty(new EntityProperty("id", "100"));
        customerFields.setProperty(new EntityProperty("name", "Fred"));
        customerFields.setProperty(new EntityProperty("parent", "123"));
        Entity entity = new Entity("Customer", customerFields);

        // link parameters must take priority
        Map<String, Object> transProps = stateMachine.getTransitionProperties(customerState.getTransition(customerState), entity, new MultivaluedMapImpl<String>(), null);
        assertEquals("123", transProps.get("id"));
    }

    @Test
    public void testGetPathParametersForTargetState() {
        // Create RSM
        ResourceState existsState = new ResourceState("toaster", "exists", new ArrayList<Action>(), "/machines/toaster");
        ResourceState cookingState = new ResourceState("toaster", "cooking", new ArrayList<Action>(), "/machines/toaster/cooking({id})");
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("id", "{toasterId}");
        existsState.addTransition(new Transition.Builder().method("GET").target(cookingState).uriParameters(uriLinkageMap).build());
        ResourceStateMachine stateMachine = new ResourceStateMachine(existsState, new EntityTransformer());

        // Create entity
        EntityProperties customerFields = new EntityProperties();
        customerFields.setProperty(new EntityProperty("toasterId", "SuperToaster"));
        Entity entity = new Entity("Toaster", customerFields);

        // Create path params
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();

        // Evaluate test
        Map<String, Object> transProps = stateMachine.getTransitionProperties(existsState.getTransition(cookingState), entity, pathParameters, null);
        MultivaluedMap<String, String> pathParams = HypermediaTemplateHelper.getPathParametersForTargetState(existsState.getTransition(cookingState), transProps);
        assertEquals("SuperToaster", pathParams.getFirst("id"));
    }

    /*
     * Check that multiple GET (View) actions are correctly added.
     */
    @Test
    public void testDetermineMultipleGetAction() {
        String ENTITY_NAME = "";
        List<Action> actions = new ArrayList<Action>();

        // Add multiple GET actions
        Action expected1 = new Action("GETEntities", Action.TYPE.VIEW);
        actions.add(expected1);
        Action expected2 = new Action("GETEntities", Action.TYPE.VIEW);
        actions.add(expected2);

        ResourceState state = new ResourceState(ENTITY_NAME, "test", actions, "test");

        // Create resource state machine
        ResourceStateMachine sm = new ResourceStateMachine(state);

        // Ensure the correct actions are present.
        List<Action> actual = sm.determineActions(new Event("GET", "GET"), state);

        assertEquals(2, actual.size());
        assertTrue(actual.contains(expected1));
        assertTrue(actual.contains(expected2));
    }

    @SuppressWarnings({ "unused" })
    private Object createTestNote(final String id) {
        return new Object() {

            final String noteId = id;

            public String getNoteId() {
                return noteId;
            }
        };
    }

    private Object createAirport(final String id, final String iataCode) {
        return new Object() {

            final String code = id;

            @SuppressWarnings("unused")
            public String getCode() {
                return code;
            }

            final String iata = iataCode;

            @SuppressWarnings("unused")
            public String getIata() {
                return iata;
            }
        };
    }

    private boolean containsLink(Collection<Link> links, String id, String href) {
        for (Link l : links) {
            if (l.getId().equals(id) && l.getHref().equals(href)) {
                return true;
            }
        }
        // Link not found => print debug info
        System.out.println("Links with id [" + id + "] and href [" + href + "] does not exist:");
        for (Link l : links) {
            System.out.println("   Link: id [" + l.getId() + "], href [" + l.getHref() + "]");
        }
        return false;
    }

    /*
     * Check that when there is a circular transition link registration does not go into an infinite loop.
     */
    @Test
    public void testCircularTransition() {
        // Create a couple of states.
        ResourceState initialState = new ResourceState("rubbish", "rubbish", new ArrayList<Action>(), "/rubbish");
        initialState.setInitial(true);
        ResourceState newState = new ResourceState("rubbish", "rubbish", new ArrayList<Action>(), "/rubbish");

        // Create a circular link between the states.
        Transition transition1 = new Transition.Builder().flags(Transition.FOR_EACH).method("GET").target(newState).build();
        initialState.addTransition(transition1);
        Transition transition2 = new Transition.Builder().flags(Transition.FOR_EACH).method("GET").target(initialState).build();
        newState.addTransition(transition2);

        // Create a state machine
        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState);

        // Try to register. In the failing condition this may take some time to
        // run out of memory but in that case we are going to fail anyway. In
        // the working state it will return rapidly.
        try {
            stateMachine.register(initialState, "GET");
        } catch (StackOverflowError e) {
            fail("Registration failed with stack overflow error.");
        } catch (Exception e) {
            fail("Registration failed with unexpected exception: " + e);
        }
    }

    /**
     * Creates a Resource State Machine for Note with one transition and Injects Links with the supplied resource entity
     * name
     * 
     * @param resourceEntityName
     * @return List of sorted links
     */
    private List<Link> createResourceStateMachineForNotes(String resourceEntityName) {
        String entityName = "Note";
        ResourceState initialState = new ResourceState(entityName, "note", new ArrayList<Action>(), "/notes({noteId})");
        initialState.setInitial(true);
        Map<String, String> uriLinkageMap = new HashMap<String, String>();
        uriLinkageMap.put("id", "{noteId}");
        ResourceState noteEditState = new ResourceState(entityName, "note_edit", new ArrayList<Action>(), "/edit");
        initialState.addTransition(new Transition.Builder().target(noteEditState).uriParameters(uriLinkageMap).build());

        ResourceStateMachine stateMachine = new ResourceStateMachine(initialState, new BeanTransformer());
        HTTPHypermediaRIM rimHandler = mockRIMHandler(stateMachine);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);

        Collection<Link> unsortedLinks = stateMachine.injectLinks(rimHandler, createMockInteractionContext(initialState), new EntityResource<Object>(createTestNote(resourceEntityName)), headers, metadata);
        List<Link> links = new ArrayList<Link>(unsortedLinks);
        // sort the links so we have a predictable order for this test
        Collections.sort(links, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getId().compareTo(o2.getId());
            }

        });
        return links;
    }

    /**
     * Unit test to verify processing of hypermedia links containing HTTP character entities as specified in RFC 3986.
     */
    @Test
    public void testGetLinksContainingReservedCharacters() {
    	// (":", "/", "?", "#", "[", "]", "@")
        assertForUrlParamWithReservedChar("123:456", "123%3A456");
        assertForUrlParamWithReservedChar("123/456", "123%2F456");
        assertForUrlParamWithReservedChar("123?456", "123%3F456");
        assertForUrlParamWithReservedChar("123#456", "123%23456");
        assertForUrlParamWithReservedChar("123[456]", "123%5B456%5D");
        assertForUrlParamWithReservedChar("123@456", "123%40456");
        assertForUrlParamWithReservedChar(":1/2?3#4[5]6@", "%3A1%2F2%3F3%234%5B5%5D6%40");

        // "!" / "$" / "&" / "'" / "(" / ")"
        // "*" / "+" / "," / ";" / "="
        assertForUrlParamWithReservedChar("123!456", "123%21456");
        assertForUrlParamWithReservedChar("123$456", "123%24456");
        assertForUrlParamWithReservedChar("123&456", "123%26456");
        assertForUrlParamWithReservedChar("123'456", "123%27456");
        assertForUrlParamWithReservedChar("123(456)", "123%28456%29");
        // assertForUrlParamWithReservedChar("123*456", "123%2A456"); //not supported by URLEncoder
        assertForUrlParamWithReservedChar("123+456", "123%2B456");
        assertForUrlParamWithReservedChar("123,456", "123%2C456");
        assertForUrlParamWithReservedChar("123;456", "123%3B456");
        assertForUrlParamWithReservedChar("123=456", "123%3D456");
        assertForUrlParamWithReservedChar("!1$2&3'4(5)6+7,8;9=", "%211%242%263%274%285%296%2B7%2C8%3B9%3D");
    }

    /**
     * @param resourceEntityName
     * @param expectedName
     */
    private void assertForUrlParamWithReservedChar(String resourceEntityName, String expectedName) {
        List<Link> links = createResourceStateMachineForNotes(resourceEntityName);
        // self
        assertEquals("self", links.get(0).getRel());
        assertEquals("/baseuri/notes(" + expectedName + ")", links.get(0).getHref());
        // item
        assertEquals("item", links.get(1).getRel());
        assertEquals("/baseuri/edit?id=" + expectedName, links.get(1).getHref());
    }

    @Test
    public void testInjectLinksForEachCollectionResource() {
        CollectionResourceState customerState = new CollectionResourceState("customer", "customer", new ArrayList<Action>(), "/customer()", null, null);
        CollectionResourceState contactState = new CollectionResourceState("contact", "contact", new ArrayList<Action>(), "/contact()", null, null);

        Map<String, String> uriLinkage = new HashMap<String, String>();
        uriLinkage.put("filter", "Id eq '{Contact.Email}'");
        customerState.addTransition(new Transition.Builder().method("GET").target(contactState).uriParameters(uriLinkage).flags(Transition.FOR_EACH).sourceField("AField").build());

        OCollection<?> contactColl = OCollections.newBuilder(null).add(createComplexObject("Email", "johnEmailAddr", "Tel", "12345")).add(createComplexObject("Email", "smithEmailAddr", "Tel", "66778")).build();
        OProperty<?> contactProp = OProperties.collection("source_Contact", null, contactColl);
        List<OProperty<?>> contactPropList = new ArrayList<OProperty<?>>();
        contactPropList.add(contactProp);

        List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
        OEntity entity = OEntities.createRequest(EdmEntitySet.newBuilder().build(), contactPropList, null);
        entities.add(new EntityResource<Object>(entity));

        ResourceStateMachine rsm = new ResourceStateMachine(customerState, getOEntityTransformer(contactColl));
        CollectionResource<Object> collectionResource = new CollectionResource<Object>(entities);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
        @SuppressWarnings("unchecked")
        Collection<Link> collectionLinks = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), customerState, mock(Metadata.class)), collectionResource, headers, metadata);

        assertNotNull(collectionLinks);
        assertFalse(collectionLinks.isEmpty());
        assertEquals(1, collectionLinks.size());

        Collection<Link> links = entities.get(0).getLinks();
        assertNotNull(links);
        assertFalse(links.isEmpty());

        // sort the links so we have a predictable order for this test
        List<Link> sortedLinks = new ArrayList<Link>();
        sortedLinks.addAll(links);
        Collections.sort(sortedLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getHref().compareTo(o2.getHref());
            }
        });
        assertEquals("/baseuri/contact()?filter=Id+eq+'johnEmailAddr'", sortedLinks.get(0).getHref());
        assertEquals("/baseuri/contact()?filter=Id+eq+'smithEmailAddr'", sortedLinks.get(1).getHref());
        assertEquals(2, links.size());
    }

    @Test
    public void testInjectLinksForEachCollectionResourceTwoLevel() {
        CollectionResourceState customerState = new CollectionResourceState("customer", "customer", new ArrayList<Action>(), "/customer()", null, null);
        CollectionResourceState contactState = new CollectionResourceState("contact", "contact", new ArrayList<Action>(), "/contact()", null, null);

        Map<String, String> uriLinkage = new HashMap<String, String>();
        uriLinkage.put("filter", "Id eq '{Contact.Address.PostCode}'");
        customerState.addTransition(new Transition.Builder().method("GET").target(contactState).uriParameters(uriLinkage).flags(Transition.FOR_EACH).sourceField("AField").build());

        // Inner collection
        OCollection<?> postCodeColl = OCollections.newBuilder(null).add(createComplexObject("PostCode", "ABCD")).add(createComplexObject("PostCode", "EFGH")).build();

        // Outer Collection
        OProperty<?> addressCollProperty = OProperties.collection("Address", null, postCodeColl);
        List<OProperty<?>> addressPropList = new ArrayList<OProperty<?>>();
        addressPropList.add(addressCollProperty);
        OComplexObject addressDetails = OComplexObjects.create(EdmComplexType.newBuilder().build(), addressPropList);
        OCollection<?> addressColl = OCollections.newBuilder(null).add(addressDetails).build();

        OProperty<?> contactCollectionProp = OProperties.collection("source_Contact", null, addressColl);
        List<OProperty<?>> contactPropList = new ArrayList<OProperty<?>>();
        contactPropList.add(contactCollectionProp);
        List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
        entities.add(new EntityResource<Object>(OEntities.createRequest(EdmEntitySet.newBuilder().build(), contactPropList, null)));

        ResourceStateMachine rsm = new ResourceStateMachine(customerState, getOEntityTransformer(addressColl));
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
        CollectionResource<Object> collectionResource = new CollectionResource<Object>(entities);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        @SuppressWarnings("unchecked")
        Collection<Link> collectionLinks = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), customerState, mock(Metadata.class)), collectionResource, headers, metadata);

        assertNotNull(collectionLinks);
        assertFalse(collectionLinks.isEmpty());
        assertEquals(1, collectionLinks.size());

        Collection<Link> links = entities.get(0).getLinks();
        assertNotNull(links);
        assertFalse(links.isEmpty());

        // sort the links so we have a predictable order for this test
        List<Link> sortedLinks = new ArrayList<Link>();
        sortedLinks.addAll(links);
        Collections.sort(sortedLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getHref().compareTo(o2.getHref());
            }
        });

        assertEquals("/baseuri/contact()?filter=Id+eq+'ABCD'", sortedLinks.get(0).getHref());
        assertEquals("/baseuri/contact()?filter=Id+eq+'EFGH'", sortedLinks.get(1).getHref());
        assertEquals(2, links.size());
    }

    @Test
    public void testInjectLinksForEachCollectionResourceMultiParams() {
        CollectionResourceState customerState = new CollectionResourceState("customer", "customer", new ArrayList<Action>(), "/customer()", null, null);
        CollectionResourceState contactState = new CollectionResourceState("contact", "contact", new ArrayList<Action>(), "/contact()", null, null);

        Map<String, String> uriLinkage = new HashMap<String, String>();
        uriLinkage.put("filter", "Name eq {personName} and Id eq '{Contact.Email}'");
        customerState.addTransition(new Transition.Builder().method("GET").target(contactState).uriParameters(uriLinkage).flags(Transition.FOR_EACH).sourceField("AField").build());

        OCollection<?> contactColl = OCollections.newBuilder(null).add(createComplexObject("Email", "johnEmailAddr", "Tel", "12345")).add(createComplexObject("Email", "smithEmailAddr", "Tel", "66778")).build();
        OProperty<?> contactProp = OProperties.collection("source_Contact", null, contactColl);
        List<OProperty<?>> contactPropList = new ArrayList<OProperty<?>>();
        contactPropList.add(contactProp);

        List<EntityResource<Object>> entities = new ArrayList<EntityResource<Object>>();
        OEntity entity = OEntities.createRequest(EdmEntitySet.newBuilder().build(), contactPropList, null);
        entities.add(new EntityResource<Object>(entity));

        ResourceStateMachine rsm = new ResourceStateMachine(customerState, getOEntityTransformer(contactColl));
        CollectionResource<Object> collectionResource = new CollectionResource<Object>(entities);
        HTTPHypermediaRIM rimHandler = mockRIMHandler(rsm);
        HttpHeaders headers = mock(HttpHeaders.class);
        Metadata metadata = mock(Metadata.class);
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String>();
        pathParameters.add("personName", "John");
        @SuppressWarnings("unchecked")
        Collection<Link> collectionLinks = rsm.injectLinks(rimHandler, new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), pathParameters, mock(MultivaluedMap.class), customerState, mock(Metadata.class)), collectionResource, headers, metadata);

        assertNotNull(collectionLinks);
        assertFalse(collectionLinks.isEmpty());
        assertEquals(1, collectionLinks.size());

        Collection<Link> links = entities.get(0).getLinks();
        assertNotNull(links);
        assertFalse(links.isEmpty());

        // sort the links so we have a predictable order for this test
        List<Link> sortedLinks = new ArrayList<Link>();
        sortedLinks.addAll(links);
        Collections.sort(sortedLinks, new Comparator<Link>() {

            @Override
            public int compare(Link o1, Link o2) {
                return o1.getHref().compareTo(o2.getHref());
            }
        });
        assertEquals("/baseuri/contact()?filter=Name+eq+John+and+Id+eq+'johnEmailAddr'", sortedLinks.get(0).getHref());
        assertEquals("/baseuri/contact()?filter=Name+eq+John+and+Id+eq+'smithEmailAddr'", sortedLinks.get(1).getHref());
        assertEquals(2, links.size());
    }

    private OComplexObject createComplexObject(String... values) {
        List<OProperty<?>> propertyList = new ArrayList<OProperty<?>>();
        for (int i = 0; i < values.length; i += 2) {
            OProperty<String> property = OProperties.string(values[i], values[i + 1]);
            propertyList.add(property);
        }
        OComplexObject complexObj = OComplexObjects.create(EdmComplexType.newBuilder().build(), propertyList);
        return complexObj;
    }

    private Transformer getOEntityTransformer(OCollection<?> collection) {
        Map<String, Object> entityProperties = new HashMap<String, Object>();
        entityProperties.put("source_Contact", collection);
        Transformer transformerMock = mock(Transformer.class);
        when(transformerMock.transform(anyObject())).thenReturn(entityProperties);
        return transformerMock;
    }

    @Test
    public void testResolveDynamicResourceSingleParam() {
        ResourceStateMachine rsm = createResourceStateMachineForResolveDynamicResource();
        String[] resourceArgs = new String[] { "{Field}" };
        DynamicResourceState resourceState = new DynamicResourceState("", "", "", resourceArgs);

        Map<String, Object> transitionProperties = new HashMap<String, Object>();
        transitionProperties.put("Field", "AVersion");

        ResourceStateAndParameters result = rsm.resolveDynamicState(resourceState, transitionProperties, null);
        assertEquals("AVersion", result.getParams()[0].getValue());
    }

    @Test
    public void testResolvedDynamicResourceCollectionParam() {
        ResourceStateMachine rsm = createResourceStateMachineForResolveDynamicResource();
        String[] resourceArgs = new String[] { "{Parent.Field}" };
        DynamicResourceState resourceState = new DynamicResourceState("", "", "", resourceArgs);

        Map<String, Object> transitionProperties = new HashMap<String, Object>();
        transitionProperties.put("Parent.Field", "value1");

        ResourceStateAndParameters result = rsm.resolveDynamicState(resourceState, transitionProperties, null);
        assertEquals("value1", result.getParams()[0].getValue());
    }
    
    @Test
    public void testResolvedDynamicResourceMultipleParam() {
        ResourceStateMachine rsm = createResourceStateMachineForResolveDynamicResource();
        String[] resourceArgs = new String[] { "{Field}", "I", "{Product}" };        
        DynamicResourceState resourceState = new DynamicResourceState("", "", "", resourceArgs);

        Map<String, Object> transitionProperties = new HashMap<String, Object>();
        transitionProperties.put("Field", "AVersion");
        transitionProperties.put("Product", "Account");

        ResourceStateAndParameters result = rsm.resolveDynamicState(resourceState, transitionProperties, null);
        assertEquals("AVersion", result.getParams()[0].getValue());
        assertEquals("I", result.getParams()[1].getValue());
        assertEquals("Account", result.getParams()[2].getValue());
    }
    
    @Test
    public void testResolvedDynamicResourceCollectionwithMultipleParam() {
        ResourceStateMachine rsm = createResourceStateMachineForResolveDynamicResource();
        String[] resourceArgs = new String[] { "{Field}", "I", "{Parent.Product}" };        
        DynamicResourceState resourceState = new DynamicResourceState("", "", "", resourceArgs);

        Map<String, Object> transitionProperties = new HashMap<String, Object>();
        transitionProperties.put("Field", "AVersion");
        transitionProperties.put("Parent.Product", "Account");

        ResourceStateAndParameters result = rsm.resolveDynamicState(resourceState, transitionProperties, null);
        assertEquals("AVersion", result.getParams()[0].getValue());
        assertEquals("I", result.getParams()[1].getValue());
        assertEquals("Account", result.getParams()[2].getValue());
    }
    
    @Test
    public void testResolvedDynamicResourceCollectionwithMultipleParamFailure() {
        ResourceStateMachine rsm = createResourceStateMachineForResolveDynamicResource();
        String[] resourceArgs = new String[] { "{Parent1.Field}", "I", "{Parent2.Product}" };        
        DynamicResourceState resourceState = new DynamicResourceState("", "", "", resourceArgs);

        Map<String, Object> transitionProperties = new HashMap<String, Object>();
        transitionProperties.put("Parent1.Field", "AVersion");
        transitionProperties.put("Parent.Product", "Account");

        ResourceStateAndParameters result = rsm.resolveDynamicState(resourceState, transitionProperties, null);
        assertEquals("AVersion", result.getParams()[0].getValue());
        assertEquals("I", result.getParams()[1].getValue());
        assertEquals("Parent2.Product", result.getParams()[2].getValue());
    }

    private ResourceStateMachine createResourceStateMachineForResolveDynamicResource() {
        ResourceParameterResolver parameterResolver = new ResourceParameterResolver() {
            @Override
            public ParameterAndValue[] resolve(Object[] aliases, ResourceParameterResolverContext context) {
                ParameterAndValue[] params = new ParameterAndValue[aliases.length];
                for (int i = 0; i < aliases.length; i++) {
                    String value = aliases[i].toString();
                    params[i] = new ParameterAndValue(value, value);
                }
                return params;
            }
        };
        
        ResourceLocator resourceLocator = new ResourceLocator() {
        	@Override
        	public ResourceState resolve(Object... alias) {
        		return new ResourceState("entityName", "name", new ArrayList<Action>(), "path");
        }};

        ResourceLocatorProvider resourceLocatorProviderMock = mock(ResourceLocatorProvider.class);
        when(resourceLocatorProviderMock.get(anyString())).thenReturn(resourceLocator);

        ResourceParameterResolverProvider parameterResolverMock = mock(ResourceParameterResolverProvider.class);
        when(parameterResolverMock.get(anyString())).thenReturn(parameterResolver);

        ResourceState resState = new ResourceState("entityName", "name", new ArrayList<Action>(), "path");
        ResourceStateMachine rsm = new ResourceStateMachine(resState, resourceLocatorProviderMock);
        rsm.setParameterResolverProvider(parameterResolverMock);

        return rsm;
    }
}
