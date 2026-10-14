package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.service.CleanupService.EntityKey;

import java.util.*;

/**
 * Soft deleted objects that can still be restored (before cleanup purges them).
 * <p>
 * A user sees and restores only their own deleted objects, an administrator all of them (and can filter by owner).
 * The reference model is the one of {@link CleanupService}: an object can't be restored while an object it depends on
 * (owner, parent, strong reference, the object owning it) is deleted too, unless that object is restored together
 * with it.
 */
public interface TrashService {

    /**
     * @param ownerId only objects of this user, ignored for non administrators (they only see their own); null is all
     * @param type    only objects of this type (root entity class from {@link #getTypes()}); null is all
     */
    record TrashFilter(Long ownerId, Class<?> type) {
        public static TrashFilter all() {
            return new TrashFilter(null, null);
        }
    }

    /**
     * @param deletedAt   last modification, which is the delete itself unless something changed the object since
     * @param extendable  the type has extended content, see {@link #getExtendedContent(EntityKey)}
     */
    record TrashItem(EntityKey key, String uuid, String label, Long ownerId, String ownerLogin, Date deletedAt,
                     boolean extendable) {
    }

    /**
     * Deleted object the restored one depends on.
     *
     * @param field the reference (field of the restored object, or of the parent when it owns the restored one)
     */
    record Blocker(EntityKey parent, String label, String field) {
    }

    class RestoreResult {
        private final Set<EntityKey> restored = new LinkedHashSet<>();
        private final Map<EntityKey, List<Blocker>> blocked = new LinkedHashMap<>();
        private final Map<EntityKey, String> blockedLabels = new HashMap<>();

        /**
         * Everything that was restored, empty when anything is blocked (restore is all or nothing).
         */
        public Set<EntityKey> getRestored() {
            return restored;
        }

        /**
         * Requested objects that can't be restored together with the selection, with the deleted objects they depend on.
         */
        public Map<EntityKey, List<Blocker>> getBlocked() {
            return blocked;
        }

        public Map<EntityKey, String> getBlockedLabels() {
            return blockedLabels;
        }

        public boolean isBlocked() {
            return !blocked.isEmpty();
        }
    }

    class DeleteResult {
        private final Set<EntityKey> deleted = new LinkedHashSet<>();
        private final Map<EntityKey, List<String>> blocked = new LinkedHashMap<>();
        private final Map<EntityKey, String> blockedLabels = new HashMap<>();

        /**
         * Everything that was removed (the requested objects and what belongs to them), empty when anything is
         * blocked (the delete is all or nothing).
         */
        public Set<EntityKey> getDeleted() {
            return deleted;
        }

        /**
         * Requested objects that can't be removed, with the reasons.
         */
        public Map<EntityKey, List<String>> getBlocked() {
            return blocked;
        }

        public Map<EntityKey, String> getBlockedLabels() {
            return blockedLabels;
        }

        public boolean isBlocked() {
            return !blocked.isEmpty();
        }
    }

    /**
     * Types of deleted objects that can be listed and restored.
     */
    List<Class<?>> getTypes() throws Exception;

    long count(TrashFilter filter) throws Exception;

    /**
     * Page of deleted objects, the most recently deleted first.
     */
    List<TrashItem> find(TrashFilter filter, int offset, int limit) throws Exception;

    /**
     * Extended content of the deleted object as formatted json, null if it has none.
     *
     * @throws SecurityException when the object belongs to someone else (and the caller is not an administrator)
     */
    String getExtendedContent(EntityKey key) throws Exception;

    /**
     * Restores the objects, or nothing when some of them depend on a deleted object that is not among them
     * ({@link RestoreResult#getBlocked()}). Objects that are no longer deleted are skipped. The deleted objects the
     * restored ones own are restored with them (messages of a chat...), as the soft
     * delete deleted them together.
     *
     * @throws SecurityException when any object belongs to someone else (and the caller is not an administrator)
     */
    RestoreResult restore(Collection<EntityKey> keys) throws Exception;

    /**
     * Types of deleted objects that {@link #deletePermanently} removes: only the small ones (chat messages), so a user
     * can clean up the trash without removing a whole chat.
     */
    List<Class<?>> getPermanentlyDeletableTypes();

    /**
     * Removes the deleted objects from the database for good, without waiting for the cleanup. Only objects of
     * {@link #getPermanentlyDeletableTypes()} are accepted.
     * Objects that are no longer deleted are skipped. Nothing is removed when anything is blocked
     * ({@link DeleteResult#getBlocked()}), e.g. by an extension that still needs the object.
     *
     * @throws IllegalArgumentException when an object is of another type
     * @throws SecurityException        when any object belongs to someone else (and the caller is not an administrator)
     */
    DeleteResult deletePermanently(Collection<EntityKey> keys) throws Exception;
}
