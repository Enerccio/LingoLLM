package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.OwnedEntity;
import com.github.enerccio.lingollm.domain.security.model.User;

import java.util.List;

public interface OwnedRepository<T extends OwnedEntity> extends BaseRepository<T> {

    List<T> findAll(User user) throws Exception;
    List<Long> findAllIds(User user) throws Exception;
    T findByUuid(String uuid, User user) throws Exception;

}
