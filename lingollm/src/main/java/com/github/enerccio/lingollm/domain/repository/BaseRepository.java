package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.BaseEntity;

import java.util.List;

public interface BaseRepository<T extends BaseEntity> {

    T find(Long id) throws Exception;
    Long find(String uuid) throws Exception;
    List<T> findAll() throws Exception;
    List<Long> findAllIds() throws Exception;
    T save(T t) throws Exception;
    T saveWithoutEvent(T t) throws Exception;
    T delete(T entity, boolean hard) throws Exception;
    void evict(T entity) throws Exception;

}
