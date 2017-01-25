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


import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.apache.commons.lang.StringUtils;
import org.odata4j.core.OCollection;
import org.odata4j.core.OComplexObject;
import org.odata4j.core.OEntity;
import org.odata4j.core.OObject;
import org.odata4j.core.OProperty;
import org.odata4j.core.OSimpleObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.temenos.interaction.core.UriInfoImpl;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.entity.Entity;
import com.temenos.interaction.core.entity.EntityMetadata;
import com.temenos.interaction.core.entity.EntityProperties;
import com.temenos.interaction.core.entity.EntityProperty;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.entity.vocabulary.terms.TermMandatory;
import com.temenos.interaction.core.hypermedia.DefaultResourceStateProvider;
import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.MethodNotAllowedException;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.hypermedia.ResourceStateMachine;
import com.temenos.interaction.core.hypermedia.ResourceStateProvider;
import com.temenos.interaction.core.hypermedia.Transition;
import com.temenos.interaction.core.resource.CollectionResource;
import com.temenos.interaction.core.resource.EntityResource;
import com.temenos.interaction.core.resource.RESTResource;
import com.temenos.interaction.core.resource.ResourceTypeHelper;
import com.theoryinpractise.halbuilder.api.ReadableRepresentation;
import com.theoryinpractise.halbuilder.api.Representation;
import com.theoryinpractise.halbuilder.api.RepresentationException;
import com.theoryinpractise.halbuilder.api.RepresentationFactory;
import com.theoryinpractise.halbuilder.json.JsonRepresentationReader;
import com.theoryinpractise.halbuilder.json.JsonRepresentationWriter;
import com.theoryinpractise.halbuilder.standard.StandardRepresentationFactory;

@Provider
@Consumes({HALMediaType.APPLICATION_HAL_XML, HALMediaType.APPLICATION_HAL_JSON, MediaType.APPLICATION_JSON})
@Produces({HALMediaType.APPLICATION_HAL_XML, HALMediaType.APPLICATION_HAL_JSON, MediaType.APPLICATION_JSON})
public class HALProvider implements MessageBodyReader<RESTResource>, MessageBodyWriter<RESTResource> {
	private final Logger logger = LoggerFactory.getLogger(HALProvider.class);

	@Context
	private UriInfo uriInfo;
	@Context
	private Request requestContext;
	private Metadata metadata = null;
	private ResourceStateProvider resourceStateProvider;
    private RepresentationFactory representationFactory;

	public HALProvider(Metadata metadata, ResourceStateProvider resourceStateProvider) {
		this(metadata);
		this.resourceStateProvider = resourceStateProvider;
	}

	public HALProvider(Metadata metadata, ResourceStateProvider resourceStateProvider, RepresentationFactory representationFactory) {
		this(metadata, representationFactory);
		this.resourceStateProvider = resourceStateProvider;
	}			

	@Deprecated
	public HALProvider(Metadata metadata, ResourceStateMachine rsm) {
		this(metadata);
		this.resourceStateProvider = new DefaultResourceStateProvider(rsm);
	}

	public HALProvider(Metadata metadata) {
		this(metadata, irisRepresentationFactory());
		this.metadata = metadata;
		assert(metadata != null);
	}

	public HALProvider(Metadata metadata, RepresentationFactory representationFactory) {
		this.metadata = metadata;
		this.representationFactory = representationFactory;
	}

	private static RepresentationFactory irisRepresentationFactory() {
		return new StandardRepresentationFactory().
			withReader(MediaType.APPLICATION_JSON, JsonRepresentationReader.class).
			withRenderer(MediaType.APPLICATION_JSON, JsonRepresentationWriter.class);
	}
	
	@Override
	public boolean isWriteable(Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		if (mediaType.equals(HALMediaType.APPLICATION_HAL_XML_TYPE)
				|| mediaType.equals(HALMediaType.APPLICATION_HAL_JSON_TYPE)
				|| mediaType.equals(MediaType.APPLICATION_JSON_TYPE)) {
			return ResourceTypeHelper.isType(type, genericType, EntityResource.class)
					|| ResourceTypeHelper.isType(type, genericType, CollectionResource.class);
		}
		return false;
	}

	@Override
	public long getSize(RESTResource t, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		return -1;
	}
	
	private Representation buildHalResource(URI id, RESTResource resource, Class<?> type, Type genericType) throws URISyntaxException {
		logger.debug("buildHalResource({})", id);
		if (!ResourceTypeHelper.isType(type, genericType, EntityResource.class)
				&& !ResourceTypeHelper.isType(type, genericType, CollectionResource.class))
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);

		// create the hal resource
        Representation halResource = representationFactory.newRepresentation(id);
		if (resource.getGenericEntity() != null) {
			// get the links
			Collection<Link> links = resource.getLinks();
			Link selfLink = findSelfLink(links);
			
			// build the HAL representation with self link
			if (selfLink != null)
				halResource = representationFactory.newRepresentation(selfLink.getHref());

			// add our links
			if (links != null) {
				for (Link l : links) {
					if (l.equals(selfLink))
						continue;
					logger.debug("Link: id=[" + l.getId() + "] rel=[" + l.getRel() +
								 "] method=[" + l.getMethod() + "] href=[" + l.getHref() + "]");

					String[] rels = new String[0];
					if (l.getRel() != null) {
						rels = l.getRel().split(" ");
					}
					
					if (rels != null) {
						for (int i = 0 ; i < rels.length; i++) {
							halResource.withLink(rels[i], l.getHref(), l.getId(), l.getTitle(), null, null); 
						}
					}
				}
			}
			
			// add the embedded resources
			Map<Transition, RESTResource> embedded = resource.getEmbedded();
			if (embedded != null) {
				for (Transition t : embedded.keySet()) {
					RESTResource embeddedResource = embedded.get(t);
					// TODO work our rel for embedded resource, just as we need to work out the rel for the other links
					Link link = findLinkByTransition(links, t);
					// Check link for null before using it
					if(link!=null) {
						String rel = (link.getRel() != null ? link.getRel() : "embedded/" + embeddedResource.getEntityName());
						logger.debug("Embedded resource: rel=[" + rel + "] href=[" + link.getHref() + "]");

						Representation embeddedRepresentation = buildHalResource(new URI(link.getHref()),
																											embeddedResource,
																											embeddedResource.getGenericEntity().getRawType(),
																											embeddedResource.getGenericEntity().getType());
						halResource.withRepresentation(rel, embeddedRepresentation);
					}
				}
			}

			// add contents of supplied entity to the representation
			buildRepresentation(halResource, resource, type, genericType);

		} else {
			logger.warn("Resource with URI {} has null genericEntity--no output produced", id);
		}
				
		return halResource;
	}
 
	/**
	 * Writes a Hypertext Application Language (HAL) representation of
	 * {@link EntityResource} to the output stream.
	 * 
	 * @precondition supplied {@link EntityResource} is non null
	 * @precondition {@link EntityResource#getEntity()} returns a valid OEntity, this 
	 * provider only supports serialising OEntities
	 * @postcondition non null HAL XML document written to OutputStream
	 * @invariant valid OutputStream
	 */
	@Override
	public void writeTo(RESTResource resource, Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, Object> httpHeaders,
			OutputStream entityStream) throws IOException,
			WebApplicationException {
		logger.debug("Writing " + mediaType);
		Representation halResource;
		try {
			halResource = buildHalResource(uriInfo.getBaseUri(), resource, type, genericType);
		}
		catch(URISyntaxException e) {
			logger.error("Invalid link syntax", e);
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
		String baseMediaType = HALMediaType.baseMediaType( mediaType );
		String representation = halResource.toString(baseMediaType);
		String charset = HALMediaType.charset( mediaType, "UTF-8" );

		logger.debug("Produced [" + representation + "]");

		entityStream.write(representation.getBytes(charset));
	}

	private Link findLinkByTransition(Collection<Link> links, Transition transition) {
		Link link = null;
		if (links != null) {
			for (Link l : links) {
				if (l.getTransition() != null && l.getTransition().equals(transition)) {
					link = l;
					break;
				}
			}
		}
		return link;
	}
	
	protected Link findSelfLink(Collection<Link> links) {
		Link selfLink = null;
		if (links != null) {
			for (Link l : links) {
				Transition t = l.getTransition();
				// TODO this bit is a bit hacky.
				// The latest version of the HAL spec should not require us to find a 'self' link for the subresource
				if (l.getRel().contains("self") ||
						(l.getTransition() != null 
						&& (t.getCommand().getMethod() == null || t.getCommand().getMethod().equals("GET"))
						&& t.getTarget().getEntityName().equals(t.getSource().getEntityName()))) {
					selfLink = l;
					break;
				}
			}
		}
		return selfLink;
	}

	/** Build the metadata fully-qualified property name by joining the simple property
	 *  name to the containing property name, if any.
	 *  @param prefix the containing property name or empty string if this is a top-level property
	 *  @param the simple name of the current property
	 *  @return the fully-qualified property name as used in EntityMetadata
	 */
	private String lengthenPrefix(String prefix, String extra) {
		if (prefix.isEmpty()) return extra;
		else return prefix + "." + extra;
	}

	/** Turn an OPropertyName into the property name used in the metadata
	 *  For complex properties, the property name is prefixed with the 
	 *  entity name, possibly so it can also be used as a unique type name.
	 *  Note this is totally separate from the fully-qualified property names
	 *  that are the keys to the entityMetadata.
	 *  i.e. if simple property prop2 is inside complex property prop1, in 
	 *  entity ent, the OProperty name of prop1 is ent_prop1, the OProperty name
	 *  of prop2 is prop2, that is accessed in the entity metadata as prop1.prop2
	 *  (not ent_prop1.prop2)
	 *  So, this takes an OProperty name and removes the entity prefix if appropriate.
	 */
	private String simpleOPropertyName(EntityMetadata entityMetadata, OProperty property) {
		String rawName = property.getName();

		if (!property.getType().isSimple()) {
			String expectedPrefix = entityMetadata.getEntityName() + "_";
			if (rawName.startsWith(expectedPrefix)) {
				String simpleName = rawName.substring(expectedPrefix.length());
				logger.debug(String.format("property lookup: %s -> %s", rawName, simpleName));
				return simpleName;
			} else {
				// This is probably not expected. Logging as info, it might be better to throw if we
				// are confident it shouldn't happen
				logger.info(String.format("property %s does not start with %s", rawName, expectedPrefix));
			}
		}
		return rawName;
	}

	/** transform OData4j object into String, Map or List
	 *  Only properties defined in the entityMetadata vocabulary are included in transform output
	 */
	private Object buildFromOObject(EntityMetadata entityMetadata, String prefix, Object any)
	{
		if (any instanceof OObject) {
			OObject object = (OObject)any;
		   
			if (object.getType().isSimple())
				return ((OSimpleObject<Object>)object).getValue().toString();
			else if (object instanceof OCollection) {
				ArrayList builtList = new ArrayList<Object>();
				OCollection<OObject> collection = (OCollection<OObject>)object;
				for ( OObject each : collection ) {
					builtList.add(buildFromOObject(entityMetadata, prefix, each));
				}
				return builtList;
			} else {
				OComplexObject complex = (OComplexObject)object;
				HashMap<String,Object> map = new HashMap<String,Object>();
				for (OProperty property : complex.getProperties()) {
					String simpleName = simpleOPropertyName(entityMetadata, property);
					String qualifiedName = lengthenPrefix(prefix, simpleName);

					if (entityMetadata.getPropertyVocabulary(qualifiedName) != null) {					    
					    String mandatory = entityMetadata.getTermValue(qualifiedName, TermMandatory.TERM_NAME);		                
		                if(property.getValue() != null || (null != mandatory && "true".equals(mandatory))) {
		                    map.put(simpleName, buildFromOObject(entityMetadata, qualifiedName, property.getValue()));
		                }
					} else {
						logger.debug(String.format("not adding property %s [%s], value %s",
												   property.getName(), qualifiedName, property.getValue()));
					}
				}
				return map;
			}
		} else
			return any;
	}

	/** populate a Map with the properties of an OEntity
	 */
	protected void buildFromOEntity(Map<String, Object> map, OEntity entity, String entityName) {
		EntityMetadata entityMetadata = metadata.getEntityMetadata(entityName);
		if (entityMetadata == null)
			throw new IllegalStateException("Entity metadata could not be found [" + entityName + "]");

		for (OProperty<?> property : entity.getProperties()) {
			// add properties if they are present on the resolved entity

			String simpleName = simpleOPropertyName(entityMetadata, property);
			if (entityMetadata.getPropertyVocabulary(simpleName) != null) {
			    String mandatory = entityMetadata.getTermValue(simpleName, TermMandatory.TERM_NAME);                
                if(property.getValue() != null || (null != mandatory && "true".equals(mandatory))) {
			        map.put(simpleName, buildFromOObject(entityMetadata, simpleName, property.getValue()));
			    }
			}
			else {
				logger.debug(String.format("not adding property %s, value %s",
										   property.getName(), property.getValue()));
			}
		}
	}

	/** populate a Map from an Entity
	 */
	protected void buildFromEntity(Map<String, Object> map, Entity entity, String entityName) {
		logger.debug("Serialising entity " + entityName);
		EntityMetadata entityMetadata = metadata.getEntityMetadata(entityName);
		if (entityMetadata == null)
			throw new IllegalStateException("Entity metadata could not be found [" + entityName + "]");
 
		buildFromEntityProperties(entityMetadata, "", map, entity.getProperties());
	}
		
	protected void buildFromEntityProperties(EntityMetadata entityMetadata, String prefix, Map<String, Object> map, EntityProperties entityProperties) {
		Map<String, EntityProperty> properties = entityProperties.getProperties();
				
		for (Map.Entry<String, EntityProperty> property : properties.entrySet()) {
			String propertyName = property.getKey(); 
			logger.debug("property key " + propertyName + " name " + property.getValue().getName());
      String qualifiedName = lengthenPrefix(prefix, propertyName);
      
      if (entityMetadata.getPropertyVocabulary(qualifiedName) != null )
        map.put( propertyName, entityPropertyValueToPOJO(entityMetadata, qualifiedName, property.getValue().getValue()));
		}
	}

	protected Object entityPropertyValueToPOJO(EntityMetadata entityMetadata, String prefix, Object propertyValue) {
		logger.debug("property value: " + propertyValue);
		if ( propertyValue == null ) return "";
		logger.debug("property value has type " + propertyValue.getClass());
		if ( propertyValue instanceof EntityProperties ) {
			Map<String,Object> newMap = new HashMap<String,Object>();
			buildFromEntityProperties(entityMetadata, prefix, newMap, (EntityProperties)propertyValue);
			return newMap;
		} else if ( propertyValue instanceof Collection ) {
			List newList = new ArrayList<EntityProperties>();
			for (Object element : (Collection<?>) propertyValue ) {
				newList.add(entityPropertyValueToPOJO(entityMetadata, prefix, element));
			}
			return newList;
		} else if ( propertyValue instanceof EntityProperty ) {
			return ((EntityProperty)propertyValue).getValue();
		} else {
			return propertyValue;
		}
	}

	/** populate a Map from a java bean
	 *  TODO implement nested structures and collections
	 */
	protected void buildFromBean(Map<String, Object> map, Object bean, String entityName) {
		EntityMetadata entityMetadata = metadata.getEntityMetadata(entityName);
		if (entityMetadata == null)
			throw new IllegalStateException("Entity metadata could not be found [" + entityName + "]");

		try {
			BeanInfo beanInfo = Introspector.getBeanInfo(bean.getClass());
			for (PropertyDescriptor propertyDesc : beanInfo.getPropertyDescriptors()) {
			    String propertyName = propertyDesc.getName();
				if (entityMetadata.getPropertyVocabulary(propertyName) != null) {
				    Object value = propertyDesc.getReadMethod().invoke(bean);
					map.put(propertyName, value);				
				}
			}
		} catch (IllegalArgumentException e) {
			logger.error("Error accessing bean property", e);
		} catch (IntrospectionException e) {
			logger.error("Error accessing bean property", e);
		} catch (IllegalAccessException e) {
			logger.error("Error accessing bean property", e);
		} catch (InvocationTargetException e) {
			logger.error("Error accessing bean property", e);
		}
	}

  // Populate a Representation with the links and properties
  void collectLinksAndProperties(Representation resource, Iterable<Link> links,
                                 Map<String, Object> propertyMap) {
        if (links != null) {
          for (Link l : links) {
           if(l != null) {
            logger.debug("Link: id=[" + l.getId() + "] rel=[" + l.getRel() +
                   "] method=[" + l.getMethod() + "] href=[" + l.getHref() + "]");
            String[] rels = new String[0];
            if (l.getRel() != null) {
              rels = l.getRel().split(" ");
            }
            
            if (rels != null) {
              for (int i = 0 ; i < rels.length; i++) {
                resource.withLink(rels[i], l.getHref(), l.getId(), l.getTitle(), null, null); 
              }
            }
           }
          }
        }

        // add properties to HAL sub resource
        for (String key : propertyMap.keySet()) {
          resource.withProperty(key, propertyMap.get(key));
        }
  }
  
	private Representation buildRepresentation(Representation halResource,
											   RESTResource resource,
											   Class<?> type,
											   Type genericType) {
		if (genericType == null)
			genericType = resource.getGenericEntity().getType();
		if (type == null)
			type = resource.getGenericEntity().getRawType();
		if (ResourceTypeHelper.isType(type, genericType, EntityResource.class, OEntity.class)) {
			@SuppressWarnings("unchecked")
			EntityResource<OEntity> oentityResource = (EntityResource<OEntity>) resource;
			Map<String, Object> propertyMap = new HashMap<String, Object>();
			buildFromOEntity(propertyMap, oentityResource.getEntity(), oentityResource.getEntityName());
			// add properties to HAL resource
			for (String key : propertyMap.keySet()) {
				logger.debug(String.format("add property to representation: %s %s = %s",
										   propertyMap.get(key).getClass(), key,
										   propertyMap.get(key)));
				halResource.withProperty(key, propertyMap.get(key));
			}
		} else if (ResourceTypeHelper.isType(type, genericType, EntityResource.class, Entity.class)) {
			logger.debug("transforming EntityResource<Entity>");
				EntityResource<Entity> entityResource = (EntityResource<Entity>) resource;
				Map<String, Object> propertyMap = new HashMap<String, Object>();
				buildFromEntity(propertyMap, entityResource.getEntity(), entityResource.getEntityName());
				// add properties to HAL resource
				for (String key : propertyMap.keySet()) {
					halResource.withProperty(key, propertyMap.get(key));
				}
		} else if (ResourceTypeHelper.isType(type, genericType, EntityResource.class)) {
			EntityResource<?> entityResource = (EntityResource<?>) resource;
			Object entity = entityResource.getEntity();
			if (entity != null) {
				/*
				 * // regular java bean
				 * halResource.withBean(entity);
				 */
				// java bean, now limited to just the properties specified in the metadata entity model
				Map<String, Object> propertyMap = new HashMap<String, Object>();
				buildFromBean(propertyMap, entity, entityResource.getEntityName());
				for (String key : propertyMap.keySet()) {
					halResource.withProperty(key, propertyMap.get(key));
				}
			}
		} else if(ResourceTypeHelper.isType(type, genericType, CollectionResource.class, OEntity.class)) {
			@SuppressWarnings("unchecked")
			CollectionResource<OEntity> cr = (CollectionResource<OEntity>) resource;
			List<EntityResource<OEntity>> entities = (List<EntityResource<OEntity>>) cr.getEntities();
			Integer inlineCount = cr.getInlineCount();
			if (inlineCount != null) {
				halResource.withProperty("count", inlineCount.toString());
			}
			for (EntityResource<OEntity> er : entities) {
				OEntity entity = er.getEntity();
				// the subresource is an item of the collection (http://tools.ietf.org/html/rfc6573)
				String rel = "item";
				// the properties
				Map<String, Object> propertyMap = new HashMap<String, Object>();
				buildFromOEntity(propertyMap, entity, cr.getEntityName());

				// create hal resource and add link for self - if there is one
				Representation subResource = representationFactory.newRepresentation();
				collectLinksAndProperties(subResource, er.getLinks(), propertyMap);
				halResource.withRepresentation(rel, subResource);
			}
		} else if(ResourceTypeHelper.isType(type, genericType, CollectionResource.class, Entity.class)) {
      logger.debug("Transforming CollectionResource<Entity>");
			@SuppressWarnings("unchecked")
			CollectionResource<Entity> cr = (CollectionResource<Entity>) resource;
			List<EntityResource<Entity>> entities = (List<EntityResource<Entity>>) cr.getEntities();
			for (EntityResource<Entity> er : entities) {
				// Make property Map
				Map<String, Object> propertyMap = new HashMap<String, Object>();
				buildFromEntity(propertyMap, er.getEntity(), cr.getEntityName());

				// Make Representation
				Representation subResource = representationFactory.newRepresentation();
				collectLinksAndProperties(subResource, er.getLinks(), propertyMap);
				halResource.withRepresentation("item", subResource);
			}
		} else if (ResourceTypeHelper.isType(type, genericType, CollectionResource.class)) {
			@SuppressWarnings("unchecked")
			CollectionResource<Object> cr = (CollectionResource<Object>) resource;
			List<EntityResource<Object>> entities = (List<EntityResource<Object>>) cr.getEntities();
			for (EntityResource<Object> er : entities) {
				Object entity = er.getEntity();
				// the subresource is part of a collection (maybe this link rel should be an 'item')
				String rel = "collection." + cr.getEntityName();
				// the properties
				Map<String, Object> propertyMap = new HashMap<String, Object>();
				buildFromBean(propertyMap, entity, cr.getEntityName());
				// create hal resource and add link for self
				Link itemSelfLink = findSelfLink(er.getLinks());
				if (itemSelfLink != null) {
					Representation subResource = representationFactory.newRepresentation(itemSelfLink.getHref());
					for (Link el : er.getLinks()) {
						String itemHref = el.getHref();
						/*
						don't add links twice, this break the client assertion of one rel per link (which seems wrong)
						List<com.theoryinpractise.halbuilder.api.Link> selfLinks = subResource.getLinksByRel("self");
						assert(selfLinks != null && selfLinks.size() == 1);
						*/
						if (!itemSelfLink.equals(el)) {
							subResource.withLink(el.getRel(), itemHref, el.getId(), el.getTitle(), null, null);
						}
					}
					// add properties to HAL sub resource
					for (String key : propertyMap.keySet()) {
						subResource.withProperty(key, propertyMap.get(key));
					}
					halResource.withRepresentation(rel, subResource);
				}
				
			}
			
		} else {
			logger.error("Accepted object for writing in isWriteable, but type not supported in writeTo method");
			throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
		}
		return halResource;
	}
	
	@Override
	public boolean isReadable(Class<?> type, Type genericType,
			Annotation[] annotations, MediaType mediaType) {
		// this class can only deserialise EntityResource
		return ResourceTypeHelper.isType(type, genericType, EntityResource.class);
	}

	/**
	 * Reads a Hypertext Application Language (HAL) representation of
	 * {@link EntityResource} from the input stream.
	 * 
	 * @precondition {@link InputStream} contains a valid HAL <resource/> document
	 * @postcondition {@link EntityResource} will be constructed and returned.
	 * @invariant valid InputStream
	 */
	@Override
	public RESTResource readFrom(Class<RESTResource> type, Type genericType,
			Annotation[] annotations, MediaType mediaType,
			MultivaluedMap<String, String> httpHeaders, InputStream entityStream)
			throws IOException, WebApplicationException {
			/* To detect if the stream is empty (a valid case since an input entity is
			 * sometimes optional), wrap in a PushbackInputStream before passing on
			 */
			PushbackInputStream wrappedStream = new PushbackInputStream(entityStream);
			int firstByte = wrappedStream.read();
			uriInfo = new UriInfoImpl(uriInfo);
			if ( firstByte == -1 ) {
					// No data provided
					return null;
			} else {
					// There is something in the body, so we will parse it. It is required
					// to be a valid JSON object. First replace the byte we borrowed.
					wrappedStream.unread(firstByte);

					//Parse hal+json into an Entity object
					Entity entity;
					try {
						entity = buildEntityFromHal(wrappedStream, mediaType);
					} catch (MethodNotAllowedException e) {
					    if (logger.isDebugEnabled()) {
					        logger.debug("Error building the entity.", e);
					    }
						StringBuilder allowHeader = new StringBuilder();

						Set<String> allowedMethods = new HashSet<String>(e.getAllowedMethods());
						allowedMethods.add("HEAD");
						allowedMethods.add("OPTIONS");
						
						for(String method: allowedMethods) {
							allowHeader.append(method);
							allowHeader.append(", ");
						}
						
						Response response = Response.status(405).header("Allow", allowHeader.toString().substring(0, allowHeader.length() - 2)).build();
						
						throw new WebApplicationException(response);
					}
					return new EntityResource<Entity>(entity);
			}
	}

	private Entity buildEntityFromHal(InputStream entityStream, MediaType mediaType) throws MethodNotAllowedException {
		try {
			// create the hal resource
			String baseUri = uriInfo.getBaseUri().toString();
			ReadableRepresentation halResource = representationFactory.readRepresentation(mediaType.toString(), new InputStreamReader(entityStream));			
			
			String resourcePath = uriInfo.getPath();
						
			logger.info("Reading HAL content for [" + resourcePath + "]");
			if (resourcePath == null)
				throw new IllegalStateException("No resource found");
			// trim the baseuri
			if (resourcePath.length() > baseUri.length() && resourcePath.startsWith(baseUri))
				resourcePath = resourcePath.substring(baseUri.length() - 1);
			/*
			 * add a leading '/' if it needs it (when defining resources we must use a 
			 * full path, but requests can be relative, i.e. without a '/'
			 */
			if (!resourcePath.startsWith("/")) {
				resourcePath = "/" + resourcePath;
			}
			
			// get the entity name
			String entityName = getEntityName(resourcePath);
			
			if(entityName == null) {
				throw new IllegalStateException("Entity name could not be found [" + resourcePath + "]");
			}
			
			EntityMetadata entityMetadata = metadata.getEntityMetadata(entityName);
			if (entityMetadata == null)
				throw new IllegalStateException("Entity metadata could not be found [" + entityName + "]");
			// add properties if they are present on the resolved entity
			EntityProperties entityFields = new EntityProperties();
			Map<String, Object> halProperties = halResource.getProperties();
			iterateProperties(entityMetadata, entityFields, halProperties, "");
			return new Entity(entityName, entityFields);
		} catch (RepresentationException e) {
			logger.warn("Malformed request from client", e);
			throw new WebApplicationException(Status.BAD_REQUEST);
		} catch (IllegalStateException e) {
			logger.warn("Malformed request from client", e);
			throw new WebApplicationException(Status.BAD_REQUEST);
		}
	}

	/*
	 * Iterate through property keys and extract values if the vocabulary is correct.
	 */
	private void iterateProperties(EntityMetadata entityMetadata, EntityProperties entityFields,
			Map<String, Object> halProperties, String prefix) {
		for (String propName : halProperties.keySet()) {
			if (entityMetadata.getPropertyVocabulary(concatenatePrefixes(prefix,propName)) != null) {
				Object propertyValue = halProperties.get(propName);
				if (propertyValue != null) {
					Object halValue = getHalPropertyValue(entityMetadata, propName, halProperties.get(propName),prefix);
					entityFields.setProperty(new EntityProperty(propName, halValue));
				}
			}
		}
	}
	
	protected ResourceState getCurrentState(String baseUri, String resourcePath) throws MethodNotAllowedException {
		ResourceState state = null;
		if (resourcePath != null) {
		    String tmpResourcePath = resourcePath;
		    
		    if(resourcePath.charAt(0) != '/') {
		        tmpResourcePath = '/' + tmpResourcePath;
			}
		    
		    state = resourceStateProvider.getResourceState(requestContext.getMethod(), tmpResourcePath);
		}
		return state;
	}	
	
	private String getEntityName(String resourcePath) throws MethodNotAllowedException {
		String entityName = null;
		
		if (resourcePath != null) {
			String absoluteUri = uriInfo.getBaseUri() + uriInfo.getPath();
			
			String tmpResourcePath = absoluteUri.substring(uriInfo.getBaseUri().toString().length());
						
			ResourceState state = getCurrentState(uriInfo.getBaseUri().toString(), tmpResourcePath);

			if (state != null) {
				entityName = state.getEntityName();
			} else {
				logger.warn("No state found, dropping back to path matching");
				Map<String, Set<String>> pathToResourceStates = resourceStateProvider.getResourceStatesByPath();
				for (String path : pathToResourceStates.keySet()) {
					for (String stateName : pathToResourceStates.get(path)) {
						ResourceState s = resourceStateProvider.getResourceState(stateName);
						String pathIdParameter = InteractionContext.DEFAULT_ID_PATH_ELEMENT;
						if (s.getPathIdParameter() != null) {
							pathIdParameter = s.getPathIdParameter();
						}
						Matcher matcher = Pattern.compile("(.*)\\{" + pathIdParameter + "\\}(.*)").matcher(path);
						if (matcher.find()) {
							int groupCount = matcher.groupCount();
							if ((groupCount == 1 && resourcePath.startsWith(matcher.group(1))) ||
								(groupCount == 2 && resourcePath.startsWith(matcher.group(1)) && resourcePath.endsWith(matcher.group(2)))) {
								entityName = s.getEntityName();
							}
						}
						if (entityName == null && path.startsWith(resourcePath)) {
							entityName = s.getEntityName();
						}
					}
				}
			}
		}
		
		return entityName;
	}
	
	/* Ugly testing support :-( */
	protected void setUriInfo(UriInfo uriInfo) {
		this.uriInfo = uriInfo;
	}
	protected void setRequestContext(Request request) {
		this.requestContext = request;
	}
	
	/*
	 * If a property is given with a null value, return it in a usable form for JSON
	 */
	private Object nullHalPropertyValue( EntityMetadata entityMetadata, String propertyName ) {
		if ( entityMetadata.isPropertyText( propertyName ) )
			return "";
		else if ( entityMetadata.isPropertyNumber( propertyName ) )
			return 0L;
		return "";
	}

	/*
	 * Parse property values from input data.
	 */
	private Object getHalPropertyValue( EntityMetadata entityMetadata, String propertyName, Object halPropertyValue, String currentPrefix )
	{
		if ( halPropertyValue == null )
			return nullHalPropertyValue( entityMetadata, propertyName );
		if(halPropertyValue instanceof Collection){
			return getValuesFromJsonArray(entityMetadata, propertyName, halPropertyValue, currentPrefix);
		} else if(halPropertyValue instanceof Map){
			return getValuesFromJsonObject(entityMetadata, propertyName, (Map<String, Object>)halPropertyValue, currentPrefix);
		}
		String stringValue = halPropertyValue.toString();
		Object typedValue;
		
		if ( entityMetadata.isPropertyText( propertyName ) )
		{
			typedValue = stringValue;
		}
		else if ( entityMetadata.isPropertyNumber( propertyName ) )
		{
			typedValue = Long.parseLong( stringValue );
		}
		else
		{
			typedValue = stringValue;
		}
		
		return typedValue;
	}

	/*
	 * Retrieve values from a JSON array. 
	 */
	private List<EntityProperties> getValuesFromJsonArray(EntityMetadata entityMetadata, String propertyName,
			Object halPropertyValue, String currentPrefix) {
		Collection halPropertyValueCollection = (Collection)halPropertyValue;
		ArrayList<EntityProperties> embeddedArray = new ArrayList<EntityProperties>();

		for(Object o : halPropertyValueCollection){
			if(o instanceof Map){
				EntityProperties properties = new EntityProperties();
				Map<String, Object> halPropertiesMap = (Map<String,Object>) o;
				this.iterateProperties(entityMetadata, properties, halPropertiesMap, this.concatenatePrefixes(currentPrefix, propertyName));
				embeddedArray.add(properties);
			}
		}
		return embeddedArray;
	}
	
	private EntityProperties getValuesFromJsonObject(EntityMetadata entityMetadata, String propertyName, 
		Map<String, Object> halPropertyValue, String currentPrefix){
		EntityProperties properties = new EntityProperties();
		this.iterateProperties(entityMetadata, properties, halPropertyValue, this.concatenatePrefixes(currentPrefix, propertyName));
		return properties;
	}
	
	/*
	 * Concatenate an object prefix with a nested object name using dot notation
	 * if a prefix already exists, else return the object name.
	 */
	private String concatenatePrefixes(String current, String newPrefixAddition){ 
		if(StringUtils.isNotBlank(current)){
			StringBuilder sb = new StringBuilder();
			sb.append(current).append(".").append(newPrefixAddition);
			return sb.toString(); 
		}
		else {
			return newPrefixAddition;
		}
	}
}
