package com.github.enerccio.lingollm.domain.traits;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field of an {@code ExtendableEntity} whose text goes into the {@code _fulltext} column. The column is
 * rebuilt by {@code ExtendableEntityListener.serialize} on every save from all marked fields (their string value, one
 * per line, in declaration order). The field can be a column or an {@link ExtendedAttribute}. Adding the annotation
 * to an existing field needs a data migration that fills {@code _fulltext} of the saved rows (see
 * {@code FulltextMigration}).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Fulltextable {

}
