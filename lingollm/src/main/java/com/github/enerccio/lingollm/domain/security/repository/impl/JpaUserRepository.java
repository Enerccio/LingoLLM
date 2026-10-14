package com.github.enerccio.lingollm.domain.security.repository.impl;

import com.github.enerccio.lingollm.domain.repository.impl.JpaBaseRepository;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.repository.UserRepository;

import java.util.List;

public class JpaUserRepository extends JpaBaseRepository<User> implements UserRepository {

    @Override
    protected Class<User> getEntityClass() {
        return User.class;
    }

    @Override
    public Long findByName(String name) throws Exception {
        List<Long> results = getEntityManager().createQuery(
                "SELECT u.id FROM User u WHERE u.login = :name AND u.deleted = false",
                Long.class
        ).setParameter("name", name).setMaxResults(1).getResultList();
        if (results.isEmpty()) {
            return null;
        }
        return results.getFirst();
    }

    @Override
    public long countAdmins() throws Exception {
        return getEntityManager().createQuery("SELECT count(u) FROM User u WHERE u.deleted = false AND u.isAdmin = true", Long.class)
                .getSingleResult();
    }

    @Override
    public boolean existsUsers() throws Exception {
        return getEntityManager().createQuery("SELECT count(u) FROM User u WHERE u.deleted = false", Long.class)
                .setMaxResults(1)
                .getSingleResult() > 0L;
    }

}
