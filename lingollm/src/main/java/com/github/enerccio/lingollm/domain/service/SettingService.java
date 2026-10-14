package com.github.enerccio.lingollm.domain.service;

import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.repository.AIRepository;
import com.github.enerccio.lingollm.domain.repository.SettingRepository;
import com.github.enerccio.lingollm.domain.security.model.User;

public interface SettingService extends ExtendableService<Setting, SettingRepository> {

    <T extends Setting> T getOrCreate(Class<T> clazz) throws Exception;
    <T extends Setting> T getOrCreateApp(Class<T> clazz) throws Exception;
    <T extends Setting> T getOrCreate(Class<T> clazz, User user) throws Exception;

}
