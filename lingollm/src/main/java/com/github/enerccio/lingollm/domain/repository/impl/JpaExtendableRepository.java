package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.listener.ExtendableEntityListener;
import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.repository.ExtendableRepository;

import java.util.Objects;

public abstract class JpaExtendableRepository<T extends ExtendableEntity> extends JpaOwnedRepository<T> implements ExtendableRepository<T> {

    protected final ExtendableEntityListener listener = new ExtendableEntityListener();

    @Override
    public T save(T entity) throws Exception {
        listener.serialize(entity);
        entity = super.save(entity);
        listener.deserialize(entity);
        return entity;
    }

    @Override
    public T saveWithoutEvent(T entity) throws Exception {
        entity = super.saveWithoutEvent(entity);
        listener.deserialize(entity);
        // extendedContent was set directly, so the fields were empty when it was written: _fulltext follows the
        // fields that were just read from it
        String fulltext = entity.get_fulltext();
        listener.updateFulltext(entity);
        if (!Objects.equals(fulltext, entity.get_fulltext())) {
            getEntityManager().flush();
        }
        return entity;
    }
}
