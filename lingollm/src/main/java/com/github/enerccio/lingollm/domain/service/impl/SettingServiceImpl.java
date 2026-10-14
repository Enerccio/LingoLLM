package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.repository.SettingRepository;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.service.SettingService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;

public class SettingServiceImpl extends ExtendableServiceImpl<Setting, SettingRepository> implements SettingService {

    @Override
    @CommonTx
    public <T extends Setting> T getOrCreate(Class<T> clazz) throws Exception {
        return getOrCreate(clazz, currentUser);
    }

    @Override
    @CommonTx
    public <T extends Setting> T getOrCreateApp(Class<T> clazz) throws Exception {
        return getOrCreate(clazz, null);
    }

    @SuppressWarnings("unchecked")
    @Override
    @CommonTx
    public synchronized <T extends Setting> T getOrCreate(Class<T> clazz, User user) throws Exception {
        T setting = getRepository().findSetting(clazz, user);
        if (setting == null) {
            setting = clazz.getConstructor().newInstance();
            setting.setKey(clazz.getSimpleName());
            if (user != null)
                setting.setOwner(userService.find(user.getId()));
            setting = (T) getRepository().save(setting);
        }
        return setting;
    }
}
