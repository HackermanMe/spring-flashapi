package io.flashapi.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

/**
 * Contract for custom CRUD services. Implement this interface and annotate your class
 * with @FlashService(Entity.class) to take control of CRUD operations for a specific entity.
 *
 * @param <T>  the entity type
 * @param <ID> the primary key type
 */
public interface FlashCrudOperations<T, ID> {

    Page<T> list(Pageable pageable, Map<String, String> filters);

    Optional<T> findById(ID id);

    T create(Map<String, Object> data);

    Optional<T> update(ID id, Map<String, Object> data);

    boolean delete(ID id);

    default boolean restore(ID id) {
        return false;
    }
}
