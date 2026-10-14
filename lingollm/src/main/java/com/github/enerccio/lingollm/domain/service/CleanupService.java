package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.traits.CleanupReference;

import java.util.*;

/**
 * Purges soft deleted entities that are no longer referenced.
 * <p>
 * Reference model is built from hibernate metamodel (associations) and {@link CleanupReference} annotations
 * (policies and soft references). Extensions can contribute additional references via {@link CleanupContributor}.
 */
public interface CleanupService {

    List<ReferenceDescriptor> getReferenceModel() throws Exception;

    CleanupPlan analyze() throws Exception;

    CleanupPlan purge() throws Exception;

    void registerContributor(CleanupContributor contributor);

    void unregisterContributor(CleanupContributor contributor);

    record EntityKey(Class<?> type, Long id) {
        @Override
        public String toString() {
            return type.getSimpleName() + " #" + id;
        }
    }

    interface BlockSink {
        void block(EntityKey target, String reason);
    }

    interface CleanupContributor {

        /**
         * Called during analysis, contributor blocks entities from candidates it still references.
         */
        default void collectReferences(Set<EntityKey> candidates, BlockSink sink) throws Exception {

        }

        /**
         * Called in purge transaction right before listed entities are removed.
         */
        default void beforePurge(Set<EntityKey> purged) throws Exception {

        }
    }

    class ReferenceDescriptor {
        private final String referrerEntity;
        private final Class<?> referrerType;
        private final String field;
        private final Class<?> targetType;
        private final String targetClassField;
        private final CleanupReference.Policy policy;
        private final boolean association;
        private final boolean collection;
        private final boolean persistent;

        public ReferenceDescriptor(String referrerEntity, Class<?> referrerType, String field, Class<?> targetType,
                                   String targetClassField, CleanupReference.Policy policy,
                                   boolean association, boolean collection, boolean persistent) {
            this.referrerEntity = referrerEntity;
            this.referrerType = referrerType;
            this.field = field;
            this.targetType = targetType;
            this.targetClassField = targetClassField;
            this.policy = policy;
            this.association = association;
            this.collection = collection;
            this.persistent = persistent;
        }

        /**
         * JPA entity name used in queries.
         */
        public String getReferrerEntity() {
            return referrerEntity;
        }

        /**
         * Root entity type of the referrer.
         */
        public Class<?> getReferrerType() {
            return referrerType;
        }

        public String getField() {
            return field;
        }

        /**
         * Root entity type of the target, null for polymorphic soft reference.
         */
        public Class<?> getTargetType() {
            return targetType;
        }

        public String getTargetClassField() {
            return targetClassField;
        }

        public CleanupReference.Policy getPolicy() {
            return policy;
        }

        public boolean isAssociation() {
            return association;
        }

        public boolean isCollection() {
            return collection;
        }

        /**
         * False when reference is stored in extended attributes and can't be queried.
         */
        public boolean isPersistent() {
            return persistent;
        }

        @Override
        public String toString() {
            return referrerEntity + "." + field + " -> " + (targetType != null ? targetType.getSimpleName() : "*") + " (" + policy + ")";
        }
    }

    class EntityStats {
        private final Class<?> type;
        private int softDeleted;
        private int purgeable;
        private int blocked;

        public EntityStats(Class<?> type) {
            this.type = type;
        }

        public Class<?> getType() {
            return type;
        }

        public int getSoftDeleted() {
            return softDeleted;
        }

        public void setSoftDeleted(int softDeleted) {
            this.softDeleted = softDeleted;
        }

        public int getPurgeable() {
            return purgeable;
        }

        public void setPurgeable(int purgeable) {
            this.purgeable = purgeable;
        }

        public int getBlocked() {
            return blocked;
        }

        public void setBlocked(int blocked) {
            this.blocked = blocked;
        }
    }

    class BlockedEntity {
        private final EntityKey key;
        private String label;
        private final List<String> reasons = new ArrayList<>();

        public BlockedEntity(EntityKey key) {
            this.key = key;
        }

        public EntityKey getKey() {
            return key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public List<String> getReasons() {
            return reasons;
        }
    }

    class CleanupPlan {
        private final Set<EntityKey> purge = new LinkedHashSet<>();
        private final Map<Class<?>, EntityStats> stats = new LinkedHashMap<>();
        private final Map<EntityKey, BlockedEntity> blocked = new LinkedHashMap<>();
        private boolean executed;

        public Set<EntityKey> getPurge() {
            return purge;
        }

        public Map<Class<?>, EntityStats> getStats() {
            return stats;
        }

        public Map<EntityKey, BlockedEntity> getBlocked() {
            return blocked;
        }

        public boolean isExecuted() {
            return executed;
        }

        public void setExecuted(boolean executed) {
            this.executed = executed;
        }
    }
}
