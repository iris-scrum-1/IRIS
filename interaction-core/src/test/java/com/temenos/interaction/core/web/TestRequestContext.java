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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.temenos.interaction.core.web.RequestContext.Builder;

public class TestRequestContext {

    @Test
    public void testInexistentHeader() {
         Map<String, List<String>> headers = new HashMap<>();
         List<String> singleElementList = new ArrayList<>();
         singleElementList.add("value0");
         headers.put("header0", singleElementList);
         
         RequestContext ctx = new RequestContext("\basepath", "\requesturi", null, headers);
         
         assertTrue(ctx.getHeaders("header1").isEmpty());
         assertNull(ctx.getFirstHeader("header1"));
    }

	@Test
	public void testSingleValueHeader() {
	     Map<String, List<String>> headers = new HashMap<>();
	     List<String> singleElementList = new ArrayList<>();
	     singleElementList.add("value0");
	     headers.put("header0", singleElementList);
	     
	     RequestContext ctx = new RequestContext("\basepath", "\requesturi", null, headers);
	     
	     assertEquals(1, ctx.getHeaders("header0").size());
	     assertEquals("value0", ctx.getHeaders("header0").get(0));
	     assertEquals("value0", ctx.getFirstHeader("header0"));
	}
	
    @Test
    public void testMultiValueHeader() {
        Map<String, List<String>> headers = new HashMap<>();
        List<String> list = new ArrayList<>();
        list.add("value0");
        list.add("value1");
        list.add("value2");
        headers.put("header0", list);

        RequestContext ctx = new RequestContext("\basepath", "\requesturi", null, headers);

        assertEquals(3, ctx.getHeaders("header0").size());
        assertEquals("value0", ctx.getHeaders("header0").get(0));
        assertEquals("value1", ctx.getHeaders("header0").get(1));
        assertEquals("value2", ctx.getHeaders("header0").get(2));
        assertEquals("value0", ctx.getFirstHeader("header0"));
    }

    @Test
    public void testGetFirstHeader() {
        Map<String, List<String>> headers = new HashMap<>();
        List<String> list = new ArrayList<>();
        list.add("value0");
        list.add("value1");
        list.add("value2");
        headers.put("header0", list);

        RequestContext ctx = new RequestContext("\basepath", "\requesturi", null, headers);

        assertNull(ctx.getFirstHeader(null));
        assertEquals("value0", ctx.getFirstHeader("header0"));
        assertNull(ctx.getFirstHeader("HEADER0"));
        assertNull(ctx.getFirstHeader("Header0"));
    }

    @Test
    public void testGetFirstHeaderCaseInsensitive() {
        Map<String, List<String>> headers = new HashMap<>();
        List<String> list = new ArrayList<>();
        list.add("value0");
        list.add("value1");
        list.add("value2");
        headers.put("header0", list);

        RequestContext ctx = new RequestContext("\basepath", "\requesturi", null, headers);

        assertNull(ctx.getFirstHeaderCaseInsensitive(null));
        assertEquals("value0", ctx.getFirstHeaderCaseInsensitive("header0"));
        assertEquals("value0", ctx.getFirstHeaderCaseInsensitive("HEADER0"));
        assertEquals("value0", ctx.getFirstHeaderCaseInsensitive("Header0"));
    }
    
    @Test
    public void testRequestTime() {
         Map<String, List<String>> headers = new HashMap<>();
         RequestContext ctx = new RequestContext("\basepath", "\requesturi", null, headers);
         assertNotNull(ctx.getRequestTime());
         assertTrue(ctx.getRequestTime() >= System.currentTimeMillis());
    }
    
    @Test
    public void testBuilder() {
        
        Map<String, List<String>> headers = new HashMap<>();
        RequestContext ctx = new RequestContext("\basepath", "\requesturi", null, headers);
        
        Builder builder = new Builder();
        builder.setBasePath("\basepath");
        builder.setRequestUri("\requesturi");
        builder.setVerbosityHeader(null);
        builder.setHeaders(headers);
        builder.setUserPrincipal(null);
        builder.setRequestTime(ctx.getRequestTime());
        
        RequestContext reqCtxBuilder = builder.build();
        
        assertEquals(ctx.getBasePath(), reqCtxBuilder.getBasePath());
        assertEquals(ctx.getRequestUri(), reqCtxBuilder.getRequestUri());
        assertEquals(ctx.getVerbosityHeader(), reqCtxBuilder.getVerbosityHeader());
        assertEquals(ctx.getHeaders(""), reqCtxBuilder.getHeaders(""));
        assertEquals(ctx.getUserPrincipal(), reqCtxBuilder.getUserPrincipal());
        assertEquals(ctx.getRequestTime(), reqCtxBuilder.getRequestTime());
    }
}
