package com.temenos.interaction.media.hal;

/*
 * #%L
 * interaction-media-hal
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
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.beanutils.PropertyUtils;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLAssert;
import org.joda.time.LocalDateTime;
import org.junit.Test;
import org.odata4j.core.OEntities;
import org.odata4j.core.OEntity;
import org.odata4j.core.OEntityKey;
import org.odata4j.core.OLink;
import org.odata4j.core.OProperties;
import org.odata4j.core.OProperty;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.edm.EdmEntityType;
import org.odata4j.edm.EdmProperty;
import org.odata4j.edm.EdmSimpleType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.command.CommandHelper;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityMetadata;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.entity.vocabulary.Vocabulary;
import com.temenos.interaction.core.entity.vocabulary.terms.TermComplexType;
import com.temenos.interaction.core.entity.vocabulary.terms.TermValueType;
import com.temenos.interaction.core.hypermedia.Action;
import com.temenos.interaction.core.hypermedia.DefaultResourceStateProvider;
import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.ResourceStateProvider;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.resource.CollectionResource;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.MetaDataResource;
import com.temenos.interaction.core.resource.RESTResource;

public class TestHALProvider {

	/*
	 * Test the getSize operation of GET with this provider
	 */
	@Test
	public void testSize() {
		HALProvider hp = new HALProvider(mock(Metadata.class), mock(ResourceStateProvider.class));
		assertEquals(-1, hp.getSize(null, null, null, null, null));
	}

	/** utility method to parse a json string for use in comparisons
	 */
	public Map parseJson(String json) throws IOException {
		//converting json to Map
		byte[] mapData = json.getBytes();
		Map<String,Object> myMap = new HashMap<String, Object>();
		
		ObjectMapper objectMapper = new ObjectMapper();
		myMap = objectMapper.readValue(mapData, HashMap.class);
		return myMap;
	}		

	/*
	 * Test the getSize operation of GET with this provider
	 */
	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialise() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/children");
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);

		String strEntityStream = "<resource href=\"/children\"><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
		assertNotNull(entity.getProperties());
		// string type
		assertEquals("noah", entity.getProperties().getProperty("name").getValue());
		// long type
		// TODO handle non string entity properties
		assertEquals(new Long(2), entity.getProperties().getProperty("age").getValue());
	}
	

	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityName() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/children");		
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);
		
		String strEntityStream = "<resource href=\"/children\"><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityNameJSON() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/children");		
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);

		String strEntityStream = "{ \"_links\": { \"self\": { \"href\": \"http://www.temenos.com/rest.svc/children\" } }, \"name\": \"noah\", \"age\": 2 }";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_JSON_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}
        
	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialisePlainJSON() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm),
										 new com.theoryinpractise.halbuilder.standard.StandardRepresentationFactory().withReader(javax.ws.rs.core.MediaType.APPLICATION_JSON, com.theoryinpractise.halbuilder.json.JsonRepresentationReader.class)
										 );
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/children");		
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);

		String strEntityStream = "{ \"_links\": { \"self\": { \"href\": \"http://www.temenos.com/rest.svc/children\" } }, \"name\": \"noah\", \"age\": 2 }";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}
        
    @SuppressWarnings("unchecked")
	@Test
	public void testNestedDeserialiseResolveEntityNameJSON() throws IOException, URISyntaxException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("students", "initial", new ArrayList<Action>(), "/students"));
		HALProvider hp = new HALProvider(createNestedMockStudentsVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/students");		
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);

		String strEntityStream = "{'_links':{'self':{'href':'http://www.temenos.com/rest.svc/students'}},'name':'noah','age':'2','tuitions':[{'Duration':'2.5','TutionName':'Maths'},{'TutionName':'English','Duration':'2'}]}".replace('\'','\"');

        InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_JSON_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("students", entity.getName());
		assertEquals(2L, PropertyUtils.getProperty(entity, "properties.properties.age.value"));
		assertEquals("noah", PropertyUtils.getProperty(entity, "properties.properties.name.value"));
		assertEquals("2.5", PropertyUtils.getProperty(entity, "properties.properties.tuitions.value[0].properties.Duration.value"));
	}
    
    @SuppressWarnings("unchecked")
	@Test
	public void testMoreComplexNestedDeserialiseResolveEntityNameJSON() throws IOException, URISyntaxException, IllegalAccessException, InvocationTargetException, NoSuchMethodException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("students", "initial", new ArrayList<Action>(), "/students"));
		HALProvider hp = new HALProvider(createMoreComplexNestedMockStudentVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/students");		
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);

		String strEntityStream = "{'_links':{'self':{'href':'http://www.temenos.com/rest.svc/students'}},'name':'noah','age':'2','address': {'houseNumber':'123', 'roadName': 'Fenchurch Street'},'tuitions':[{'Duration':'2.5','TutionName':'Maths'},{'TutionName':'English','Duration':'2', 'Teachers': [{'Name': 'John'}, {'Name': 'Martin'}]}]}".replace('\'','\"');

        InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_JSON_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("students", entity.getName());
		assertEquals(2L, PropertyUtils.getProperty(entity, "properties.properties(age).value"));
		assertEquals("noah", PropertyUtils.getProperty(entity, "properties.properties(name).value"));
		assertEquals("123", PropertyUtils.getProperty(entity, "properties.properties(address).value.properties(houseNumber).value"));
		assertEquals("Fenchurch Street", PropertyUtils.getProperty(entity, "properties.properties(address).value.properties(roadName).value"));
		assertEquals("2.5", PropertyUtils.getProperty(entity, "properties.properties(tuitions).value[0].properties(Duration).value"));
	}
        
    @Test
    public void testIsWritable(){
    	ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider provider = new HALProvider(createNestedMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		GenericEntity<EntityResource<Entity>> entity = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {};
		assertTrue(provider.isWriteable(RESTResource.class, entity.getType(), null, MediaType.APPLICATION_HAL_JSON_TYPE));
		assertTrue(provider.isWriteable(RESTResource.class, entity.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE));
		assertTrue(provider.isWriteable(RESTResource.class, entity.getType(), null, javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE));
    }
    
    @Test
    public void testIsWritableReturnsTrueForCollectionResourceObjects(){
    	ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider provider = new HALProvider(createNestedMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		GenericEntity<CollectionResource<Entity>> entity = new GenericEntity<CollectionResource<Entity>>(new CollectionResource<Entity>()) {};
		assertTrue(provider.isWriteable(RESTResource.class, entity.getType(), null, MediaType.APPLICATION_HAL_JSON_TYPE));
		assertTrue(provider.isWriteable(RESTResource.class, entity.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE));
		assertTrue(provider.isWriteable(RESTResource.class, entity.getType(), null, javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE));
    }
    
    @Test
    public void testIsWritableReturnsFalseForUnwritableTypes(){
    	ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider provider = new HALProvider(createNestedMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		GenericEntity<EntityResource<Entity>> entity = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {};
		assertFalse(provider.isWriteable(RESTResource.class, entity.getType(), null, javax.ws.rs.core.MediaType.APPLICATION_SVG_XML_TYPE));
    }
    
    @Test
    public void testIsWritableReturnsFalseForNonEntityOrCollectionTypes(){
    	ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider provider = new HALProvider(createNestedMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		GenericEntity<EntityResource<Entity>> entity = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {};
		assertFalse(provider.isWriteable(RESTResource.class, entity.getType(), null, javax.ws.rs.core.MediaType.APPLICATION_SVG_XML_TYPE));
    }
	
	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityNameWithId() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children/{id}", "id", null));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/children/123");
		MultivaluedMap<String, String> mockPathParameters = new MultivaluedMapImpl<String>();
		when(mockUriInfo.getPathParameters()).thenReturn(mockPathParameters);
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);
		
		String strEntityStream = "<resource href=\"/children/123\"><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityNameWithIdODataPath() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children({id})/updated", "id", null));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
		when(mockUriInfo.getPath()).thenReturn("/children(123)/updated");		
		MultivaluedMap<String, String> mockPathParameters = new MultivaluedMapImpl<String>();
		mockPathParameters.add("id", "123");
		when(mockUriInfo.getPathParameters()).thenReturn(mockPathParameters);
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);

		String strEntityStream = "<resource href=\"/children(123)/updated\"><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}
	
	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityNameUriInfo() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children"));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		when(mockUriInfo.getPath()).thenReturn("/children");
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);
		
		String strEntityStream = "<resource><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityNameUriInfoRelative() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/Orders()"));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		when(mockUriInfo.getPath()).thenReturn("Orders()");
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);

		String strEntityStream = "<resource><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityNameUriInfoWithId() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children/{id}", "id", null));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		when(mockUriInfo.getPath()).thenReturn("/children/123");
		MultivaluedMap<String, String> mockPathParameters = new MultivaluedMapImpl<String>();
		mockPathParameters.add("id", "123");
		when(mockUriInfo.getPathParameters()).thenReturn(mockPathParameters);
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);
		
		String strEntityStream = "<resource><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testDeserialiseResolveEntityNameUriInfoWithIdODataPath() throws IOException, URISyntaxException {
		ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), "/children({id})/updated", "id", null));
		HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		when(mockUriInfo.getPath()).thenReturn("/children(123)/updated");
		MultivaluedMap<String, String> mockPathParameters = new MultivaluedMapImpl<String>();
		mockPathParameters.add("id", "123");
		when(mockUriInfo.getPathParameters()).thenReturn(mockPathParameters);
		hp.setUriInfo(mockUriInfo);
		Request requestContext = mock(Request.class);
		when(requestContext.getMethod()).thenReturn("GET");
		hp.setRequestContext(requestContext);
		
		String strEntityStream = "<resource><name>noah</name><age>2</age></resource>";
		InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
		GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
		EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, MediaType.APPLICATION_HAL_XML_TYPE, null, entityStream);
		assertNotNull(er.getEntity());
		Entity entity = er.getEntity();
		assertEquals("Children", entity.getName());
	}

	private Metadata createMockChildVocabMetadata() {
		EntityMetadata vocs = new EntityMetadata("Children");
		Vocabulary vocId = new Vocabulary();
		vocId.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("name", vocId);
		Vocabulary vocBody = new Vocabulary();
		vocBody.setTerm(new TermValueType(TermValueType.INTEGER_NUMBER));
		vocs.setPropertyVocabulary("age", vocBody);
		Vocabulary vocBirth = new Vocabulary();
		vocBirth.setTerm(new TermValueType(TermValueType.DATE));
        vocs.setPropertyVocabulary("dateOfBirth", vocBirth);
		Metadata metadata = new Metadata("Family");
		metadata.setEntityMetadata(vocs);
		return metadata;
	}
        
    private Metadata createNestedMockChildVocabMetadata() {
		EntityMetadata vocs = new EntityMetadata("Children");
		Vocabulary vocId = new Vocabulary();
		vocId.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("name", vocId);
		Vocabulary vocBody = new Vocabulary();
		vocBody.setTerm(new TermValueType(TermValueType.INTEGER_NUMBER));
		vocs.setPropertyVocabulary("age", vocBody);
                
        Vocabulary vocRides = new Vocabulary();
		vocRides.setTerm(new TermComplexType(true));
		vocs.setPropertyVocabulary("tuitions", vocRides);
		Vocabulary vocTutionName = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("TutionName", vocTutionName, Collections.enumeration(Collections.singletonList("tuitions")));
		Vocabulary vocTutionDuration = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("Duration", vocTutionDuration, Collections.enumeration(Collections.singletonList("tuitions")));
		
		Metadata metadata = new Metadata("Family");
		metadata.setEntityMetadata(vocs);
		return metadata;
	}
    
    private Metadata createNestedMockStudentsVocabMetadata() {
		EntityMetadata vocs = new EntityMetadata("students");
		Vocabulary vocId = new Vocabulary();
		vocId.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("name", vocId);
		Vocabulary vocBody = new Vocabulary();
		vocBody.setTerm(new TermValueType(TermValueType.INTEGER_NUMBER));
		vocs.setPropertyVocabulary("age", vocBody);
                
        Vocabulary vocRides = new Vocabulary();
		vocRides.setTerm(new TermComplexType(true));
		vocs.setPropertyVocabulary("tuitions", vocRides);
		Vocabulary vocTutionName = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("TutionName", vocTutionName, Collections.enumeration(Collections.singletonList("tuitions")));
		Vocabulary vocTutionDuration = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("Duration", vocTutionDuration, Collections.enumeration(Collections.singletonList("tuitions")));
		
		Metadata metadata = new Metadata("Faculty");
		metadata.setEntityMetadata(vocs);
		return metadata;
	}

    
    private Metadata createMoreComplexNestedMockStudentVocabMetadata() {
		EntityMetadata vocs = new EntityMetadata("students");
		Vocabulary vocId = new Vocabulary();
		vocId.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("name", vocId);
		Vocabulary vocBody = new Vocabulary();
		vocBody.setTerm(new TermValueType(TermValueType.INTEGER_NUMBER));
		vocs.setPropertyVocabulary("age", vocBody);
                
        Vocabulary vocRides = new Vocabulary();
		vocRides.setTerm(new TermComplexType(true));
		vocs.setPropertyVocabulary("tuitions", vocRides);
		Vocabulary vocTeachers = new Vocabulary();
		vocTeachers.setTerm(new TermComplexType(true));
		vocs.setPropertyVocabulary("Teachers", vocTeachers, Collections.enumeration(Collections.singletonList("tuitions")));
		Vocabulary vocTutionName = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("TutionName", vocTutionName, Collections.enumeration(Collections.singletonList("tuitions")));
		Vocabulary vocTutionDuration = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("Duration", vocTutionDuration, Collections.enumeration(Collections.singletonList("tuitions")));
		
		Vocabulary vocTeacherName = new Vocabulary();
		vocTeacherName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("Name", vocTeacherName, Collections.enumeration(Arrays.asList(new String[]{"tuitions","Teachers"})));
		
		Vocabulary vocAddress = new Vocabulary();
		vocAddress.setTerm(new TermComplexType(true));
		vocs.setPropertyVocabulary("address", vocAddress);
		
		Vocabulary vocAddressHouseNumber = new Vocabulary();
		vocAddressHouseNumber.setTerm(new TermValueType(TermValueType.NUMBER));
		vocs.setPropertyVocabulary("houseNumber", vocAddressHouseNumber, Collections.enumeration(Arrays.asList(new String[]{"address"})));
		
		Vocabulary vocAddressRoadName = new Vocabulary();
		vocAddressRoadName.setTerm(new TermValueType(TermValueType.NUMBER));
		vocs.setPropertyVocabulary("roadName", vocAddressRoadName, Collections.enumeration(Arrays.asList(new String[]{"address"})));
		
		Metadata metadata = new Metadata("Faculty");
		metadata.setEntityMetadata(vocs);
		return metadata;
	}
    
    private Metadata createObjectNestedVocabMetadataStub(){
		EntityMetadata vocs = new EntityMetadata("Children");
		Vocabulary vocId = new Vocabulary();
		vocId.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("name", vocId);
		Vocabulary vocBody = new Vocabulary();
		vocBody.setTerm(new TermValueType(TermValueType.INTEGER_NUMBER));
		vocs.setPropertyVocabulary("age", vocBody);
                
        Vocabulary vocRides = new Vocabulary();
		vocRides.setTerm(new TermComplexType(true));
		vocs.setPropertyVocabulary("tuitions", vocRides);
		Vocabulary vocTutionName = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("TutionName", vocTutionName, Collections.enumeration(Collections.singletonList("tuitions")));
		Vocabulary vocTutionDuration = new Vocabulary();
		vocTutionName.setTerm(new TermValueType(TermValueType.TEXT));
		vocs.setPropertyVocabulary("Duration", vocTutionDuration, Collections.enumeration(Collections.singletonList("tuitions")));
		
		Vocabulary vocAddress = new Vocabulary();
		vocAddress.setTerm(new TermComplexType(true));
		vocs.setPropertyVocabulary("address", vocAddress);
		
		Vocabulary vocAddressHouseNumber = new Vocabulary();
		vocAddressHouseNumber.setTerm(new TermValueType(TermValueType.NUMBER));
		vocs.setPropertyVocabulary("houseNumber", vocAddressHouseNumber, Collections.enumeration(Arrays.asList(new String[]{"address"})));
		
		Vocabulary vocAddressRoadName = new Vocabulary();
		vocAddressRoadName.setTerm(new TermValueType(TermValueType.NUMBER));
		vocs.setPropertyVocabulary("roadName", vocAddressRoadName, Collections.enumeration(Arrays.asList(new String[]{"address"})));
		
		Metadata metadata = new Metadata("Family");
		metadata.setEntityMetadata(vocs);
		return metadata;
    }
	
	@Test(expected = WebApplicationException.class)
	public void testAttemptToSerialiseNonEntityResource() throws Exception {
		EntityResource<?> mdr = mock(EntityResource.class);

		HALProvider hp = new HALProvider(mock(Metadata.class));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		hp.writeTo(mdr, MetaDataResource.class, null, null, MediaType.APPLICATION_HAL_XML_TYPE, null, new ByteArrayOutputStream());
	}
	
	@Test
	public void testSerialiseSimpleResource() throws Exception {
		// the test key
		OEntityKey entityKey = OEntityKey.create("123");
		// the test properties
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("name", "noah"));
		properties.add(OProperties.string("age", "2"));

		OEntity entity = OEntities.create(createMockChildrenEntitySet(), entityKey, properties, new ArrayList<OLink>());
		EntityResource<OEntity> er = CommandHelper.createEntityResource(entity, OEntity.class);
		er.setEntityName("Children");

		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, OEntity.class, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\"><name>noah</name><age>2</age></resource>";
		String responseString = createFlatXML(bos);
		
		Diff diff = new Diff(expectedXML, responseString);
		// don't worry about the order of the elements in the xml
		assertTrue(diff.similar());
	}

	@Test
	public void testSerialiseCollectionResource() throws Exception {
		// the test key
		OEntityKey entityKey = OEntityKey.create("123");
		// the test properties
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("name", "noah"));
		properties.add(OProperties.string("age", "2"));

		Collection<EntityResource<OEntity>> entities = new ArrayList<EntityResource<OEntity>>();
		entities.add(createEntityResourceWithSelfLink(entityKey, properties, "http://www.temenos.com/rest.svc/children/1"));
		entities.add(createEntityResourceWithSelfLink(entityKey, properties, "http://www.temenos.com/rest.svc/children/2"));
		entities.add(createEntityResourceWithSelfLink(entityKey, properties, "http://www.temenos.com/rest.svc/children/3"));
		CollectionResource<OEntity> er = new CollectionResource<OEntity>("Children", entities);
		// mock setting entity name
		er.setEntityName("Children");
		
		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, CollectionResource.class, OEntity.class, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\"><resource href=\"http://www.temenos.com/rest.svc/children/1\" rel=\"item\"><age>2</age><name>noah</name></resource><resource href=\"http://www.temenos.com/rest.svc/children/2\" rel=\"item\"><age>2</age><name>noah</name></resource><resource href=\"http://www.temenos.com/rest.svc/children/3\" rel=\"item\"><age>2</age><name>noah</name></resource></resource>";
		String responseString = createFlatXML(bos);
		
		Diff diff = new Diff(expectedXML, responseString);
		// don't worry about the order of the elements in the xml
		assertTrue(diff.similar());
	}

	@Test
	public void testSerialiseEmbeddedResources() throws Exception {
		OEntityKey parentEntityKey = OEntityKey.create("333");
		List<OProperty<?>> parentProperties = new ArrayList<OProperty<?>>();
		parentProperties.add(OProperties.string("name", "aaron"));
		parentProperties.add(OProperties.string("age", "30"));

		OEntityKey childEntityKey = OEntityKey.create("123");
		List<OProperty<?>> childProperties = new ArrayList<OProperty<?>>();
		childProperties.add(OProperties.string("name", "noah"));
		childProperties.add(OProperties.string("age", "2"));

		OEntity childEntity = OEntities.create(createMockChildrenEntitySet(), childEntityKey, childProperties, new ArrayList<OLink>());
		EntityResource<OEntity> childEntityResource = CommandHelper.createEntityResource(childEntity, OEntity.class);
		childEntityResource.setEntityName("Children");

		OEntity parentEntity = OEntities.create(createMockChildrenEntitySet(), parentEntityKey, parentProperties, new ArrayList<OLink>());
		EntityResource<OEntity> parentEntityResource = CommandHelper.createEntityResource(parentEntity, OEntity.class);
		parentEntityResource.setEntityName("Children");

		
		// build the embedded map
		Map<Transition, RESTResource> embedded = new HashMap<Transition, RESTResource>();
		Transition childToParent = new Transition.Builder()
				.source(new ResourceState("PERSON", "child", new ArrayList<Action>(), "/child/{id}"))
				.target(new ResourceState("PERSON", "parent", new ArrayList<Action>(), "/parent/{id}"))
				.build();
		embedded.put(childToParent, parentEntityResource);
		// child has a parent
		childEntityResource.setEmbedded(embedded);
		
		// mock the test links
		List<Link> links = new ArrayList<Link>();
		links.add(mockLink("child", "self", "/child/123", null));
		links.add(mockLink("parent", "person", "/parent/333", childToParent));
		childEntityResource.setLinks(links);
		
		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(childEntityResource, EntityResource.class, OEntity.class, null, MediaType.APPLICATION_HAL_JSON_TYPE, null, bos);

		String expectedXML = "{\"_links\":{\"self\":{\"href\":\"/child/123\"},\"person\":[{\"href\":\"/parent/333\",\"name\":\"parent\",\"title\":\"parent\"}]},\"age\":\"2\",\"name\":\"noah\",\"_embedded\":{\"person\":[{\"_links\":{\"self\":{\"href\":\"/parent/333\"}},\"age\":\"30\",\"name\":\"aaron\"}]}}";
		String responseString = new String(bos.toByteArray(), "UTF-8");
		responseString = responseString.replaceAll(System.getProperty("line.separator"), "");


		Map<String,Object> expectedData = parseJson(expectedXML);
		Map<String,Object> actualData   = parseJson(responseString);
		
		assertEquals(expectedData, actualData);
	}

	private EntityResource<OEntity> createEntityResourceWithSelfLink(OEntityKey entityKey, List<OProperty<?>> properties, String selfLink) {
		OEntity oentity = OEntities.create(createMockChildrenEntitySet(), entityKey, properties, new ArrayList<OLink>());
		EntityResource<OEntity> entityResource = new EntityResource<OEntity>(oentity);
		Collection<Link> links = new ArrayList<Link>();
		links.add(new Link("id", "self", selfLink, null, null));
		entityResource.setLinks(links);
		return entityResource;
	}
	
	@Test
	public void testSerialiseBeanResource() throws Exception {
		// the test bean, with elements that should not be serialised
		Children person = new Children("noah", 2, "42");
		EntityResource<Children> er = new EntityResource<Children>(person);
		er.setEntityName("Children");

		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, Children.class, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\"><name>noah</name><age>2</age></resource>";
		String responseString = createFlatXML(bos);
		
		Diff diff = new Diff(expectedXML, responseString);
		// don't worry about the order of the elements in the xml
		assertTrue(diff.similar());
	}

	@Test
	public void testSerialiseNullBeanResource() throws Exception {
		// null object
		EntityResource<Object> er = new EntityResource<Object>(null);

		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		hp.writeTo(er, EntityResource.class, Object.class, null, MediaType.APPLICATION_HAL_XML_TYPE, null, new ByteArrayOutputStream());
	}

	private String createFlatXML(ByteArrayOutputStream bos) throws Exception {
		String responseString = new String(bos.toByteArray(), "UTF-8");
		responseString = responseString.replaceAll(System.getProperty("line.separator"), "");
		responseString = responseString.replaceAll(">\\s+<", "><");
		return responseString;
	}
	
	private Link mockLink(String id, String rel, String href, Transition transition) {
		Link link = mock(Link.class);
		when(link.getId()).thenReturn(id);
		when(link.getTitle()).thenReturn(id);
		when(link.getRel()).thenReturn(rel);
		when(link.getHref()).thenReturn(href);
		when(link.getTransition()).thenReturn(transition);
        return link;
	}
	
	@Test
	public void testSerialiseResourceNoEntity() throws Exception {
		EntityResource<?> er = mock(EntityResource.class);
		when(er.getEntity()).thenReturn(null);
		
		HALProvider hp = new HALProvider(mock(Metadata.class));
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, null, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com\"></resource>";
		String responseString = createFlatXML(bos);
		XMLAssert.assertXMLEqual(expectedXML, responseString);		
	}

	@Test
	public void testBaseUri() throws Exception {
		EntityResource<?> er = mock(EntityResource.class);
		when(er.getEntity()).thenReturn(null);
		
		HALProvider hp = new HALProvider(mock(Metadata.class));
		UriInfo mockUriInfo = mock(UriInfo.class);
		// java 1.6 bug getBaseUri returns absolute path
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, null, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\"></resource>";
		String responseString = createFlatXML(bos);
		XMLAssert.assertXMLEqual(expectedXML, responseString);		
	}

	@Test
	public void testJSONMediaTypeWithCharset() throws Exception {
		EntityResource<?> er = mock(EntityResource.class);
		when(er.getEntity()).thenReturn(null);
		
		HALProvider hp = new HALProvider(mock(Metadata.class));
		UriInfo mockUriInfo = mock(UriInfo.class);
		// java 1.6 bug getBaseUri returns absolute path
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, null, null, javax.ws.rs.core.MediaType.valueOf("application/hal+json; charset=utf-8"), null, bos);

		String expectedXML = "{\"_links\":{\"self\":{\"href\":\"http://www.temenos.com/rest.svc/\"}}}";
		String responseString = new String(bos.toByteArray(), "UTF-8");
		responseString = responseString.replaceAll(System.getProperty("line.separator"), "");
		assertEquals(expectedXML, responseString);
	}

	@Test
	public void testXMLMediaTypeWithCharset() throws Exception {
		EntityResource<?> er = mock(EntityResource.class);
		when(er.getEntity()).thenReturn(null);
		
		HALProvider hp = new HALProvider(mock(Metadata.class));
		UriInfo mockUriInfo = mock(UriInfo.class);
		// java 1.6 bug getBaseUri returns absolute path
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, null, null, javax.ws.rs.core.MediaType.valueOf("application/hal+xml; charset=utf-8"), null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\"></resource>";
		String responseString = createFlatXML(bos);
		XMLAssert.assertXMLEqual(expectedXML, responseString);		
	}

	@Test
	public void testSerialiseResourceWithLinks() throws Exception {
		// the test key
		OEntityKey entityKey = OEntityKey.create("123");
		// the test properties
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("name", "noah"));
		properties.add(OProperties.string("age", "2"));
		// the test links
		List<Link> links = new ArrayList<Link>();
		links.add(mockLink("father", "_person", "humans/31", null));
		links.add(mockLink("mother", "_person", "/rest.svc/humans/32", null));
		
		OEntity entity = OEntities.create(createMockChildrenEntitySet(), entityKey, properties, new ArrayList<OLink>());
		EntityResource<OEntity> er = CommandHelper.createEntityResource(entity, OEntity.class);
		er.setEntityName("Children");
		er.setLinks(links);
		
		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, OEntity.class, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\"><link href=\"humans/31\" rel=\"_person\" name=\"father\" title=\"father\"/><link href=\"/rest.svc/humans/32\" rel=\"_person\" name=\"mother\" title=\"mother\"/><name>noah</name><age>2</age></resource>";
		String responseString = createFlatXML(bos);
		
		Diff diff = new Diff(expectedXML, responseString);
		// don't worry about the order of the elements in the xml
		assertTrue(diff.similar());
	}

	@Test
	public void testSerialiseResourceWithRelatedLinks() throws Exception {
		// the test key
		OEntityKey entityKey = OEntityKey.create("123");
		// the test properties
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("name", "noah"));
		properties.add(OProperties.string("age", "2"));
		// the test links
		/*
		 * Not sure, but I think relatedEntity and link are the same thing in an OEntity.
		 * However, a relatedEntity also has the relatedEntityInline capability.
		 * TODO add tests for 'inline' links
		 */
		List<Link> links = new ArrayList<Link>();
		links.add(mockLink("father", "_person", "humans/31", null));
		links.add(mockLink("mother", "_person", "humans/32", null));
		links.add(mockLink("siblings", "_family", "humans/phetheans", null));
		
		OEntity entity = OEntities.create(createMockChildrenEntitySet(), entityKey, properties, new ArrayList<OLink>());
		EntityResource<OEntity> er = new EntityResource<OEntity>(entity);
		er.setEntityName("Children");
		er.setLinks(links);
		
		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, OEntity.class, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\"><link href=\"humans/phetheans\" rel=\"_family\" name=\"siblings\" title=\"siblings\"/><link href=\"humans/31\" rel=\"_person\" name=\"father\" title=\"father\"/><link href=\"humans/32\" rel=\"_person\" name=\"mother\" title=\"mother\"/><name>noah</name><age>2</age></resource>";
		String responseString = createFlatXML(bos);
		
		Diff diff = new Diff(expectedXML, responseString);
		// don't worry about the order of the elements in the xml
		assertTrue(diff.similar());
	}

	@Test
	public void testSerialiseResourceWithDoubleBarrelledRelatedLinks() throws Exception {
		// the test key
		OEntityKey entityKey = OEntityKey.create("123");
		// the test properties
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("name", "noah"));
		properties.add(OProperties.string("age", "2"));
		// the test links
		/*
		 * Not sure, but I think relatedEntity and link are the same thing in an OEntity.
		 * However, a relatedEntity also has the relatedEntityInline capability.
		 * TODO add tests for 'inline' links
		 */
		List<Link> links = new ArrayList<Link>();
		links.add(mockLink("father", "_person edit", "humans/31", null));
		links.add(mockLink("mother", "_person edit", "humans/32", null));
		links.add(mockLink("siblings", "_family", "humans/phetheans", null));
		
		OEntity entity = OEntities.create(createMockChildrenEntitySet(), entityKey, properties, new ArrayList<OLink>());
		EntityResource<OEntity> er = new EntityResource<OEntity>(entity);
		er.setEntityName("Children");
		er.setLinks(links);
		
		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		UriInfo mockUriInfo = mock(UriInfo.class);
		when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc/"));
		hp.setUriInfo(mockUriInfo);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		hp.writeTo(er, EntityResource.class, OEntity.class, null, MediaType.APPLICATION_HAL_XML_TYPE, null, bos);

		String expectedXML = "<resource href=\"http://www.temenos.com/rest.svc/\">"
				+ "<link href=\"humans/phetheans\" rel=\"_family\" name=\"siblings\" title=\"siblings\"/><link href=\"humans/31\" rel=\"_person\" name=\"father\" title=\"father\"/><link href=\"humans/32\" rel=\"_person\" name=\"mother\" title=\"mother\"/>"
				+ "<link rel=\"edit\" href=\"humans/31\" name=\"father\" title=\"father\" /><link rel=\"edit\" href=\"humans/32\" name=\"mother\" title=\"mother\" />"
				+ "<name>noah</name><age>2</age></resource>";
		String responseString = createFlatXML(bos);
		
		Diff diff = new Diff(expectedXML, responseString);
		// don't worry about the order of the elements in the xml
		assertTrue(diff.similar());
	}

	@Test
	public void testBuildMapFromOEntity() throws ParseException {
		HALProvider hp = new HALProvider(createMockChildVocabMetadata());
		
		/* 
		 * create an OEntity with more properties than the 
		 * supplied metadata.  The point is that we should only
		 * fill the map with the defined entities properties.
		 */
		OEntityKey entityKey = OEntityKey.create("123");
		List<OProperty<?>> properties = new ArrayList<OProperty<?>>();
		properties.add(OProperties.string("name", "noah"));
		properties.add(OProperties.int32("age", 2));
		properties.add(OProperties.int32("shoeSize", 5));
		
		SimpleDateFormat sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss");
		String dateInString = "31-08-2016 10:20:56";
		Date date = sdf.parse(dateInString);
		
		properties.add(OProperties.datetime("dateOfBirth", date));
		OEntity entity = OEntities.create(createMockChildrenEntitySet(), entityKey, properties, new ArrayList<OLink>());
		
		// map of property name to value object
		Map<String, Object> map = new HashMap<String, Object>();
		hp.buildFromOEntity(map, entity, "Children");
		
		assertEquals(3, map.keySet().size());
		assertTrue(map.keySet().contains("name"));
		assertTrue(map.keySet().contains("age"));
		assertTrue(map.keySet().contains("dateOfBirth"));
		assertEquals("noah", map.get("name"));
		assertEquals(2, map.get("age"));		
		assertTrue(map.get("dateOfBirth").equals(new LocalDateTime(date.getTime())));
	}
	
	@Test
	public void testSerialiseResourceWithForm() {
		// don't know how to deal with forms yet, possibly embed an xform
	}

	@Test
	public void testSerialiseStreamingResource() {
		// cannot decorate a streaming resource so should fail
	}
	
	@Test
    public void testDeserialiseResourceWithSelfLinkDifferentFromRequestedResourceLinkForApplicationJson() throws IOException, URISyntaxException {
        EntityResource<Entity> er = runDeserialiseResourceWithLinks(javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE, "http://www.temenos.com/rest.svc/name", "/children");
        assertNotNull(er.getEntity());
        Entity entity = er.getEntity();
        assertEquals("Children", entity.getName());
    }
    
    @Test
    public void testDeserialiseResourceWithSelfLinkDifferentFromRequestedResourceLinkForApplicationHalJson() throws IOException, URISyntaxException {
        EntityResource<Entity> er = runDeserialiseResourceWithLinks(MediaType.APPLICATION_HAL_JSON_TYPE, "http://www.temenos.com/rest.svc/name", "/children");
        assertNotNull(er.getEntity());
        Entity entity = er.getEntity();
        assertEquals("Children", entity.getName());
    }
    
    @SuppressWarnings("unchecked")
    private EntityResource<Entity> runDeserialiseResourceWithLinks(javax.ws.rs.core.MediaType mediaType, String selfLink, String targetResource) throws IOException, URISyntaxException {
        ResourceStateMachine sm = new ResourceStateMachine(new ResourceState("Children", "initial", new ArrayList<Action>(), targetResource));
        HALProvider hp = new HALProvider(createMockChildVocabMetadata(), new DefaultResourceStateProvider(sm));
        UriInfo mockUriInfo = mock(UriInfo.class);
        when(mockUriInfo.getBaseUri()).thenReturn(new URI("http://www.temenos.com/rest.svc"));
        when(mockUriInfo.getPath()).thenReturn(targetResource);        
        hp.setUriInfo(mockUriInfo);
        Request requestContext = mock(Request.class);
        when(requestContext.getMethod()).thenReturn("GET");
        hp.setRequestContext(requestContext);

        String strEntityStream = "{ \"_links\": { \"self\": { \"href\": \"" + selfLink + "\" } }, \"name\": \"noah\", \"age\": 2 }";
        InputStream entityStream = new ByteArrayInputStream(strEntityStream.getBytes());
        GenericEntity<EntityResource<Entity>> ge = new GenericEntity<EntityResource<Entity>>(new EntityResource<Entity>()) {}; 
        EntityResource<Entity> er = (EntityResource<Entity>) hp.readFrom(RESTResource.class, ge.getType(), null, mediaType, null, entityStream);
        return er;
    }
	
	private EdmEntitySet createMockChildrenEntitySet() {
		// mock a simple entity (Children entity set)
		List<EdmProperty.Builder> eprops = new ArrayList<EdmProperty.Builder>();
		eprops.add(EdmProperty.newBuilder("ID").setType(EdmSimpleType.STRING));
		eprops.add(EdmProperty.newBuilder("name").setType(EdmSimpleType.STRING));
		eprops.add(EdmProperty.newBuilder("age").setType(EdmSimpleType.STRING));
		eprops.add(EdmProperty.newBuilder("dateOfBirth").setType(EdmSimpleType.DATETIME));
		EdmEntityType.Builder eet = EdmEntityType.newBuilder().setNamespace("InteractionTest").setName("Children").addKeys(Arrays.asList("ID")).addProperties(eprops);
		EdmEntitySet.Builder ees = EdmEntitySet.newBuilder().setName("Children").setEntityType(eet);
		return ees.build();
	}
}
