/*
 * Our Xtext Java class generator
 */
package com.temenos.interaction.rimdsl.generator

import com.temenos.interaction.rimdsl.rim.State
import com.temenos.interaction.rimdsl.rim.TransitionRef
import java.util.ArrayList
import java.util.Collection
import java.util.HashMap
import java.util.HashSet
import java.util.Map
import java.util.Set
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.xtext.generator.IFileSystemAccess
import org.eclipse.xtext.generator.IGenerator
import com.temenos.interaction.rimdsl.rim.ResourceInteractionModel
import com.temenos.interaction.rimdsl.rim.BasePath
import java.util.regex.Pattern
import com.temenos.interaction.rimdsl.rim.DomainDeclaration
import com.temenos.interaction.rimdsl.rim.Ref
import org.apache.commons.lang.StringUtils
import org.eclipse.emf.common.util.EList
import com.temenos.interaction.rimdsl.rim.MdfAnnotation
import com.temenos.interaction.rimdsl.rim.CommandProperty
import java.util.Hashtable
import com.temenos.interaction.rimdsl.rim.MethodRef

class RIMDslGeneratorSwagger implements IGenerator {
    
    var nextComma = false;
    var positionPath = 0;
    var metadata = new HashMap<String, Object>();
	
	override void doGenerate(Resource resource, IFileSystemAccess fsa) {
	    
	    if (resource == null) {
            throw new RuntimeException("Generator called with null resource");	        
	    }
	    var domains = resource.allContents.toIterable.filter(typeof(DomainDeclaration))
	    
	    for (DomainDeclaration domain : domains) {
	        var refs = domain.rims
	        for (Ref ref : refs) {
	            if(ref instanceof ResourceInteractionModel) {	                
	                var rim = ref as ResourceInteractionModel
                    var states = rim.states
                    var initialState = states.findFirst[ isInitial ]
                    metadata = resource.resourceSet.loadOptions.get("Metadata") as HashMap<String, Object>
                    if (initialState != null) {
                        var interactionsByPath = new HashMap<String, Set<String>>()
                        collectInteractionsByPath(interactionsByPath, initialState)
                        var stateByMethodPath = new HashMap<String, State>()
                        collectStateByMethodPath(stateByMethodPath, initialState)
                        var resources = new ArrayList<String>()
                        for (String path : interactionsByPath.keySet) {
                            if (path.startsWith("/")) {
                                resources.add(path)
                            }
                        }
                        fsa.generateFile("api-docs-"+domain.name+"-"+rim.name+".json", toApiDocHeader(domain, rim) + toResourceListing(rim, initialState, resources, interactionsByPath, stateByMethodPath).toString + toApiDocFooter())
                    } 
	            }
	        }
	    }
	}
	
	def toApiDocHeader(DomainDeclaration domain, ResourceInteractionModel rim) '''
    {
        "swagger": "2.0",
        "info": {
            "title": "«getAnnotation(rim.annotations,"title")»",
            "description": "«getAnnotation(rim.annotations,"description")»",
            "version": "1.0.0"
        },
        "produces": ["application/json","application/xml"],
	'''

    def toResourceListing(ResourceInteractionModel rim, State initialState, Iterable<String> paths, Map<String, Set<String>> interactionsByPath, Map<String, State> stateByMethodPath) '''
        "paths": {
        «setPathPosition(0)»
        «FOR path : paths»
            «IF path != null && showResource(path,rim,interactionsByPath,stateByMethodPath)»
                «var interactions = interactionsByPath.get(path)»
                «IF interactions != null»
                    «IF positionPath >= 1»,«ENDIF»
                    «IF rim.basepath != null && !rim.basepath.name.isNullOrEmpty && rim.basepath.name.contains("{")»
                        "«rim.basepath.name + path»": {
                            «toOperations(path, rim, interactionsByPath, stateByMethodPath)»
                        }
                    «ELSE»
                        "«path»": {
                            «toOperations(path, rim, interactionsByPath, stateByMethodPath)»
                        }
                    «ENDIF»
                    «IF positionPath < paths.size - 1»«setPathPosition(positionPath+1)»«ENDIF»
                «ENDIF»
            «ENDIF»
        «ENDFOR»
        },
        "definitions": {
            "ErrorsMvGroup": {
                "type": "object",
                "properties": {
                    "Text": {
                        "type": "string"
                    },
                    "Type": {
                        "type": "string"
                    },
                    "Info": {
                        "type": "string"
                    },
                    "Code": {
                        "type": "string"
                    }
                }
            }
            «FOR path : paths»
                «IF path != null»
                    «FOR method : interactionsByPath.get(path)»
                        «var state = stateByMethodPath.get(path + method)»
                        «var entity = state.entity»
                        «IF null != metadata && metadata.containsKey(entity.name)»
                            «var entityProps = metadata.get(entity.name) as HashMap<String, Object>»
                            «IF entityProps != null»
                                ,
                                «paramsPrint(entity.name, entityProps)»
                                «removeEntry(entity.name)»
                            «ENDIF»
                        «ENDIF»
                    «ENDFOR»
                «ENDIF»
            «ENDFOR»
        }
    '''

	def toOperations(String path, ResourceInteractionModel rim, Map<String, Set<String>> interactionsByPath, Map<String, State> stateByMethodPath) '''
    «FOR method : interactionsByPath.get(path) SEPARATOR ','»
        "«method.toLowerCase»": {
            «var state = stateByMethodPath.get(path + method)»
            «var methods = state.impl.methods»
            "description": "«getAnnotation(state.annotations,"description")»",
            «IF method == "POST" || method == "PUT"»
            "consumes": ["application/json","application/xml"],
            "produces": ["application/json","application/xml"],
            «ELSE»
            "produces": ["application/json","application/xml"],
            «ENDIF»
            "parameters": [
                «getParams(methods, path, rim.basepath, method, state)»
            ],
            «IF findAnnotation(state.annotations,"tags")» "tags": [«getTags(state.annotations)»],
            «ELSEIF findAnnotation(rim.annotations,"tags")» "tags": [«getTags(rim.annotations)»],
            «ELSE» "tags": ["«rim.name»"],
            «ENDIF»
            "responses": {
                «IF method == "POST"»
                "201": {
                    "description": "Created",
                    "schema": {
                        "type": "array",
                        "items": {
                            "$ref": "#/definitions/«state.entity.name»"
                        }
                    }
                },
                «ELSE»
                "200": {
                    "description": "",
                    "schema": {
                        "type": "array",
                        "items": {
                            "$ref": "#/definitions/«state.entity.name»"
                        }
                    }
                },
                «ENDIF»
                "400": {
                    "description": "Bad request",
                    "schema": {
                        "type": "array",
                        "items": {
                            "$ref": "#/definitions/ErrorsMvGroup"
                        }
                    }
                },
                "401": {
                    "description": "Authentication/Authorization error",
                    "schema": {
                        "type": "array",
                        "items": {
                            "$ref": "#/definitions/ErrorsMvGroup"
                        }
                    }
                },
                "404": {
                    "description": "Resource not found"
                },
                "default": {
                    "description": "Unexpected output",
                    "schema": {
                        "$ref": "#/definitions/ErrorsMvGroup"
                    }
                }
            }
        }
    «ENDFOR»
    '''
      
    def toApiDocFooter() '''
    }
    '''
    
    def void setPathPosition(int value) {
        this.positionPath = value;
    }
    
    def void setNextComma (boolean set) {
        this.nextComma = set;
    }
    
    def void removeEntry (String entry) {
        this.metadata.remove(entry);
    }
    
    def boolean showResource(String path, ResourceInteractionModel rim, Map<String, Set<String>> interactionsByPath, Map<String, State> stateByMethodPath) {
        
        var showResource = false;
        
        if(findAnnotation(rim.annotations, "tags")) {
               showResource = true;
        } else {
            for(String method : interactionsByPath.get(path)) {
                showResource =  findAnnotation(stateByMethodPath.get(path + method).annotations, "tags");
            }
        }
        return showResource;
    }
  
	def void collectInteractionsByPath(Map<String, Set<String>> result, State initial) {
		var states = new ArrayList<State>();
		collectInteractionsByPath(result, states, initial);
	}
	
	def void collectInteractionsByPath(Map<String, Set<String>> result, Collection<State> states, State currentState) {
		if (currentState == null || states.contains(currentState)) return;
		states.add(currentState);
		var sPath = getPath(currentState)
		var interactions = result.get(sPath);
		if (interactions == null)
			interactions = new HashSet<String>();
		
		// add interactions by iterating through the transitions from this state
		for (TransitionRef t : currentState.transitions) {
			if(t.state != null)
			{
				var path = getPath(t.state)
				interactions = result.get(path);
				if (interactions == null)
					interactions = new HashSet<String>();
				if (t.event.httpMethod != null)
					interactions.add(t.event.httpMethod);
					
				result.put(path, interactions);				
			}
			 
			collectInteractionsByPath(result, states, t.state);
		}		
	}
  
	def String getPath(State state) {
		var path = ""
		if (state.path == null) {
			path = state.name
		} else {
			path = state.path.name
		}
		return path;
	}

	def void collectStateByMethodPath(Map<String, State> result, State initial) {
		var states = new ArrayList<State>();
		collectStateByMethodPath(result, states, initial);
	}
	
	def void collectStateByMethodPath(Map<String, State> result, Collection<State> states, State currentState) {
		if (currentState == null || states.contains(currentState)) return;
		states.add(currentState);
		for (TransitionRef t : currentState.transitions) {
			if(t.state != null) {
				var path = getPath(t.state)
				
				if (t.event.httpMethod != null)
					result.put(path + t.event.httpMethod, t.state);
				collectStateByMethodPath(result, states, t.state);				
			}
		}		
	}
	
	def paramsPrint(String entityName, HashMap<String, Object> entityProps) '''
    "«entityName»":  {
        "type": "object",
        "properties": {
            «FOR property : entityProps.keySet SEPARATOR ','»
            "«property»" : {
                «IF entityProps.get(property) instanceof ArrayList<?>»
                    «var arraylistObj = entityProps.get(property) as ArrayList<String>»
                    «IF arraylistObj.get(0).equals("double")»
                        "type" : "number",
                        "format" : "«arraylistObj.get(0)»",
                        "description" : "«arraylistObj.get(1)»"
                    «ELSEIF arraylistObj.get(0).equals("date") || arraylistObj.get(0).equals("dateTime")»
                        "type" : "string",
                        "format" : "«arraylistObj.get(0)»",
                        "description" : "«arraylistObj.get(1)»"
                    «ELSE»
                        "type" : "«arraylistObj.get(0)»",
                        "description" : "«arraylistObj.get(1)»"
                    «ENDIF»
                «ELSE»
                «var drilldown = entityProps.get(property) as HashMap<String, Object>»
                «paramDrillPrint(drilldown)»
                «ENDIF»
            }
            «ENDFOR»
        }
    }
	'''
	
	def paramDrillPrint(HashMap<String, Object> drilldown) '''
    "type": "array",
    "items": {
        "type": "object",
        "properties" : {
            «FOR property : drilldown.keySet SEPARATOR ','»
            "«property»" : {
                «IF drilldown.get(property) instanceof ArrayList<?>»
                    «var drillArrayListObj = drilldown.get(property) as ArrayList<String>»
                    «IF drillArrayListObj.get(0).equals("double")»
                        "type" : "number",
                        "format" : "«drillArrayListObj.get(0)»",
                        "description" : "«drillArrayListObj.get(1)»"
                    «ELSEIF drillArrayListObj.get(0).equals("date")»
                        "type" : "string",
                        "format" : "«drillArrayListObj.get(0)»",
                        "description" : "«drillArrayListObj.get(1)»"
                     «ELSE»
                        "type" : "«drillArrayListObj.get(0)»",
                        "description" : "«drillArrayListObj.get(1)»"
                    «ENDIF»
                «ELSE»
                    «var drill = drilldown.get(property) as HashMap<String, Object>»
                    «paramDrillPrint(drill)»
                «ENDIF»
             }
            «ENDFOR»
        }
    }
	'''
	
	def StringBuilder createPathParameter(String description, String required, String value, boolean nextComma) {
	    
	    var valueComposer = new StringBuilder();
	    
	    if(nextComma) {
            valueComposer.append(",").append(System.lineSeparator());
        }
        
        valueComposer.append("{").append(System.lineSeparator());
        valueComposer.append(" \"name\": \"").append(value).append("\",").append(System.lineSeparator());
        valueComposer.append(" \"in\": \"path\",").append(System.lineSeparator());
        valueComposer.append(" \"description\": \"").append(description).append("\",").append(System.lineSeparator());
        valueComposer.append(" \"required\": ").append(required).append(",").append(System.lineSeparator());
        valueComposer.append(" \"type\": \"string\"").append(System.lineSeparator());
        valueComposer.append("}").append(System.lineSeparator());

	    return valueComposer;
	}
	
	def String getParams (EList<MethodRef> methodsList, String path, BasePath basePath, String method, State state) {
        
        var nextComma = false;
        var valueComposer = new StringBuilder();
        var placeHolderParamsOnPath = new HashSet();
        var pattern = Pattern.compile("(?:\\{)([0-9a-zA-Z.]+)(?:\\})");
        
        if(path != null && path.contains("{")) {
            
            var matcher = pattern.matcher(path);
            
            while (matcher.find()) {
                
                var pathValue = matcher.group(1);                
                if(!placeHolderParamsOnPath.contains(pathValue)) {
                    
                    placeHolderParamsOnPath.add(pathValue);
                    valueComposer.append(createPathParameter("Path parameter","true", pathValue, nextComma));
                    nextComma = true;
                }
            }
        }
        
        for(MethodRef methodRef : methodsList) {
            
            for(CommandProperty property : methodRef.command.properties) {
                
                if(property != null && !property.value.isNullOrEmpty) {
                    
                    var matcher = pattern.matcher(property.value);
                    while (matcher.find()) {                    
                        var parameter = matcher.group(1);
                        
                        if(!placeHolderParamsOnPath.contains(parameter)) {
                            if(nextComma) {
                                valueComposer.append(",").append(System.lineSeparator());
                            }
                            valueComposer.append("{").append(System.lineSeparator());
                            valueComposer.append(" \"name\": \"").append(parameter).append("\",").append(System.lineSeparator());
                            valueComposer.append(" \"in\": \"query\",").append(System.lineSeparator());
                            valueComposer.append(" \"description\": \"\",").append(System.lineSeparator());
                            valueComposer.append(" \"required\": true,").append(System.lineSeparator());
                            valueComposer.append(" \"type\": \"string\"").append(System.lineSeparator());
                            valueComposer.append("}").append(System.lineSeparator());
                            nextComma = true;
                        }
                    }
                }
            }
        }
        
        if(basePath != null && !basePath.name.isNullOrEmpty) {
            
            var matcher = pattern.matcher(basePath.name);
            
            while (matcher.find()) {
                valueComposer.append(createPathParameter("Base path parameter","true", matcher.group(1), nextComma));
                nextComma = true;
            }
        }
        
        if(method != null && !method.isNullOrEmpty && (method.equals("PUT") || method.equals("POST"))) {
            
            if(nextComma) {
                valueComposer.append(",").append(System.lineSeparator());
            }
            
            valueComposer.append("{").append(System.lineSeparator());
            valueComposer.append(" \"name\": \"body\",").append(System.lineSeparator());
            valueComposer.append(" \"in\": \"body\",").append(System.lineSeparator());
            valueComposer.append(" \"description\": \"-\",").append(System.lineSeparator());
            valueComposer.append(" \"required\": true,").append(System.lineSeparator());
            valueComposer.append(" \"schema\": { \"$ref\": \"#/definitions/").append(state.entity.name).append("\"}").append(System.lineSeparator());
            valueComposer.append("}").append(System.lineSeparator());
            
            nextComma = true;                     
        }
        
        valueComposer.append(
            createPathParameter("http://www.odata.org/documentation/odata-version-2-0/uri-conventions/#FilterSystemQueryOption","false", "$filter", nextComma)
        );
        
        valueComposer.append(
            createPathParameter("http://www.odata.org/documentation/odata-version-2-0/uri-conventions/#SkipSystemQueryOption","false", "$skip", nextComma)
        );
        
        valueComposer.append(
            createPathParameter("http://www.odata.org/documentation/odata-version-2-0/uri-conventions/#OrderbySystemQueryOption","false", "$orderby", nextComma)
        );
        
        valueComposer.append(
            createPathParameter("http://www.odata.org/documentation/odata-version-2-0/uri-conventions/#SelectSystemQueryOption","false", "$select", nextComma)
        );
        
        return valueComposer.toString();
    }
	
    def String getAnnotation (EList<MdfAnnotation> annotations, String name) {
        
        var value = "";
        
        if (annotations != null && annotations.size > 0) {
            for (MdfAnnotation annotation : annotations) {
                if(annotation.namespace.equals("Annotation") && annotation.name.equals(name)) {
                    value = annotation.properties.get(0);
                }
            }
        }
        
        return value; 
    }
        
    def boolean findAnnotation (EList<MdfAnnotation> annotations, String name) {
        for (MdfAnnotation annotation : annotations) {
            if (annotation.namespace.equals("Annotation") && annotation.name.equals(name)) {
                return true;
            }
        }
    }    
    	
    def getTags (EList<MdfAnnotation> annotations) '''
        «IF annotations != null»
            «FOR MdfAnnotation annotation : annotations»
                «IF annotation.namespace.equals("Annotation") && annotation.name.equals("tags")»
                    «FOR String tag : annotation.properties SEPARATOR ','»
                        "«tag»"
                    «ENDFOR»
                «ENDIF»
            «ENDFOR»
        «ELSE»
        ""
        «ENDIF»
    '''
}

