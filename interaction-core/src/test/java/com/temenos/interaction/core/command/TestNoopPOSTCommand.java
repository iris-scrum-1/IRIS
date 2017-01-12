package com.temenos.interaction.core.command;

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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import org.junit.Test;

import com.temenos.interaction.core.MultivaluedMapImpl;
import com.temenos.interaction.core.entity.Metadata;
import com.temenos.interaction.core.hypermedia.ResourceState;
import com.temenos.interaction.core.resource.EntityResource;

/**
 * Tests for NoopPOSTCommand implementation of InteractionCommand interface
 *
 * @author clopes
 *
 */
public class TestNoopPOSTCommand {

    @SuppressWarnings("unchecked")
    @Test
    public void testNoopPOSTCommandResourceNull() throws Exception {
        
        MultivaluedMap<String,String> map = new MultivaluedMapImpl<>();
        map.add("param1","param1value");
        map.add("param2","param2value");
        map.add("param3","param3value1");
        map.add("param3","param3value2");
        
        InteractionContext ctx = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), mock(MultivaluedMap.class), map, mock(ResourceState.class), mock(Metadata.class));
        ctx.setResource(null);

        InteractionCommand mockCommand = new NoopPOSTCommand();
        mockCommand.execute(ctx);
        
        assertNotNull(ctx.getOutQueryParameters());
        assertTrue(ctx.getOutQueryParameters().size() == map.size());
    }
    
    @SuppressWarnings("unchecked")
    @Test
    public void testNoopPOSTCommandResourceNotNull() throws Exception {
        
        MultivaluedMap<String,String> map = new MultivaluedMapImpl<>();
        map.add("param1","param1value");
        map.add("param2","param2value");
        map.add("param3","param3value1");
        map.add("param3","param3value2");
        
        InteractionContext ctx = new InteractionContext(mock(UriInfo.class), mock(HttpHeaders.class), mock(MultivaluedMap.class), map, mock(ResourceState.class), mock(Metadata.class));
        ctx.setResource(new EntityResource<Object>());

        InteractionCommand mockCommand = new NoopPOSTCommand();
        mockCommand.execute(ctx);
        
        assertNotNull(ctx.getOutQueryParameters());
        assertTrue(ctx.getOutQueryParameters().size() == map.size());
    }
}
