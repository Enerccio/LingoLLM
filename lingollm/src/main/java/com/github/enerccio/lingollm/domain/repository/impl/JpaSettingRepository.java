package com.github.enerccio.lingollm.domain.repository.impl;

import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.repository.AIRepository;
import com.github.enerccio.lingollm.domain.repository.SettingRepository;
import com.github.enerccio.lingollm.domain.security.model.User;
import jakarta.persistence.TypedQuery;

import java.util.List;

public class JpaSettingRepository extends JpaExtendableRepository<Setting> implements SettingRepository {

    @Override
    protected Class<Setting> getEntityClass() {
        return Setting.class;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Setting> T findAppSetting(Class<T> clazz) throws Exception {
        return (T) hydrate(find(findAppSettingId(clazz)));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Setting> T findSetting(Class<T> clazz, User user) throws Exception {
        return (T) hydrate(find(findSettingId(clazz, user)));
    }

    @Override
    public Long findAppSettingId(Class<? extends Setting> clazz) throws Exception {
        return findSettingId(clazz, null);
    }

    @Override
    public Long findSettingId(Class<? extends Setting> clazz, User user) throws Exception {
        TypedQuery<Long> query = getEntityManager().createQuery("SELECT s.id FROM Setting s WHERE s.key = :key AND " + (user == null ? "s.owner IS NULL" : "s.owner.id = :owner"), Long.class)
                .setParameter("key", clazz.getSimpleName())
                .setMaxResults(1);
        if (user != null) {
            query.setParameter("owner", user.getId());
        }
        List<Long> ids = query.getResultList();
        return ids.isEmpty() ? null : ids.getFirst();
    }

}
