/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.atlas.repository.audit;

import com.google.inject.Inject;
import org.apache.atlas.ApplicationProperties;
import org.apache.atlas.AtlasException;
import org.apache.atlas.EntityAuditEvent;
import org.apache.atlas.RequestContext;
import org.apache.atlas.listener.EntityChangeListener;
import org.apache.atlas.typesystem.IReferenceableInstance;
import org.apache.atlas.typesystem.IStruct;
import org.apache.atlas.typesystem.ITypedReferenceableInstance;
import org.apache.atlas.typesystem.json.InstanceSerialization;
import org.apache.atlas.typesystem.types.AttributeInfo;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Listener on entity create/update/delete, tag add/delete. Adds the corresponding audit event to the audit repository.
 */
public class EntityAuditListener implements EntityChangeListener {
    private static final Logger LOG = LoggerFactory.getLogger(EntityAuditListener.class);

    private EntityAuditRepository auditRepository;
    public static final String ATLAS_HBASE_KEYVALUE_MAX_SIZE     = "atlas.hbase.client.keyvalue.maxsize";
    public static final String AUDIT_EXCLUDE_ATTRIBUTE_PROPERTY  = "atlas.audit.hbase.entity";
    public static final int    ATLAS_HBASE_KEYVALUE_DEFAULT_SIZE = 1024 * 1024;

    private Map<String, List<String>> auditExcludedAttributesCache = new HashMap<>();

    static Configuration APPLICATION_PROPERTIES = null;

    @Inject
    public EntityAuditListener(EntityAuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    @Override
    public void onEntitiesAdded(Collection<ITypedReferenceableInstance> entities) throws AtlasException {
        List<EntityAuditEvent> events = new ArrayList<>();
        long currentTime = RequestContext.get().getRequestTime();

        for (ITypedReferenceableInstance entity : entities) {
            EntityAuditEvent event = createEvent(entity, currentTime, EntityAuditEvent.EntityAuditAction.ENTITY_CREATE);

            events.add(event);
        }

        auditRepository.putEvents(events);
    }

    @Override
    public void onEntitiesUpdated(Collection<ITypedReferenceableInstance> entities) throws AtlasException {
        List<EntityAuditEvent> events = new ArrayList<>();
        long currentTime = RequestContext.get().getRequestTime();

        for (ITypedReferenceableInstance entity : entities) {
            EntityAuditEvent event = createEvent(entity, currentTime, EntityAuditEvent.EntityAuditAction.ENTITY_UPDATE);

            events.add(event);
        }

        auditRepository.putEvents(events);
    }

    @Override
    public void onTraitAdded(ITypedReferenceableInstance entity, IStruct trait) throws AtlasException {
        EntityAuditEvent event = createEvent(entity, RequestContext.get().getRequestTime(),
                EntityAuditEvent.EntityAuditAction.TAG_ADD,
                "Added trait: " + InstanceSerialization.toJson(trait, true));

        auditRepository.putEvents(event);
    }

    @Override
    public void onTraitDeleted(ITypedReferenceableInstance entity, String traitName) throws AtlasException {
        EntityAuditEvent event = createEvent(entity, RequestContext.get().getRequestTime(),
                EntityAuditEvent.EntityAuditAction.TAG_DELETE, "Deleted trait: " + traitName);

        auditRepository.putEvents(event);
    }

    @Override
    public void onEntitiesDeleted(Collection<ITypedReferenceableInstance> entities) throws AtlasException {
        List<EntityAuditEvent> events = new ArrayList<>();
        long currentTime = RequestContext.get().getRequestTime();

        for (ITypedReferenceableInstance entity : entities) {
            EntityAuditEvent event = createEvent(entity, currentTime,
                    EntityAuditEvent.EntityAuditAction.ENTITY_DELETE, "Deleted entity");

            events.add(event);
        }

        auditRepository.putEvents(events);
    }

    private EntityAuditEvent createEvent(ITypedReferenceableInstance entity, long ts,
                                         EntityAuditEvent.EntityAuditAction action)
            throws AtlasException {
        String detail = getAuditEventDetail(entity, action);

        return createEvent(entity, ts, action, detail);
    }

    private EntityAuditEvent createEvent(ITypedReferenceableInstance entity, long ts,
                                         EntityAuditEvent.EntityAuditAction action, String details)
            throws AtlasException {
        return new EntityAuditEvent(entity.getId()._getId(), ts, RequestContext.get().getUser(), action, details, entity);
    }

    private String getAuditEventDetail(ITypedReferenceableInstance entity, EntityAuditEvent.EntityAuditAction action) throws AtlasException {
        Map<String, Object> prunedAttributes = pruneEntityAttributesForAudit(entity);

        String auditPrefix = getAuditPrefix(action);
        String auditString = auditPrefix + InstanceSerialization.toJson(entity, true);
        byte[] auditBytes  = auditString.getBytes(StandardCharsets.UTF_8);
        long   auditSize   = auditBytes != null ? auditBytes.length : 0;

        if (auditSize > getMaxAuditDetailSize()) { // don't store attributes in audit
            LOG.warn("audit record too long: entityType={}, guid={}, size={}; maxSize={}. entity attribute values not stored in audit",
                     entity.getTypeName(), entity.getId()._getId(), auditSize, getMaxAuditDetailSize());

            Map<String, Object> attrValues = entity.getValuesMap();

            clearAttributeValues(entity);

            auditString = auditPrefix + InstanceSerialization.toJson(entity, true);

            addAttributeValues(entity, attrValues);
        }

        restoreEntityAttributes(entity, prunedAttributes);

        return auditString;
    }

    private void clearAttributeValues(IReferenceableInstance entity) throws AtlasException {
        Map<String, Object> attributesMap = entity.getValuesMap();

        if (MapUtils.isNotEmpty(attributesMap)) {
            for (String attribute : attributesMap.keySet()) {
                entity.setNull(attribute);
            }
        }
    }

    private void addAttributeValues(ITypedReferenceableInstance entity, Map<String, Object> attributesMap) throws AtlasException {
        if (MapUtils.isNotEmpty(attributesMap)) {
            for (String attr : attributesMap.keySet()) {
                entity.set(attr, attributesMap.get(attr));
            }
        }
    }

    private Map<String, Object> pruneEntityAttributesForAudit(ITypedReferenceableInstance entity) throws AtlasException {
        Map<String, Object> ret               = null;
        Map<String, Object> entityAttributes  = entity.getValuesMap();
        List<String>        excludeAttributes = getAuditExcludeAttributes(entity.getTypeName());

        if (CollectionUtils.isNotEmpty(excludeAttributes) && MapUtils.isNotEmpty(entityAttributes)) {
            Map<String, AttributeInfo> attributeInfoMap = entity.fieldMapping().fields;

            for (String attrName : entityAttributes.keySet()) {
                Object        attrValue = entityAttributes.get(attrName);
                AttributeInfo attrInfo  = attributeInfoMap.get(attrName);

                if (excludeAttributes.contains(attrName)) {
                    if (ret == null) {
                        ret = new HashMap<>();
                    }

                    ret.put(attrName, attrValue);
                    entity.setNull(attrName);
                } else if (attrInfo.isComposite) {
                    if (attrValue instanceof Collection) {
                        for (Object attribute : (Collection) attrValue) {
                            if (attribute instanceof ITypedReferenceableInstance) {
                                ITypedReferenceableInstance attrInstance = (ITypedReferenceableInstance) attribute;
                                Map<String, Object>         prunedAttrs  = pruneEntityAttributesForAudit(attrInstance);

                                if (MapUtils.isNotEmpty(prunedAttrs)) {
                                    if (ret == null) {
                                        ret = new HashMap<>();
                                    }

                                    ret.put(attrInstance.getId()._getId(), prunedAttrs);
                                }
                            }
                        }
                    } else if (attrValue instanceof ITypedReferenceableInstance) {
                        ITypedReferenceableInstance attrInstance = (ITypedReferenceableInstance) attrValue;
                        Map<String, Object>         prunedAttrs  = pruneEntityAttributesForAudit(attrInstance);

                        if (MapUtils.isNotEmpty(prunedAttrs)) {
                            if (ret == null) {
                                ret = new HashMap<>();
                            }

                            ret.put(attrInstance.getId()._getId(), prunedAttrs);
                        }
                    }
                }
            }
        }

        return ret;
    }

    private void restoreEntityAttributes(ITypedReferenceableInstance entity, Map<String, Object> prunedAttributes) throws AtlasException {
        if (MapUtils.isEmpty(prunedAttributes)) {
            return;
        }

        Map<String, Object> entityAttributes = entity.getValuesMap();

        if (MapUtils.isNotEmpty(entityAttributes)) {
            Map<String, AttributeInfo> attributeInfoMap = entity.fieldMapping().fields;

            for (String attrName : entityAttributes.keySet()) {
                Object        attrValue = entityAttributes.get(attrName);
                AttributeInfo attrInfo  = attributeInfoMap.get(attrName);

                if (prunedAttributes.containsKey(attrName)) {
                    entity.set(attrName, prunedAttributes.get(attrName));
                } else if (attrInfo.isComposite) {
                    if (attrValue instanceof Collection) {
                        for (Object attributeEntity : (Collection) attrValue) {
                            if (attributeEntity instanceof ITypedReferenceableInstance) {
                                ITypedReferenceableInstance attrInstance = (ITypedReferenceableInstance) attributeEntity;
                                Object                      obj          = prunedAttributes.get(attrInstance.getId()._getId());

                                if (obj instanceof Map) {
                                    restoreEntityAttributes(attrInstance, (Map) obj);
                                }
                            }
                        }
                    } else if (attrValue instanceof ITypedReferenceableInstance) {
                        ITypedReferenceableInstance attrInstance = (ITypedReferenceableInstance) attrValue;
                        Object                      obj          = prunedAttributes.get(attrInstance.getId()._getId());

                        if (obj instanceof Map) {
                            restoreEntityAttributes(attrInstance, (Map) obj);
                        }
                    }
                }
            }
        }
    }

    private String getAuditPrefix(EntityAuditEvent.EntityAuditAction action) {
        final String ret;

        switch (action) {
            case ENTITY_CREATE:
                ret = "Created: ";
                break;
            case ENTITY_UPDATE:
                ret = "Updated: ";
                break;
            case ENTITY_DELETE:
                ret = "Deleted: ";
                break;
            case TAG_ADD:
                ret = "Added trait: ";
                break;
            case TAG_DELETE:
                ret = "Deleted trait: ";
                break;
            default:
                ret = "Unknown: ";
        }

        return ret;
    }

    private int getMaxAuditDetailSize() {
        initApplicationProperties();

        return (APPLICATION_PROPERTIES == null) ? ATLAS_HBASE_KEYVALUE_DEFAULT_SIZE
                      : APPLICATION_PROPERTIES.getInt(ATLAS_HBASE_KEYVALUE_MAX_SIZE, ATLAS_HBASE_KEYVALUE_DEFAULT_SIZE);
    }

    private List<String> getAuditExcludeAttributes(String entityType) {
        List<String> ret = null;

        initApplicationProperties();

        if (auditExcludedAttributesCache.containsKey(entityType)) {
            ret = auditExcludedAttributesCache.get(entityType);
        } else if (APPLICATION_PROPERTIES != null) {
            String[] excludeAttributes = APPLICATION_PROPERTIES.getStringArray(AUDIT_EXCLUDE_ATTRIBUTE_PROPERTY + "." + entityType + "." + "attributes.exclude");

            if (excludeAttributes != null) {
                ret = Arrays.asList(excludeAttributes);
            }

            auditExcludedAttributesCache.put(entityType, ret);
        }

        return ret;
    }

    private void initApplicationProperties() {
        if (APPLICATION_PROPERTIES == null) {
            try {
                APPLICATION_PROPERTIES = ApplicationProperties.get();
            } catch (AtlasException excp) {
                // ignore
            }
        }
    }

}
