package com.github.enerccio.lingollm.domain.repository;

import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.security.model.User;

public interface SettingRepository extends ExtendableRepository<Setting> {

    <T extends Setting> T findAppSetting(Class<T> clazz) throws Exception;
    <T extends Setting> T findSetting(Class<T> clazz, User user) throws Exception;
    Long findAppSettingId(Class<? extends Setting> clazz) throws Exception;
    Long findSettingId(Class<? extends Setting> clazz, User user) throws Exception;

}
