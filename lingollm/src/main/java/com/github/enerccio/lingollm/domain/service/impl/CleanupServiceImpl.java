package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.listener.ExtendableEntityListener;
import com.github.enerccio.lingollm.domain.model.BaseEntity;
import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.model.OwnedEntity;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.security.AdminGuard;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.CleanupService;
import com.github.enerccio.lingollm.domain.service.TrashService;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import com.github.enerccio.lingollm.domain.traits.CleanupReference.Policy;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import jakarta.persistence.*;
import jakarta.persistence.metamodel.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Cleanup of soft deleted entities and, on the same reference model, listing and restoring them ({@link TrashService}).
 * Cleanup is for administrators only, the trash is for every user (own objects) and administrators (all objects).
 */
public class CleanupServiceImpl implements CleanupService, TrashService {

    private static final Logger log = LoggerFactory.getLogger(CleanupServiceImpl.class);

    private static final int CHUNK_SIZE = 500;
    private static final List<Class<?>> PERMANENTLY_DELETABLE = List.of(FreeChatMessage.class);
    // what goes with a permanently deleted object
    private static final Set<Class<?>> OWNED_ON_DELETE = Set.of();
    private static final int REMOVE_BATCH_SIZE = 200;
    private static final int MAX_REASONS = 5;
    private static final String[] LABEL_METHODS = {"getName", "getLogin", "getValue"};

    private final ExtendableEntityListener extendableEntityListener = new ExtendableEntityListener();
    private final List<CleanupContributor> contributors = new CopyOnWriteArrayList<>();

    @Autowired
    private AdminGuard adminGuard;

    @Autowired
    private User currentUser;

    private EntityManager entityManager;
    private volatile Model model;

    @Override
    @CommonTxReadOnly
    public List<ReferenceDescriptor> getReferenceModel() throws Exception {
        adminGuard.requireAdmin();
        return Collections.unmodifiableList(getModel().references);
    }

    @Override
    @CommonTxReadOnly
    public CleanupPlan analyze() throws Exception {
        adminGuard.requireAdmin();
        CleanupPlan plan = computePlan(getModel());
        for (BlockedEntity blocked : plan.getBlocked().values()) {
            blocked.setLabel(label(blocked.getKey()));
        }
        return plan;
    }

    @Override
    @CommonTx
    public CleanupPlan purge() throws Exception {
        adminGuard.requireAdmin();
        Model m = getModel();
        CleanupPlan plan = computePlan(m);
        Set<EntityKey> purge = Collections.unmodifiableSet(plan.getPurge());
        if (!purge.isEmpty()) {
            executePurge(m, purge);
        }
        plan.setExecuted(true);
        log.info("Cleanup purged {} entities, {} soft deleted entities blocked", purge.size(), plan.getBlocked().size());
        return plan;
    }

    private void executePurge(Model m, Set<EntityKey> purge) throws Exception {
        clearWeakReferences(m, purge);
        for (CleanupContributor contributor : contributors) {
            contributor.beforePurge(purge);
        }
        entityManager.flush();
        entityManager.clear();
        detachReferences(m, purge);
        remove(purge);
    }

    @Override
    public void registerContributor(CleanupContributor contributor) {
        contributors.add(contributor);
    }

    @Override
    public void unregisterContributor(CleanupContributor contributor) {
        contributors.remove(contributor);
    }

    // ---------------------------------------------------------------------------------------------------------------
    // reference model
    // ---------------------------------------------------------------------------------------------------------------

    private static class Model {
        private final List<ReferenceDescriptor> references = new ArrayList<>();
        private final Map<Class<?>, String> rootEntities = new LinkedHashMap<>();
        private final Map<String, Class<?>> classNameToRoot = new HashMap<>();
    }

    private Model getModel() {
        if (model == null) {
            synchronized (this) {
                if (model == null) {
                    model = buildModel();
                }
            }
        }
        return model;
    }

    private Model buildModel() {
        Model m = new Model();
        Metamodel metamodel = entityManager.getMetamodel();

        for (EntityType<?> entityType : metamodel.getEntities()) {
            EntityType<?> root = rootOf(entityType);
            m.classNameToRoot.put(entityType.getJavaType().getName(), root.getJavaType());
            if (root == entityType && BaseEntity.class.isAssignableFrom(root.getJavaType())) {
                m.rootEntities.put(root.getJavaType(), root.getName());
            }
        }

        for (EntityType<?> entityType : metamodel.getEntities()) {
            boolean isRoot = rootOf(entityType) == entityType;
            Class<?> referrerType = rootOf(entityType).getJavaType();
            // root sees inherited mapped superclass attributes, subclasses only add their own
            Set<? extends Attribute<?, ?>> attributes = isRoot ? entityType.getAttributes() : entityType.getDeclaredAttributes();
            Map<String, Attribute<?, ?>> attributesByName = new HashMap<>();

            for (Attribute<?, ?> attribute : attributes) {
                attributesByName.put(attribute.getName(), attribute);
                if (!attribute.isAssociation()) {
                    continue;
                }
                Field field = attribute.getJavaMember() instanceof Field f ? f : null;
                if (field != null && isInverseSide(field)) {
                    continue;
                }
                Class<?> targetJavaType = attribute instanceof PluralAttribute<?, ?, ?> plural
                        ? plural.getElementType().getJavaType()
                        : ((SingularAttribute<?, ?>) attribute).getType().getJavaType();
                Class<?> targetType = m.classNameToRoot.get(targetJavaType.getName());
                if (targetType == null) {
                    continue;
                }
                m.references.add(new ReferenceDescriptor(entityType.getName(), referrerType, attribute.getName(),
                        targetType, null, resolvePolicy(entityType.getJavaType(), attribute.getName(), field),
                        true, attribute.isCollection(), true));
            }

            for (Field field : getFields(entityType.getJavaType(), isRoot)) {
                CleanupReference annotation = field.getAnnotation(CleanupReference.class);
                Attribute<?, ?> attribute = attributesByName.get(field.getName());
                if (annotation == null || (attribute != null && attribute.isAssociation())) {
                    continue;
                }
                Class<?> targetType = annotation.target() != BaseEntity.class ? m.classNameToRoot.get(annotation.target().getName()) : null;
                String targetClassField = StringUtils.trimToNull(annotation.targetClassField());
                if (targetType == null && targetClassField == null) {
                    log.warn("Soft reference {}.{} has neither known target nor target class field, ignoring", entityType.getName(), field.getName());
                    continue;
                }
                m.references.add(new ReferenceDescriptor(entityType.getName(), referrerType, field.getName(),
                        targetType, targetClassField, annotation.value(), false, false, attribute != null));
            }
        }

        log.debug("Cleanup reference model: {}", m.references);
        return m;
    }

    private EntityType<?> rootOf(EntityType<?> entityType) {
        IdentifiableType<?> type = entityType;
        while (type.getSupertype() instanceof EntityType<?> superType) {
            type = superType;
        }
        return (EntityType<?>) type;
    }

    private boolean isInverseSide(Field field) {
        OneToMany oneToMany = field.getAnnotation(OneToMany.class);
        if (oneToMany != null && StringUtils.isNotBlank(oneToMany.mappedBy())) {
            return true;
        }
        OneToOne oneToOne = field.getAnnotation(OneToOne.class);
        if (oneToOne != null && StringUtils.isNotBlank(oneToOne.mappedBy())) {
            return true;
        }
        ManyToMany manyToMany = field.getAnnotation(ManyToMany.class);
        return manyToMany != null && StringUtils.isNotBlank(manyToMany.mappedBy());
    }

    private Policy resolvePolicy(Class<?> entityClass, String fieldName, Field field) {
        if (field != null && field.getAnnotation(CleanupReference.class) != null) {
            return field.getAnnotation(CleanupReference.class).value();
        }
        for (Class<?> cls = entityClass; cls != null; cls = cls.getSuperclass()) {
            for (CleanupReference annotation : cls.getAnnotationsByType(CleanupReference.class)) {
                if (fieldName.equals(annotation.field())) {
                    return annotation.value();
                }
            }
        }
        return Policy.STRONG;
    }

    private List<Field> getFields(Class<?> entityClass, boolean includeSuperclasses) {
        List<Field> fields = new ArrayList<>(Arrays.asList(entityClass.getDeclaredFields()));
        if (includeSuperclasses) {
            for (Class<?> cls = entityClass.getSuperclass(); cls != null && cls != Object.class; cls = cls.getSuperclass()) {
                fields.addAll(Arrays.asList(cls.getDeclaredFields()));
            }
        }
        return fields;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // planning
    // ---------------------------------------------------------------------------------------------------------------

    private record Edge(EntityKey referrer, EntityKey target, ReferenceDescriptor descriptor) {
    }

    private static class Closure {
        private final Set<EntityKey> members = new LinkedHashSet<>();
        // members that must be purged for their origins to be purged (reached only via OWNED_BY)
        private final Set<EntityKey> mandatory = new HashSet<>();
        private final Map<EntityKey, Set<EntityKey>> origins = new HashMap<>();
    }

    private CleanupPlan computePlan(Model m) throws Exception {
        Set<EntityKey> softDeleted = new LinkedHashSet<>();
        for (Map.Entry<Class<?>, String> root : m.rootEntities.entrySet()) {
            List<Long> ids = entityManager.createQuery("SELECT e.id FROM " + root.getValue() + " e WHERE e.deleted = true", Long.class)
                    .getResultList();
            for (Long id : ids) {
                softDeleted.add(new EntityKey(root.getKey(), id));
            }
        }

        Set<EntityKey> blockedRoots = new HashSet<>();
        // soft deleted objects owned by a blocked one (a soft delete takes the owned objects with it): they stay while
        // their owner stays, so the owner can still be restored whole
        Set<EntityKey> heldBack = new HashSet<>();
        Set<EntityKey> expanded = new HashSet<>();
        Set<EntityKey> excluded = new HashSet<>();
        Map<EntityKey, List<String>> reasons = new LinkedHashMap<>();
        Closure closure;

        while (true) {
            Set<EntityKey> roots = new LinkedHashSet<>(softDeleted);
            roots.removeAll(blockedRoots);
            roots.removeAll(heldBack);
            closure = closure(m, roots, excluded);

            boolean changed = false;
            for (Map.Entry<EntityKey, List<String>> blocked : findBlocked(m, closure.members).entrySet()) {
                EntityKey node = blocked.getKey();
                if (closure.mandatory.contains(node)) {
                    for (EntityKey root : closure.origins.getOrDefault(node, Set.of(node))) {
                        changed |= blockedRoots.add(root);
                        List<String> rootReasons = reasons.computeIfAbsent(root, k -> new ArrayList<>());
                        for (String reason : blocked.getValue()) {
                            if (rootReasons.size() < MAX_REASONS) {
                                rootReasons.add(node.equals(root) ? reason : node + ": " + reason);
                            }
                        }
                    }
                } else {
                    changed |= excluded.add(node);
                }
            }
            changed |= holdBackOwned(m, softDeleted, blockedRoots, heldBack, expanded);
            if (!changed) {
                break;
            }
        }

        CleanupPlan plan = new CleanupPlan();
        plan.getPurge().addAll(closure.members);
        for (EntityKey key : softDeleted) {
            plan.getStats().computeIfAbsent(key.type(), EntityStats::new).setSoftDeleted(
                    plan.getStats().get(key.type()).getSoftDeleted() + 1);
        }
        for (EntityKey key : closure.members) {
            EntityStats stats = plan.getStats().computeIfAbsent(key.type(), EntityStats::new);
            stats.setPurgeable(stats.getPurgeable() + 1);
        }
        for (EntityKey key : blockedRoots) {
            EntityStats stats = plan.getStats().computeIfAbsent(key.type(), EntityStats::new);
            stats.setBlocked(stats.getBlocked() + 1);
            BlockedEntity blockedEntity = new BlockedEntity(key);
            blockedEntity.getReasons().addAll(reasons.getOrDefault(key, List.of()));
            plan.getBlocked().put(key, blockedEntity);
        }
        return plan;
    }

    /**
     * Adds to {@code heldBack} the soft deleted objects owned (OWNED_BY, directly or through other held objects) by a
     * blocked root.
     *
     * @param expanded objects already searched for owned objects
     * @return whether anything was added
     */
    private boolean holdBackOwned(Model m, Set<EntityKey> softDeleted, Set<EntityKey> blockedRoots, Set<EntityKey> heldBack,
                                  Set<EntityKey> expanded) throws Exception {
        Set<EntityKey> frontier = new LinkedHashSet<>(blockedRoots);
        frontier.addAll(heldBack);
        frontier.removeAll(expanded);
        boolean changed = false;
        while (!frontier.isEmpty()) {
            expanded.addAll(frontier);
            Set<EntityKey> next = new LinkedHashSet<>();
            Map<Class<?>, List<Long>> grouped = group(frontier);
            for (ReferenceDescriptor descriptor : m.references) {
                if (descriptor.getPolicy() != Policy.OWNED_BY) {
                    continue;
                }
                for (Map.Entry<Class<?>, List<Long>> group : grouped.entrySet()) {
                    for (Edge edge : edgesByTarget(m, descriptor, group.getKey(), group.getValue())) {
                        EntityKey owned = edge.referrer();
                        if (softDeleted.contains(owned) && !blockedRoots.contains(owned) && heldBack.add(owned)) {
                            next.add(owned);
                            changed = true;
                        }
                    }
                }
            }
            next.removeAll(expanded);
            frontier = next;
        }
        return changed;
    }

    private Closure closure(Model m, Set<EntityKey> roots, Set<EntityKey> excluded) throws Exception {
        Closure closure = new Closure();
        for (EntityKey root : roots) {
            closure.members.add(root);
            closure.mandatory.add(root);
            closure.origins.put(root, new HashSet<>(Set.of(root)));
        }

        Set<EntityKey> frontier = new LinkedHashSet<>(roots);
        while (!frontier.isEmpty()) {
            Set<EntityKey> next = new LinkedHashSet<>();
            Map<Class<?>, List<Long>> grouped = group(frontier);
            for (ReferenceDescriptor descriptor : m.references) {
                if (descriptor.getPolicy() == Policy.OWNED_BY) {
                    for (Map.Entry<Class<?>, List<Long>> group : grouped.entrySet()) {
                        for (Edge edge : edgesByTarget(m, descriptor, group.getKey(), group.getValue())) {
                            propagate(closure, edge.referrer(), edge.target(), true, excluded, next);
                        }
                    }
                } else if (descriptor.getPolicy() == Policy.OWNS) {
                    List<Long> ids = grouped.get(descriptor.getReferrerType());
                    if (ids != null) {
                        for (Edge edge : edgesByReferrer(m, descriptor, ids)) {
                            propagate(closure, edge.target(), edge.referrer(), false, excluded, next);
                        }
                    }
                }
            }
            frontier = next;
        }
        return closure;
    }

    private void propagate(Closure closure, EntityKey child, EntityKey from, boolean ownedBy, Set<EntityKey> excluded, Set<EntityKey> next) {
        if (excluded.contains(child)) {
            return;
        }
        boolean changed = closure.members.add(child);
        if (ownedBy && closure.mandatory.contains(from)) {
            changed |= closure.mandatory.add(child);
            changed |= closure.origins.computeIfAbsent(child, k -> new HashSet<>()).addAll(closure.origins.get(from));
        }
        if (changed) {
            next.add(child);
        }
    }

    private Map<EntityKey, List<String>> findBlocked(Model m, Set<EntityKey> members) throws Exception {
        Map<EntityKey, List<String>> blocked = new LinkedHashMap<>();
        Map<Class<?>, List<Long>> grouped = group(members);
        for (ReferenceDescriptor descriptor : m.references) {
            if (descriptor.getPolicy() == Policy.WEAK) {
                continue;
            }
            for (Map.Entry<Class<?>, List<Long>> group : grouped.entrySet()) {
                for (Edge edge : edgesByTarget(m, descriptor, group.getKey(), group.getValue())) {
                    if (!members.contains(edge.referrer())) {
                        blocked.computeIfAbsent(edge.target(), k -> new ArrayList<>())
                                .add(edge.referrer() + " (" + descriptor.getField() + ")");
                    }
                }
            }
        }
        Set<EntityKey> candidates = Collections.unmodifiableSet(members);
        for (CleanupContributor contributor : contributors) {
            contributor.collectReferences(candidates, (target, reason) -> {
                if (members.contains(target)) {
                    blocked.computeIfAbsent(target, k -> new ArrayList<>()).add(reason);
                }
            });
        }
        return blocked;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // reference queries
    // ---------------------------------------------------------------------------------------------------------------

    private List<Edge> edgesByTarget(Model m, ReferenceDescriptor descriptor, Class<?> targetType, List<Long> ids) throws Exception {
        if (descriptor.getTargetType() != null && descriptor.getTargetType() != targetType) {
            return List.of();
        }
        List<Edge> edges = new ArrayList<>();
        if (!descriptor.isPersistent()) {
            Set<Long> idSet = new HashSet<>(ids);
            for (Object entity : entityManager.createQuery("SELECT e FROM " + descriptor.getReferrerEntity() + " e", Object.class).getResultList()) {
                EntityKey target = readSoftReference(m, descriptor, entity);
                if (target != null && target.type() == targetType && idSet.contains(target.id())) {
                    edges.add(new Edge(new EntityKey(descriptor.getReferrerType(), ((BaseEntity) entity).getId()), target, descriptor));
                }
            }
            return edges;
        }
        String targetExpression = targetExpression(descriptor);
        for (List<Long> chunk : chunks(ids)) {
            List<Object[]> rows = entityManager.createQuery(selectClause(descriptor) + " WHERE " + targetExpression + " IN :ids", Object[].class)
                    .setParameter("ids", chunk)
                    .getResultList();
            addEdges(m, descriptor, rows, edges, targetType);
        }
        return edges;
    }

    private List<Edge> edgesByReferrer(Model m, ReferenceDescriptor descriptor, List<Long> referrerIds) throws Exception {
        List<Edge> edges = new ArrayList<>();
        if (!descriptor.isPersistent()) {
            for (List<Long> chunk : chunks(referrerIds)) {
                for (Object entity : entityManager.createQuery("SELECT e FROM " + descriptor.getReferrerEntity() + " e WHERE e.id IN :ids", Object.class)
                        .setParameter("ids", chunk).getResultList()) {
                    EntityKey target = readSoftReference(m, descriptor, entity);
                    if (target != null) {
                        edges.add(new Edge(new EntityKey(descriptor.getReferrerType(), ((BaseEntity) entity).getId()), target, descriptor));
                    }
                }
            }
            return edges;
        }
        String targetExpression = targetExpression(descriptor);
        for (List<Long> chunk : chunks(referrerIds)) {
            List<Object[]> rows = entityManager.createQuery(selectClause(descriptor) + " WHERE e.id IN :ids AND " + targetExpression + " IS NOT NULL", Object[].class)
                    .setParameter("ids", chunk)
                    .getResultList();
            addEdges(m, descriptor, rows, edges, null);
        }
        return edges;
    }

    private String targetExpression(ReferenceDescriptor descriptor) {
        if (descriptor.isCollection()) {
            return "x.id";
        }
        return descriptor.isAssociation() ? "e." + descriptor.getField() + ".id" : "e." + descriptor.getField();
    }

    private String selectClause(ReferenceDescriptor descriptor) {
        String select = "SELECT e.id, " + targetExpression(descriptor);
        if (descriptor.getTargetClassField() != null) {
            select += ", e." + descriptor.getTargetClassField();
        }
        select += " FROM " + descriptor.getReferrerEntity() + " e";
        if (descriptor.isCollection()) {
            select += " JOIN e." + descriptor.getField() + " x";
        }
        return select;
    }

    private void addEdges(Model m, ReferenceDescriptor descriptor, List<Object[]> rows, List<Edge> edges, Class<?> targetTypeFilter) {
        for (Object[] row : rows) {
            if (!(row[1] instanceof Number targetId)) {
                continue;
            }
            Class<?> targetType = descriptor.getTargetType();
            if (descriptor.getTargetClassField() != null) {
                targetType = row[2] != null ? m.classNameToRoot.get(row[2].toString()) : null;
            }
            if (targetType == null || (targetTypeFilter != null && targetType != targetTypeFilter)) {
                continue;
            }
            edges.add(new Edge(new EntityKey(descriptor.getReferrerType(), ((Number) row[0]).longValue()),
                    new EntityKey(targetType, targetId.longValue()), descriptor));
        }
    }

    private EntityKey readSoftReference(Model m, ReferenceDescriptor descriptor, Object entity) throws Exception {
        Object value = findField(entity.getClass(), descriptor.getField()).get(entity);
        if (!(value instanceof Number id)) {
            return null;
        }
        Class<?> targetType = descriptor.getTargetType();
        if (descriptor.getTargetClassField() != null) {
            Object className = findField(entity.getClass(), descriptor.getTargetClassField()).get(entity);
            targetType = className != null ? m.classNameToRoot.get(className.toString()) : null;
        }
        return targetType != null ? new EntityKey(targetType, id.longValue()) : null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // purge
    // ---------------------------------------------------------------------------------------------------------------

    private void clearWeakReferences(Model m, Set<EntityKey> purge) throws Exception {
        Map<Class<?>, List<Long>> grouped = group(purge);
        for (ReferenceDescriptor descriptor : m.references) {
            if (descriptor.getPolicy() != Policy.WEAK) {
                continue;
            }
            Map<Long, Set<EntityKey>> targetsByReferrer = new LinkedHashMap<>();
            for (Map.Entry<Class<?>, List<Long>> group : grouped.entrySet()) {
                for (Edge edge : edgesByTarget(m, descriptor, group.getKey(), group.getValue())) {
                    if (!purge.contains(edge.referrer())) {
                        targetsByReferrer.computeIfAbsent(edge.referrer().id(), k -> new HashSet<>()).add(edge.target());
                    }
                }
            }
            if (targetsByReferrer.isEmpty()) {
                continue;
            }

            if (descriptor.isPersistent() && !descriptor.isCollection()) {
                String update = "UPDATE " + descriptor.getReferrerEntity() + " e SET e." + descriptor.getField() + " = NULL";
                if (descriptor.getTargetClassField() != null) {
                    update += ", e." + descriptor.getTargetClassField() + " = NULL";
                }
                for (List<Long> chunk : chunks(new ArrayList<>(targetsByReferrer.keySet()))) {
                    entityManager.createQuery(update + " WHERE e.id IN :ids").setParameter("ids", chunk).executeUpdate();
                }
                continue;
            }

            for (Map.Entry<Long, Set<EntityKey>> entry : targetsByReferrer.entrySet()) {
                Object referrer = entityManager.find(descriptor.getReferrerType(), entry.getKey());
                if (referrer == null) {
                    continue;
                }
                Field field = findField(referrer.getClass(), descriptor.getField());
                if (descriptor.isCollection()) {
                    Object value = field.get(referrer);
                    if (value instanceof Collection<?> collection) {
                        collection.removeIf(o -> o instanceof BaseEntity be
                                && entry.getValue().contains(new EntityKey(descriptor.getTargetType(), be.getId())));
                    }
                } else {
                    field.set(referrer, null);
                    if (descriptor.getTargetClassField() != null) {
                        findField(referrer.getClass(), descriptor.getTargetClassField()).set(referrer, null);
                    }
                    if (referrer instanceof ExtendableEntity extendable) {
                        extendableEntityListener.serialize(extendable);
                    }
                }
                if (referrer instanceof BaseEntity baseEntity) {
                    baseEntity.setModification(new Date());
                }
            }
            entityManager.flush();
        }
    }

    /**
     * Purged entities can reference each other (even in cycles), hibernate
     * refuses to remove entity still referenced by loaded one, so references are nulled first.
     */
    private void detachReferences(Model m, Set<EntityKey> purge) {
        Map<Class<?>, List<Long>> grouped = group(purge);
        for (ReferenceDescriptor descriptor : m.references) {
            List<Long> ids = grouped.get(descriptor.getReferrerType());
            if (ids == null || !descriptor.isAssociation() || descriptor.isCollection()) {
                continue;
            }
            for (List<Long> chunk : chunks(ids)) {
                entityManager.createQuery("UPDATE " + descriptor.getReferrerEntity() + " e SET e." + descriptor.getField() + " = NULL WHERE e.id IN :ids")
                        .setParameter("ids", chunk)
                        .executeUpdate();
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    private void remove(Set<EntityKey> purge) {
        for (Map.Entry<Class<?>, List<Long>> group : group(purge).entrySet()) {
            int count = 0;
            for (Long id : group.getValue()) {
                Object entity = entityManager.find(group.getKey(), id);
                if (entity != null) {
                    entityManager.remove(entity);
                }
                if (++count % REMOVE_BATCH_SIZE == 0) {
                    entityManager.flush();
                    entityManager.clear();
                }
            }
            entityManager.flush();
            entityManager.clear();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // trash (list and restore soft deleted entities)
    // ---------------------------------------------------------------------------------------------------------------

    private record TrashRow(EntityKey key, String uuid, Date modification, Long ownerId, String ownerLogin) {
    }

    @Override
    @CommonTxReadOnly
    public List<Class<?>> getTypes() throws Exception {
        trashScope();
        return trashTypes(getModel());
    }

    @Override
    @CommonTxReadOnly
    public long count(TrashFilter filter) throws Exception {
        Model m = getModel();
        Long owner = trashOwner(filter);
        long total = 0;
        for (Class<?> type : trashTypes(m, filter)) {
            TypedQuery<Long> query = entityManager.createQuery("SELECT COUNT(e) FROM " + m.rootEntities.get(type)
                    + " e WHERE e.deleted = true" + (owner != null ? " AND e.owner.id = :owner" : ""), Long.class);
            if (owner != null) {
                query.setParameter("owner", owner);
            }
            total += query.getSingleResult();
        }
        return total;
    }

    @Override
    @CommonTxReadOnly
    public List<TrashItem> find(TrashFilter filter, int offset, int limit) throws Exception {
        Model m = getModel();
        Long owner = trashOwner(filter);
        // narrow rows only (no extended content), the entities are loaded for the requested page
        List<TrashRow> rows = new ArrayList<>();
        for (Class<?> type : trashTypes(m, filter)) {
            TypedQuery<Object[]> query = entityManager.createQuery("SELECT e.id, e.uuid, e.modification, o.id, o.login FROM "
                    + m.rootEntities.get(type) + " e LEFT JOIN e.owner o WHERE e.deleted = true"
                    + (owner != null ? " AND o.id = :owner" : ""), Object[].class);
            if (owner != null) {
                query.setParameter("owner", owner);
            }
            for (Object[] row : query.getResultList()) {
                rows.add(new TrashRow(new EntityKey(type, ((Number) row[0]).longValue()), (String) row[1],
                        row[2] != null ? new Date(((Date) row[2]).getTime()) : null,
                        row[3] != null ? ((Number) row[3]).longValue() : null, (String) row[4]));
            }
        }
        rows.sort(Comparator.comparingLong((TrashRow row) -> row.modification() != null ? row.modification().getTime() : Long.MIN_VALUE)
                .reversed().thenComparing(row -> row.key().id()));

        int from = Math.min(Math.max(offset, 0), rows.size());
        int to = (int) Math.min(rows.size(), (long) from + Math.max(limit, 0));
        List<TrashItem> items = new ArrayList<>();
        for (TrashRow row : rows.subList(from, to)) {
            items.add(new TrashItem(row.key(), row.uuid(), label(row.key()), row.ownerId(), row.ownerLogin(),
                    row.modification(), ExtendableEntity.class.isAssignableFrom(row.key().type())));
        }
        return items;
    }

    @Override
    @CommonTxReadOnly
    public String getExtendedContent(EntityKey key) throws Exception {
        Model m = getModel();
        if (!ExtendableEntity.class.isAssignableFrom(key.type()) || !deletedAccessible(m, List.of(key)).contains(key)) {
            return null;
        }
        List<byte[]> content = entityManager.createQuery("SELECT e.extendedContent FROM " + m.rootEntities.get(key.type())
                        + " e WHERE e.id = :id", byte[].class)
                .setParameter("id", key.id())
                .getResultList();
        if (content.isEmpty() || content.getFirst() == null || content.getFirst().length == 0) {
            return null;
        }
        String json = new String(content.getFirst(), StandardCharsets.UTF_8);
        try {
            JsonElement parsed = JsonParser.parseString(json);
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(parsed);
        } catch (Exception e) {
            return json;
        }
    }

    @Override
    @CommonTx
    public RestoreResult restore(Collection<EntityKey> keys) throws Exception {
        Model m = getModel();
        Set<EntityKey> selection = deletedAccessible(m, keys);
        RestoreResult result = new RestoreResult();
        if (selection.isEmpty()) {
            return result;
        }
        // the soft delete took the owned objects with it, so the restore brings them back
        selection.addAll(deletedOwned(m, selection));

        Map<EntityKey, List<Blocker>> blocked = findDeletedParents(m, selection);
        if (!blocked.isEmpty()) {
            result.getBlocked().putAll(blocked);
            for (EntityKey key : blocked.keySet()) {
                result.getBlockedLabels().put(key, label(key));
            }
            return result;
        }

        // bulk update on purpose: no listeners, extended attributes of the entities stay as they are
        Date now = new Date();
        for (Map.Entry<Class<?>, List<Long>> group : group(selection).entrySet()) {
            for (List<Long> chunk : chunks(group.getValue())) {
                entityManager.createQuery("UPDATE " + m.rootEntities.get(group.getKey())
                                + " e SET e.deleted = false, e.modification = :now WHERE e.id IN :ids")
                        .setParameter("now", now)
                        .setParameter("ids", chunk)
                        .executeUpdate();
            }
        }
        entityManager.flush();
        entityManager.clear();
        result.getRestored().addAll(selection);
        log.info("Restored {} soft deleted entities", selection.size());
        return result;
    }

    @Override
    public List<Class<?>> getPermanentlyDeletableTypes() {
        return PERMANENTLY_DELETABLE;
    }

    @Override
    @CommonTx
    public DeleteResult deletePermanently(Collection<EntityKey> keys) throws Exception {
        Model m = getModel();
        for (EntityKey key : keys) {
            if (!PERMANENTLY_DELETABLE.contains(key.type())) {
                throw new IllegalArgumentException("Can't be deleted permanently: " + key.type().getName());
            }
        }
        Set<EntityKey> selection = deletedAccessible(m, keys);
        DeleteResult result = new DeleteResult();
        if (selection.isEmpty()) {
            return result;
        }


        moveChildrenOfMessages(selection);

        Closure closure = closure(m, selection, Set.of());
        Map<EntityKey, List<String>> reasons = new LinkedHashMap<>();
        Set<EntityKey> purge = new LinkedHashSet<>(closure.members);
        // what else would go with the selection: nothing is allowed to, a message owns no deleted object
        for (EntityKey member : closure.members) {
            if (!selection.contains(member) && !OWNED_ON_DELETE.contains(member.type())) {
                for (EntityKey root : closure.origins.getOrDefault(member, selection)) {
                    reasons.computeIfAbsent(root, k -> new ArrayList<>()).add(member + " belongs to it");
                }
            }
        }
        for (Map.Entry<EntityKey, List<String>> blocked : findBlocked(m, closure.members).entrySet()) {
            if (closure.mandatory.contains(blocked.getKey())) {
                for (EntityKey root : closure.origins.getOrDefault(blocked.getKey(), Set.of(blocked.getKey()))) {
                    List<String> rootReasons = reasons.computeIfAbsent(root, k -> new ArrayList<>());
                    for (String reason : blocked.getValue()) {
                        if (rootReasons.size() < MAX_REASONS) {
                            rootReasons.add(blocked.getKey().equals(root) ? reason : blocked.getKey() + ": " + reason);
                        }
                    }
                }
            } else {
                // an object that something else uses stays
                purge.remove(blocked.getKey());
            }
        }

        if (!reasons.isEmpty()) {
            // the parts were already moved: undo it
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            for (Map.Entry<EntityKey, List<String>> blocked : reasons.entrySet()) {
                if (selection.contains(blocked.getKey())) {
                    result.getBlocked().put(blocked.getKey(), blocked.getValue());
                    result.getBlockedLabels().put(blocked.getKey(), label(blocked.getKey()));
                }
            }
            return result;
        }

        executePurge(m, purge);
        result.getDeleted().addAll(purge);
        log.info("Permanently deleted {} entities from the trash", purge.size());
        return result;
    }

    /**
     * Moves the children of the messages to the nearest ancestor that is not among the messages (deleted children
     * too, a restored one finds its parent), so the chat stays connected after the messages are removed.
     */
    private void moveChildrenOfMessages(Set<EntityKey> selection) {
        List<Long> ids = selection.stream().filter(key -> key.type() == FreeChatMessage.class).map(EntityKey::id).toList();
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Long> parentOf = new HashMap<>();
        for (List<Long> chunk : chunks(ids)) {
            for (Object[] row : entityManager.createQuery("SELECT m.id, m.parent.id FROM FreeChatMessage m WHERE m.id IN :ids", Object[].class)
                    .setParameter("ids", chunk).getResultList()) {
                parentOf.put(((Number) row[0]).longValue(), row[1] != null ? ((Number) row[1]).longValue() : null);
            }
        }
        Set<Long> removed = new HashSet<>(ids);
        for (Long id : ids) {
            Long target = parentOf.get(id);
            while (target != null && removed.contains(target)) {
                target = parentOf.get(target);
            }
            FreeChatMessage newParent = target != null ? entityManager.getReference(FreeChatMessage.class, target) : null;
            entityManager.createQuery("UPDATE FreeChatMessage m SET m.parent = :parent WHERE m.parent.id = :id")
                    .setParameter("parent", newParent)
                    .setParameter("id", id)
                    .executeUpdate();
        }
        entityManager.flush();
        entityManager.clear();
    }

    /**
     * Owner the current user is limited to, null for administrator (all owners). Fails when nobody is logged in.
     */
    private Long trashScope() throws Exception {
        if (currentUser.getId() == null) {
            throw new SecurityException("Nobody is logged in");
        }
        return adminGuard.isAdmin() ? null : currentUser.getId();
    }

    private Long trashOwner(TrashFilter filter) throws Exception {
        Long scope = trashScope();
        return scope != null ? scope : filter != null ? filter.ownerId() : null;
    }

    private List<Class<?>> trashTypes(Model m) {
        List<Class<?>> types = new ArrayList<>();
        for (Class<?> type : m.rootEntities.keySet()) {
            if (OwnedEntity.class.isAssignableFrom(type)) {
                types.add(type);
            }
        }
        return types;
    }

    private List<Class<?>> trashTypes(Model m, TrashFilter filter) {
        List<Class<?>> types = trashTypes(m);
        if (filter == null || filter.type() == null) {
            return types;
        }
        if (!types.contains(filter.type())) {
            throw new IllegalArgumentException("Not a restorable type: " + filter.type().getName());
        }
        return List.of(filter.type());
    }

    /**
     * Requested objects that are still deleted. Objects that do not exist or were restored in the meantime are left
     * out.
     *
     * @throws SecurityException when the object belongs to someone else, whatever its state
     */
    private Set<EntityKey> deletedAccessible(Model m, Collection<EntityKey> keys) throws Exception {
        Long scope = trashScope();
        List<Class<?>> types = trashTypes(m);
        Set<EntityKey> deleted = new LinkedHashSet<>();
        for (Map.Entry<Class<?>, List<Long>> group : group(new LinkedHashSet<>(keys)).entrySet()) {
            if (!types.contains(group.getKey())) {
                throw new IllegalArgumentException("Not a restorable type: " + group.getKey().getName());
            }
            for (List<Long> chunk : chunks(group.getValue())) {
                List<Object[]> rows = entityManager.createQuery("SELECT e.id, o.id, e.deleted FROM " + m.rootEntities.get(group.getKey())
                                + " e LEFT JOIN e.owner o WHERE e.id IN :ids", Object[].class)
                        .setParameter("ids", chunk)
                        .getResultList();
                for (Object[] row : rows) {
                    Long ownerId = row[1] != null ? ((Number) row[1]).longValue() : null;
                    if (scope != null && !scope.equals(ownerId)) {
                        throw new SecurityException("The object belongs to another user");
                    }
                    if (Boolean.TRUE.equals(row[2])) {
                        deleted.add(new EntityKey(group.getKey(), ((Number) row[0]).longValue()));
                    }
                }
            }
        }
        return deleted;
    }

    /**
     * Deleted objects the selection owns, directly or through each other: the objects that are OWNED_BY a selected
     * object (messages of a chat) and the objects a selected one OWNS. Soft delete
     * of an object deletes these too.
     */
    private Set<EntityKey> deletedOwned(Model m, Set<EntityKey> selection) throws Exception {
        Set<EntityKey> owned = new LinkedHashSet<>();
        Set<EntityKey> frontier = new LinkedHashSet<>(selection);
        while (!frontier.isEmpty()) {
            Set<EntityKey> candidates = new LinkedHashSet<>();
            Map<Class<?>, List<Long>> grouped = group(frontier);
            for (ReferenceDescriptor descriptor : m.references) {
                if (descriptor.getPolicy() == Policy.OWNED_BY) {
                    for (Map.Entry<Class<?>, List<Long>> group : grouped.entrySet()) {
                        for (Edge edge : edgesByTarget(m, descriptor, group.getKey(), group.getValue())) {
                            candidates.add(edge.referrer());
                        }
                    }
                } else if (descriptor.getPolicy() == Policy.OWNS) {
                    List<Long> ids = grouped.get(descriptor.getReferrerType());
                    if (ids != null) {
                        for (Edge edge : edgesByReferrer(m, descriptor, ids)) {
                            candidates.add(edge.target());
                        }
                    }
                }
            }
            candidates.removeAll(selection);
            candidates.removeAll(owned);
            candidates = new LinkedHashSet<>(deletedAmong(m, candidates));
            owned.addAll(candidates);
            frontier = candidates;
        }
        return owned;
    }

    /**
     * Objects of the selection that depend on a deleted object outside of the selection. Dependency is everything the
     * object can't live without: the objects it is owned by or strongly references, and the object that owns it. Weak
     * references are cleared by purge anyway, so they are not a dependency.
     */
    private Map<EntityKey, List<Blocker>> findDeletedParents(Model m, Set<EntityKey> selection) throws Exception {
        Map<EntityKey, Map<EntityKey, String>> parentsOf = new LinkedHashMap<>();
        Map<Class<?>, List<Long>> grouped = group(selection);
        for (ReferenceDescriptor descriptor : m.references) {
            switch (descriptor.getPolicy()) {
                case STRONG, OWNED_BY -> {
                    List<Long> ids = grouped.get(descriptor.getReferrerType());
                    if (ids != null) {
                        for (Edge edge : edgesByReferrer(m, descriptor, ids)) {
                            parentsOf.computeIfAbsent(edge.referrer(), k -> new LinkedHashMap<>())
                                    .putIfAbsent(edge.target(), descriptor.getField());
                        }
                    }
                }
                case OWNS -> {
                    for (Map.Entry<Class<?>, List<Long>> group : grouped.entrySet()) {
                        for (Edge edge : edgesByTarget(m, descriptor, group.getKey(), group.getValue())) {
                            parentsOf.computeIfAbsent(edge.target(), k -> new LinkedHashMap<>())
                                    .putIfAbsent(edge.referrer(), descriptor.getField());
                        }
                    }
                }
                case WEAK -> {
                }
            }
        }

        Set<EntityKey> outside = new LinkedHashSet<>();
        for (Map<EntityKey, String> parents : parentsOf.values()) {
            for (EntityKey parent : parents.keySet()) {
                if (!selection.contains(parent)) {
                    outside.add(parent);
                }
            }
        }
        Set<EntityKey> deletedOutside = deletedAmong(m, outside);

        Map<EntityKey, List<Blocker>> blocked = new LinkedHashMap<>();
        Map<EntityKey, String> labels = new HashMap<>();
        for (Map.Entry<EntityKey, Map<EntityKey, String>> child : parentsOf.entrySet()) {
            for (Map.Entry<EntityKey, String> parent : child.getValue().entrySet()) {
                if (deletedOutside.contains(parent.getKey())) {
                    blocked.computeIfAbsent(child.getKey(), k -> new ArrayList<>()).add(new Blocker(parent.getKey(),
                            labels.computeIfAbsent(parent.getKey(), this::label), parent.getValue()));
                }
            }
        }
        return blocked;
    }

    private Set<EntityKey> deletedAmong(Model m, Set<EntityKey> keys) {
        Set<EntityKey> deleted = new HashSet<>();
        for (Map.Entry<Class<?>, List<Long>> group : group(keys).entrySet()) {
            String entityName = m.rootEntities.get(group.getKey());
            if (entityName == null) {
                continue;
            }
            for (List<Long> chunk : chunks(group.getValue())) {
                for (Long id : entityManager.createQuery("SELECT e.id FROM " + entityName + " e WHERE e.id IN :ids AND e.deleted = true", Long.class)
                        .setParameter("ids", chunk).getResultList()) {
                    deleted.add(new EntityKey(group.getKey(), id));
                }
            }
        }
        return deleted;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------------------------------------------

    private String label(EntityKey key) {
        try {
            Object entity = entityManager.find(key.type(), key.id());
            if (entity == null) {
                return key.toString();
            }
            for (String methodName : LABEL_METHODS) {
                try {
                    Method method = entity.getClass().getMethod(methodName);
                    Object value = method.invoke(entity);
                    if (value != null && StringUtils.isNotBlank(value.toString())) {
                        return value.toString();
                    }
                } catch (NoSuchMethodException ignored) {
                }
            }
            return entity instanceof BaseEntity baseEntity ? baseEntity.getUuid() : key.toString();
        } catch (Exception e) {
            log.debug("Failed to resolve label for {}", key, e);
            return key.toString();
        }
    }

    private Map<Class<?>, List<Long>> group(Collection<EntityKey> keys) {
        Map<Class<?>, List<Long>> grouped = new LinkedHashMap<>();
        for (EntityKey key : keys) {
            grouped.computeIfAbsent(key.type(), k -> new ArrayList<>()).add(key.id());
        }
        return grouped;
    }

    private List<List<Long>> chunks(List<Long> ids) {
        List<List<Long>> chunks = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += CHUNK_SIZE) {
            chunks.add(ids.subList(i, Math.min(ids.size(), i + CHUNK_SIZE)));
        }
        return chunks;
    }

    private Field findField(Class<?> cls, String name) throws NoSuchFieldException {
        for (Class<?> c = cls; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(cls.getName() + "." + name);
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}
