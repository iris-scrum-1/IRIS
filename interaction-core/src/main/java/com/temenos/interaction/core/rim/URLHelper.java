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

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.UriInfo;

/**
 * This class provides helper functions related to URL processing
 *
 * @author mlambert
 *
 */
public class URLHelper {
    /**
     * Extracts any path parameters from the given uri segments and adds them to the UriInfo's path parameters
     */
    public void extractPathParameters(UriInfo uriInfo, String[] uriSegments, String[] pathSegments) { 
        if(uriSegments!=null && uriSegments.length<=0) {
            return;
        }else if(uriSegments==null) {
            return;
        }
        for (int i = 0; i < pathSegments.length; i++) {
            String pathSegment = pathSegments[i];
            String uriSegment = uriSegments[i];
            
            if(pathSegment.isEmpty())
                return; // There are no path segments so bail out
                        
            final int pathSegmentFirstIndex = 0;
            int pathSegmentLastIndex = pathSegment.length() - 1;
                                
            if(pathSegment.charAt(pathSegmentLastIndex) == ')') {
                // Path ends in closing bracket
                
                int pathSegmentOpenBracket = pathSegment.indexOf('(');
                
                if(pathSegmentOpenBracket + 1 == pathSegmentLastIndex) {
                    continue; // Current segment has no parameters i.e. xyz() 
                }
                
                if(pathSegmentOpenBracket < pathSegmentLastIndex - 1) {
                    // We are dealing with a path that contains (...) where ... may contain 0 or more characters
                    
                    // Remove brackets from path segment to simplify subsequent path matching
                    pathSegment = pathSegment.substring(pathSegmentOpenBracket + 1, pathSegmentLastIndex);
                    pathSegmentLastIndex = pathSegment.length() - 1;
                    
                    if(!pathSegment.isEmpty()) {
                        
                        
                        if (pathSegment.charAt(pathSegmentFirstIndex) == '\'' && pathSegment.charAt(pathSegmentLastIndex) == '\'') {
                            pathSegment = pathSegment.substring(1, pathSegmentLastIndex);
                            pathSegmentLastIndex = pathSegment.length() - 1;
                        }                        
                    }
                    
                    uriSegment = uriSegment.substring(pathSegmentOpenBracket + 1, uriSegment.length() - 1);
                    
                    if(!uriSegment.isEmpty()) {
                        if (uriSegment.charAt(0) == '\'' && uriSegment.charAt(uriSegment.length() - 1) == '\'') {
                            uriSegment = uriSegment.substring(1, uriSegment.length() - 1);
                        }
                    }
                }                                
            }
            
            if(!pathSegment.isEmpty()) {                
                if (pathSegment.charAt(pathSegmentFirstIndex) == '{' && pathSegment.charAt(pathSegmentLastIndex) == '}') {
                    // Strip '{' and '}' from pathSegment
                    pathSegment = pathSegment.substring(1, pathSegmentLastIndex);
                    List<String> pathSegmentValues = new ArrayList<String>();
                    pathSegmentValues.add(uriSegment);

                    uriInfo.getPathParameters().put(pathSegment, pathSegmentValues);
                }                
            }
        }
    }   
}
