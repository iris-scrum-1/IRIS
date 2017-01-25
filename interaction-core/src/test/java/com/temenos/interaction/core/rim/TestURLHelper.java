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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import junit.framework.TestCase;

import org.apache.wink.common.internal.MultivaluedMapImpl;
import org.junit.Test;

public class TestURLHelper extends TestCase {
    @Test
    public void testExtractSinglePathParameter() {
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String, String>();
        when(uriInfo.getPathParameters()).thenReturn(pathParameters);
        
        /* /ABC0001/enqCustomerInfos()
         * /{companyid}/enqCustomerInfos()
         */        
        String[] uriSegments = new String[]{"ABC0001", "enqCustomerInfos()"};
        String[] pathSegments = new String[]{"{companyid}", "enqCustomerInfos()"};        
        
        new URLHelper().extractPathParameters(uriInfo, uriSegments, pathSegments);
        
        assertEquals("Wrong companyid", "ABC0001", pathParameters.getFirst("companyid"));
        assertNull(pathParameters.getFirst("id"));
    }
    
    
    @Test
    public void testExtractPathParametersWithEmpty() {
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String, String>();
        when(uriInfo.getPathParameters()).thenReturn(pathParameters);
        
        String[] uriSegments = new String[]{};
        String[] pathSegments = new String[]{"{companyid}", "enqCustomerInfos()"};        
        
        new URLHelper().extractPathParameters(uriInfo, uriSegments, pathSegments);
        
        assertEquals("Size 0 check", 0, pathParameters.size());
    }
    
    @Test
    public void testExtractMultiplePathParameters() {        
        UriInfo uriInfo = mock(UriInfo.class);
        MultivaluedMap<String, String> pathParameters = new MultivaluedMapImpl<String, String>();
        when(uriInfo.getPathParameters()).thenReturn(pathParameters);
        
        /* /ABC0001/enqCustomerInfos('100105')
         * /{companyid}/enqCustomerInfos('{id}')
         */
        String[] uriSegments = new String[]{"ABC0001", "enqCustomerInfos('100105')"};
        String[] pathSegments = new String[]{"{companyid}", "enqCustomerInfos('{id}')"};
        
        new URLHelper().extractPathParameters(uriInfo, uriSegments, pathSegments);
                
        assertEquals("Wrong companyid", "ABC0001", pathParameters.getFirst("companyid"));
        assertEquals("Wrong id", "100105", pathParameters.getFirst("id"));

        
        /* /ABC0001/enqCustomerInfos()/123
         * /{companyid}/enqCustomerInfos()/recordid
         */        
        uriSegments = new String[]{"ABC0001", "enqCustomerInfos()", "123"};
        pathSegments = new String[]{"{companyid}", "enqCustomerInfos()", "{recordid}"};
        
        uriInfo = mock(UriInfo.class);
        pathParameters = new MultivaluedMapImpl<String, String>();
        when(uriInfo.getPathParameters()).thenReturn(pathParameters);        

        new URLHelper().extractPathParameters(uriInfo, uriSegments, pathSegments);    
        assertEquals("Wrong companyid", "ABC0001", pathParameters.getFirst("companyid"));
        assertNull(pathParameters.getFirst("id"));        
        assertEquals("Wrong recordid", "123", pathParameters.getFirst("recordid"));
    }

}
