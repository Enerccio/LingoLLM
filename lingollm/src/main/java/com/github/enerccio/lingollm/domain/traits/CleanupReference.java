package com.github.enerccio.lingollm.domain.traits;

import com.github.enerccio.lingollm.domain.model.BaseEntity;

import java.lang.annotation.*;

/**
 * Describes how a reference between entities behaves during cleanup of soft deleted objects.
 * <p>
 * JPA associations (ManyToOne, OneToOne, OneToMany, ManyToMany) are discovered from hibernate metamodel automatically
 * and default to {@link Policy#STRONG}, annotation is only needed to change the policy.
 * <p>
 * Non association fields holding ids of other entities (soft references) must be annotated with either
 * {@link #target()} or {@link #targetClassField()} to be discovered. Soft reference can be either persistent column
 * or {@link ExtendedAttribute}.
 * <p>
 * When placed on entity class, {@link #field()} names inherited field (ie from mapped superclass) the policy applies to.
 */
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(CleanupReferences.class)
public @interface CleanupReference {

    Policy value() default Policy.STRONG;

    /**
     * Only for type level use, name of the inherited field.
     */
    String field() default "";

    /**
     * Soft reference target entity type.
     */
    Class<? extends BaseEntity> target() default BaseEntity.class;

    /**
     * Soft polymorphic reference, name of the field holding target class name.
     */
    String targetClassField() default "";

    enum Policy {
        /**
         * Referenced object can't be purged while the reference exists.
         */
        STRONG,
        /**
         * Referencing object belongs to referenced object and is purged together with it.
         */
        OWNED_BY,
        /**
         * Referenced object belongs to referencing object and is purged together with it, unless referenced elsewhere.
         */
        OWNS,
        /**
         * Reference does not prevent purge, it is cleared when referenced object is purged.
         */
        WEAK,
    }
}
