package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.OwnedEntity;
import com.github.enerccio.lingollm.domain.repository.OwnedRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.List;

public interface OwnedService<T extends OwnedEntity, R extends OwnedRepository<T>> extends BaseService<T, R> {

    List<T> findAll(User user) throws Exception;
    List<Long> findAllIds(User user) throws Exception;
    List<T> findAllForUser() throws Exception;
    List<Long> findAllIdsForUser() throws Exception;

    /**
     * Finds entity by uuid owned by current user. Entities of other users and deleted entities are treated as
     * not found and null is returned.
     */
    T findForUser(String uuid) throws Exception;

}
