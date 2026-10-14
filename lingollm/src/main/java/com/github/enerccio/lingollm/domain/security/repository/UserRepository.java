package com.github.enerccio.lingollm.domain.security.repository;

import com.github.enerccio.lingollm.domain.repository.BaseRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

public interface UserRepository extends BaseRepository<User> {

    Long findByName(String name) throws Exception;

    boolean existsUsers() throws Exception;

    long countAdmins() throws Exception;

}
