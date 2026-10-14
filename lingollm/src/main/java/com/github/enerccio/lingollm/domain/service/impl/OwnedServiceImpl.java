package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.OwnedEntity;
import com.github.enerccio.lingollm.domain.repository.OwnedRepository;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.service.OwnedService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class OwnedServiceImpl<T extends OwnedEntity, R extends OwnedRepository<T>> extends BaseServiceImpl<T, R> implements OwnedService<T, R> {

    @Autowired
    protected UserService userService;

    @Autowired
    protected User currentUser;

    @Override
    @CommonTxReadOnly
    public List<T> findAll(User user) throws Exception {
        return getRepository().findAll(user);
    }

    @Override
    @CommonTxReadOnly
    public List<Long> findAllIds(User user) throws Exception {
        return getRepository().findAllIds(user);
    }

    @Override
    @CommonTxReadOnly
    public List<T> findAllForUser() throws Exception {
        return findAll(currentUser);
    }

    @Override
    @CommonTxReadOnly
    public List<Long> findAllIdsForUser() throws Exception {
        return findAllIds(currentUser);
    }

    @Override
    @CommonTxReadOnly
    public T findForUser(String uuid) throws Exception {
        if (StringUtils.isBlank(uuid)) {
            return null;
        }
        return getRepository().findByUuid(uuid, currentUser);
    }

    @Override
    public T save(T t) throws Exception {
        if (t.getOwner() == null && t.getId() == null) {
            t.setOwner(userService.find(currentUser.getId()));
        }
        return super.save(t);
    }

    @Override
    @CommonTx
    public T saveWithoutEvent(T t) throws Exception {
        if (t.getOwner() == null && t.getId() == null) {
            t.setOwner(userService.find(currentUser.getId()));
        }
        return super.saveWithoutEvent(t);
    }

    protected T saveWithoutOwner(T t) throws Exception {
        return super.save(t);
    }
}
