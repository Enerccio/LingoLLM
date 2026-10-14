package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.BaseEntity;
import com.github.enerccio.lingollm.domain.repository.BaseRepository;

import java.util.List;

public interface BaseService<T extends BaseEntity, R extends BaseRepository<T>> {

    T find(Long id) throws Exception;
    T find(T t) throws Exception;
    Long find(String uuid) throws Exception;
    List<T> findAll() throws Exception;
    List<Long> findAllIds() throws Exception;
    T save(T t) throws Exception;
    T saveWithoutEvent(T t) throws Exception;
    T delete(T entity, boolean hard) throws Exception;
    void evict(T entity) throws Exception;

}
