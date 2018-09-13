/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.atlas.discovery;


import org.apache.atlas.AtlasErrorCode;
import org.apache.atlas.exception.AtlasBaseException;
import org.apache.atlas.model.discovery.SearchParameters;
import org.apache.atlas.model.discovery.SearchParameters.FilterCriteria;
import org.apache.atlas.model.typedef.AtlasClassificationDef;
import org.apache.atlas.repository.graphdb.AtlasGraph;
import org.apache.atlas.type.AtlasClassificationType;
import org.apache.atlas.type.AtlasEntityType;
import org.apache.atlas.type.AtlasStructType;
import org.apache.atlas.type.AtlasTypeRegistry;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.HashSet;
import java.util.Set;

/*
 * Search context captures elements required for performing a basic search
 * For every search request the search context will determine the execution sequence of the search processor(s) and the
 * possible chaining of processor(s)
 */
public class SearchContext {
    private final SearchParameters        searchParameters;
    private final AtlasTypeRegistry       typeRegistry;
    private final AtlasGraph              graph;
    private final Set<String>             indexedKeys;
    private final Set<String>             entityAttributes;
    private final AtlasEntityType         entityType;
    private final AtlasClassificationType classificationType;
    private       SearchProcessor         searchProcessor;
    private       boolean                 terminateSearch = false;

    public final static AtlasClassificationType MATCH_ALL_CLASSIFICATION = new AtlasClassificationType(new AtlasClassificationDef("*"));

    public SearchContext(SearchParameters searchParameters, AtlasTypeRegistry typeRegistry, AtlasGraph graph, Set<String> indexedKeys) throws AtlasBaseException {
        String classificationName = searchParameters.getClassification();

        this.searchParameters   = searchParameters;
        this.typeRegistry       = typeRegistry;
        this.graph              = graph;
        this.indexedKeys        = indexedKeys;
        this.entityAttributes   = new HashSet<>();
        this.entityType         = typeRegistry.getEntityTypeByName(searchParameters.getTypeName());
        this.classificationType = getClassificationType(classificationName);

        // Validate if the type name exists
        if (StringUtils.isNotEmpty(searchParameters.getTypeName()) && entityType == null) {
            throw new AtlasBaseException(AtlasErrorCode.UNKNOWN_TYPENAME, searchParameters.getTypeName());
        }

        // Validate if the classification exists
        if (StringUtils.isNotEmpty(classificationName) && classificationType == null) {
            throw new AtlasBaseException(AtlasErrorCode.UNKNOWN_CLASSIFICATION, classificationName);
        }

        // Invalid attributes will raise an exception with 400 error code
        validateAttributes(entityType, searchParameters.getEntityFilters());

        // Invalid attributes will raise an exception with 400 error code
        validateAttributes(classificationType, searchParameters.getTagFilters());

        if (needFullTextProcessor()) {
            addProcessor(new FullTextSearchProcessor(this));
        }

        if (needClassificationProcessor()) {
            addProcessor(new ClassificationSearchProcessor(this));
        }

        if (needEntityProcessor()) {
            addProcessor(new EntitySearchProcessor(this));
        }
    }

    public SearchParameters getSearchParameters() { return searchParameters; }

    public AtlasTypeRegistry getTypeRegistry() { return typeRegistry; }

    public AtlasGraph getGraph() { return graph; }

    public Set<String> getIndexedKeys() { return indexedKeys; }

    public Set<String> getEntityAttributes() { return entityAttributes; }

    public AtlasEntityType getEntityType() { return entityType; }

    public AtlasClassificationType getClassificationType() { return classificationType; }

    public SearchProcessor getSearchProcessor() { return searchProcessor; }

    public boolean terminateSearch() { return terminateSearch; }

    public void terminateSearch(boolean terminateSearch) { this.terminateSearch = terminateSearch; }

    public StringBuilder toString(StringBuilder sb) {
        if (sb == null) {
            sb = new StringBuilder();
        }

        sb.append("searchParameters=");

        if (searchParameters != null) {
            searchParameters.toString(sb);
        }

        return sb;
    }

    @Override
    public String toString() {
        return toString(new StringBuilder()).toString();
    }

    boolean needFullTextProcessor() {
        return StringUtils.isNotEmpty(searchParameters.getQuery());
    }

    boolean needClassificationProcessor() {
        return classificationType != null && (entityType == null || hasAttributeFilter(searchParameters.getTagFilters()));
    }

    boolean needEntityProcessor() {
        return entityType != null;
    }

    private void validateAttributes(final AtlasStructType structType, final FilterCriteria filterCriteria) throws AtlasBaseException {
        if (filterCriteria != null) {
            FilterCriteria.Condition condition = filterCriteria.getCondition();

            if (condition != null && CollectionUtils.isNotEmpty(filterCriteria.getCriterion())) {
                for (FilterCriteria criteria : filterCriteria.getCriterion()) {
                    validateAttributes(structType, criteria);
                }
            } else {
                String attributeName = filterCriteria.getAttributeName();

                if (StringUtils.isNotEmpty(attributeName) && structType.getAttributeType(attributeName) == null) {
                    throw new AtlasBaseException(AtlasErrorCode.UNKNOWN_ATTRIBUTE, attributeName, structType.getTypeName());
                }
            }
        }
    }

    private boolean hasAttributeFilter(FilterCriteria filterCriteria) {
        return filterCriteria != null &&
               (CollectionUtils.isNotEmpty(filterCriteria.getCriterion()) || StringUtils.isNotEmpty(filterCriteria.getAttributeName()));
    }

    private void addProcessor(SearchProcessor processor) {
        if (searchProcessor == null) {
            searchProcessor = processor;
        } else {
            searchProcessor.addProcessor(processor);
        }
    }

    private AtlasClassificationType getClassificationType(String classificationName) {
        AtlasClassificationType ret;

        if (StringUtils.equals(classificationName, MATCH_ALL_CLASSIFICATION.getTypeName())) {
            ret = MATCH_ALL_CLASSIFICATION;
        } else {
            ret = typeRegistry.getClassificationTypeByName(classificationName);
        }

        return ret;
    }
}
