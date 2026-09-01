package io.github.hackermanme.flashapi.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an integer field as a declarative counter that auto-increments/decrements
 * when a related entity is created or deleted.
 *
 * The counter field is automatically read-only (stripped from request body).
 * Updates use atomic SQL (UPDATE ... SET field = field + 1).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FlashCounter {

    /**
     * The source entity class whose creation/deletion drives the counter.
     */
    Class<?> source();

    /**
     * The @ManyToOne field name on the source entity that points back to this entity.
     * Example: if Post has @FlashCounter(source=PostLike.class, relation="post"),
     * then PostLike must have a @ManyToOne field named "post" pointing to Post.
     */
    String relation();
}
