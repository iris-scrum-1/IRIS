package com.temenos.interaction.core.hypermedia;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;


/**
 * Implementation of {@link LinkToFieldAssociation}
 */
public class LinkToFieldAssociationImpl implements LinkToFieldAssociation {

    private static final Pattern COLLECTION_PARAM_PATTERN = Pattern.compile("\\{{1}([a-zA-Z0-9]+(\\.[a-zA-Z0-9]+)+)\\}{1}");
    private static final String PARAM_REPLACEMENT_REGEX = "\\((\\d+)\\)";
    private static final Logger logger = LoggerFactory.getLogger(LinkToFieldAssociationImpl.class);
    private Transition transition;
    private String targetFieldName;
    private List<String> transitionCollectionParams;
    private Boolean hasCollectionDynamicResourceName;
    private Map<String, Object> transitionProperties;
    private Map<String, Object> normalisedTransitionProperties;

    public LinkToFieldAssociationImpl(Transition transition, Map<String, Object> properties) {
        this.transition = transition;
        targetFieldName = transition.getSourceField();
        transitionCollectionParams = getCollectionParams(transition.getCommand().getUriParameters());
        hasCollectionDynamicResourceName = hasCollectionDynamicResourceName();
        transitionProperties = properties;
        normalisedTransitionProperties = HypermediaTemplateHelper.normalizeProperties(properties);
    }

    @Override
    public boolean isTransitionSupported() {

        if (targetFieldName == null && (!transitionCollectionParams.isEmpty() || hasCollectionDynamicResourceName)) {
            logger.error("Cannot generate links for transition " + transition + ". Target field name cannot be null if we have collection parameters or a collection dymamic resource.");
            return false;
        }

        if (hasCollectionDynamicResourceName && !StringUtils.equals(getParentNameOfCollectionValue(targetFieldName), getFirstParentNameOfDynamicResource())){
            logger.error("Cannot generate links for transition " + transition + ". Parent of target field name and dynamic resource must be same.");
            return false;
        }
        
        if(hasCollectionDynamicResourceName && !allParametersHaveSameParent(((DynamicResourceState) transition.getTarget()).getResourceLocatorArgs())){
            logger.error("Cannot generate links for transition " + transition + ". All multivalue fields in the parameter list of the dynamic resource must have the same parent.");
            return false;
        }

        if (!transitionCollectionParams.isEmpty() && !allParametersHaveSameParent(transitionCollectionParams.toArray(new String[0]))) {
            logger.error("Cannot generate links for transition " + transition + ". All collection parameters must have the same parent.");
            return false;
        }

        return true;
    }

    @Override
    public List<LinkProperties> getProperties() {

        List<LinkProperties> transitionPropertiesList = new ArrayList<LinkProperties>();

        //Get list of child names, i.e. For AB.CD and AA.ZZ add CD, ZZ to list
        List<String> childParamNames = new ArrayList<String>();
        for (String collectionParam : transitionCollectionParams) {
            childParamNames.add(getChildNameOfCollectionValue(collectionParam));
        }

        if (targetFieldName != null && targetFieldName.contains(".")) // Multivalue target
        {
            createLinkPropertiesForMultivalueTarget(transitionPropertiesList, childParamNames);
        } else // Non multivalue target
        {
            createLinkPropertiesForSingleTarget(transitionPropertiesList, childParamNames);
        }

        logger.debug("Created " + transitionPropertiesList.size() + " properties map(s) for transition " + transition);

        return transitionPropertiesList;
    }

    private void createLinkPropertiesForMultivalueTarget(List<LinkProperties> transitionPropertiesList, List<String> childParamNames) {
        List<String> targetFields = extractMatchingFieldsFromTransitionProperties(targetFieldName);
        //If properties does not contain the target, add unresolved target
        if(targetFields.isEmpty()) {            
            targetFields.add(targetFieldName);
        }
        
        String parentTargetFieldName = getParentNameOfCollectionValue(targetFieldName);

        boolean hasSameParent = false;
        String parentResolvedName = new String();        
        if (transitionCollectionParams.size() > 0) {
            String firstCollectionParam = transitionCollectionParams.get(0);
            //Determine if parent of target field and parent of parameters are same 
            hasSameParent = StringUtils.equals(getParentNameOfCollectionValue(firstCollectionParam), parentTargetFieldName);
            if(!hasSameParent)
            {
                parentResolvedName = getParentOfMultivalueChildren();
            }
        }
        
        int targetFieldIndex = 0;
        for (String targetField : targetFields) // Generate one or more map of properties for each target field
        {
            List<String> resolvedDynamicResourceFieldNames = getDynamicResourceResolvedFieldName(transition.getTarget(), targetField);
            if (transitionCollectionParams.isEmpty()) // Create one link properties map per target
            {
                LinkProperties linkProps = createLinkProperties(targetField, resolvedDynamicResourceFieldNames, childParamNames, null);
                transitionPropertiesList.add(linkProps);
            } else if (hasSameParent) // Create one link properties map per target
            {
                String parentResolvedTargetFieldName = getParentNameOfCollectionValue(targetField);
                LinkProperties linkProps = createLinkProperties(targetField, resolvedDynamicResourceFieldNames, childParamNames, parentResolvedTargetFieldName);
                transitionPropertiesList.add(linkProps);
            } else { 
                // Create one properties maps per target.
                    String childParentResolvedParamNewIndex = parentResolvedName + "(" + targetFieldIndex + ")";
                    LinkProperties linkProps = createLinkProperties(targetField, resolvedDynamicResourceFieldNames, childParamNames, childParentResolvedParamNewIndex);
                    transitionPropertiesList.add(linkProps);
                    targetFieldIndex ++;
            }
        }
    }

    private void createLinkPropertiesForSingleTarget(List<LinkProperties> transitionPropertiesList, List<String> childParamNames) {
        if (transitionCollectionParams.isEmpty()) // Create one link properties map per target
        {
            LinkProperties linkProps = new LinkProperties(targetFieldName, transitionProperties);
            transitionPropertiesList.add(linkProps);
        } else {
            int numOfChildren = getNumberOfMultivalueChildren();
            String parentResolvedName = getParentOfMultivalueChildren();

            // Create multiple properties maps per target. Depends on the number of children in the entity in transition properties
            for (int i = 0; i <= numOfChildren; i++) {
                String childParentResolvedParamNewIndex = parentResolvedName + "(" + i + ")";
                LinkProperties linkProps = createLinkProperties(targetFieldName, null, childParamNames, childParentResolvedParamNewIndex);
                transitionPropertiesList.add(linkProps);
            }
        }
    }

    private LinkProperties createLinkProperties(String targetField, List<String> resolvedDynamicResourceFieldNames, List<String> childParamNames, String resolvedParentName) {
        List<String> paramPropertyKeys = new ArrayList<String>();
        if (StringUtils.isNotBlank(resolvedParentName)) {
            paramPropertyKeys = getListOfParamPropertyKeys(childParamNames, resolvedParentName);
        }

        if (!CollectionUtils.isEmpty(resolvedDynamicResourceFieldNames)) {
            paramPropertyKeys.addAll(resolvedDynamicResourceFieldNames);
        }
        LinkProperties linkProps = new LinkProperties(targetField, transitionProperties);
        addEntriesToLinkProperties(linkProps, paramPropertyKeys);
        return linkProps;
    }

    private List<String> getListOfParamPropertyKeys(List<String> childParamNames, String fullyQualifiedParentName) {
        List<String> propertyKeys = new ArrayList<String>();

        for (String childParam : childParamNames) {
            propertyKeys.add(fullyQualifiedParentName + "." + childParam);
        }

        return propertyKeys;
    }

    private List<String> getDynamicResourceResolvedFieldName(ResourceState targetResourceState, String targetField) {
        List<String> resolvedFieldNames = new ArrayList<String>();
        if (hasCollectionDynamicResourceName) {
            String targetFieldParentName = getParentNameOfCollectionValue(targetField);
            if (targetResourceState instanceof DynamicResourceState) {
                String[] args = ArrayUtils.nullToEmpty(((DynamicResourceState) targetResourceState).getResourceLocatorArgs());
                for(int index = 0; args.length > index ; index++ ){
                    String argValue = args[index];
                    if(StringUtils.contains(argValue, ".")){
                        resolvedFieldNames.add(targetFieldParentName + "." + getChildNameOfCollectionValue(argValue).replaceAll("\\}", ""));
                    }
                }
            }
        }
        return resolvedFieldNames;
    }
    
    private int getNumberOfMultivalueChildren() {
        //Find the number of children using the index of the parent in transition properties
        //i.e. if we have A(0).B and A(1).B in the properties map, return 1
        int numChildren = 0;
        for (String collectionParam : transitionCollectionParams) {
            List<String> entityParamFields = extractMatchingFieldsFromTransitionProperties(collectionParam);
            for (String entityParam : entityParamFields) {
                int index = Integer.parseInt(entityParam.substring(entityParam.lastIndexOf("(") + 1, entityParam.lastIndexOf(")")));
                if (index > numChildren) {
                    numChildren = index;
                }
            }
        }
        return numChildren;
    }
    
    private String getParentOfMultivalueChildren() {
        String collectionParam = transitionCollectionParams.get(0);
        List<String> matchingFields = extractMatchingFieldsFromTransitionProperties(collectionParam);
        String parent = getParentNameOfCollectionValue(matchingFields.get(0));
        return parent.substring(0, parent.lastIndexOf("("));        
    }

    private List<String> getCollectionParams(Map<String, String> transitionUriMap) {
        List<String> collectionParams = new ArrayList<String>();
        if (transitionUriMap == null) {
            return collectionParams;
        }

        for (Map.Entry<String, String> entry : transitionUriMap.entrySet()) {
            String parameter = entry.getValue();
            Matcher matcher = COLLECTION_PARAM_PATTERN.matcher(parameter);
            while (matcher.find()) {
                collectionParams.add(matcher.group(1));
            }
        }
        return collectionParams;
    }

    private boolean hasCollectionDynamicResourceName() {
        return getFirstParentNameOfDynamicResource() != null ? true : false;
    }

    private String getFirstParentNameOfDynamicResource() {
        String firstParent = null;
        if (transition.getTarget() instanceof DynamicResourceState) {
            String[] args = ArrayUtils.nullToEmpty(((DynamicResourceState) transition.getTarget()).getResourceLocatorArgs());
            for (String argValue : args) {
                if(StringUtils.contains(argValue, ".")){
                    firstParent = StringUtils.replace(getParentNameOfCollectionValue(argValue), "{", "");//Remove starting curly brace {
                    break;
                }
            }
        }
        return firstParent;
    }

    private List<String> extractMatchingFieldsFromTransitionProperties(String fieldName) {
        List<String> matchingFieldList = new ArrayList<String>();
        for (String key : normalisedTransitionProperties.keySet()) {
            if (StringUtils.equals(fieldName, key.replaceAll(PARAM_REPLACEMENT_REGEX, ""))) {
                matchingFieldList.add(key);
            }
        }

        return matchingFieldList;
    }

    private String getParentNameOfCollectionValue(String value) {
        if (StringUtils.isNotBlank(value) && value.contains(".")) {
            return value.substring(0, value.lastIndexOf("."));
        }
        return null;
    }

    private String getChildNameOfCollectionValue(String value) {
        if (StringUtils.isNotBlank(value) && value.contains(".")) {
            return value.substring(value.lastIndexOf(".") + 1);
        }
        return null;
    }

    private boolean allParametersHaveSameParent(String... parameters) {
        boolean haveSameParent = true;
        List<String> parents = new ArrayList<String>();
        for (String param : parameters) {
            String parent = getParentNameOfCollectionValue(param);
            if (parent == null) {
                //Do nothing. Dealing with non multivalue field
            } else if (parents.isEmpty()) {
                parents.add(parent);
            } else if (!parents.contains(parent)) {
                haveSameParent = false;
                break;
            }
        }

        return haveSameParent;
    }

    private void addEntriesToLinkProperties(LinkProperties linkProps, List<String> keys) {
        Map<String, Object> linkPropertiesMap = linkProps.getTransitionProperties();
        Set<String> unindexedKeys = new HashSet<String>();
        for (String key : keys) {
            Object value = this.normalisedTransitionProperties.get(key);
            String unindexedKey = key.replaceAll(PARAM_REPLACEMENT_REGEX, "");
            unindexedKeys.add(unindexedKey);
            if (value != null && value instanceof String) {
                linkPropertiesMap.put(unindexedKey, value);
            } else {
                linkPropertiesMap.put(unindexedKey, "");
            }
        }

        // Replace Id={A.B.C} with Id={VAL} if A.B.C=VAL is in the linkPropertiesMap
        for (String key : linkPropertiesMap.keySet()) {
            Object value = linkPropertiesMap.get(key);
            if (value instanceof String) {

                String replacementKey = ((String) value).replaceAll("\\{", "").replaceAll("\\}", "");

                if (unindexedKeys.contains(replacementKey)) {
                    String replacementValue = ((String) value).replaceAll("\\{" + Pattern.quote(replacementKey) + "\\}", linkPropertiesMap.get(replacementKey).toString());
                    linkPropertiesMap.put(key, replacementValue);
                }
            }
        }
    }
}
