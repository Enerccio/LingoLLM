package com.github.enerccio.lingollm.domain.security.service;

import com.github.enerccio.lingollm.domain.security.PersistedLoginInfo;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.repository.UserRepository;
import com.github.enerccio.lingollm.domain.service.BaseService;

import java.util.List;

public interface UserService extends BaseService<User, UserRepository> {

    boolean existsUsers() throws Exception;
    User findByName(String name) throws Exception;
    boolean isLoginAvailable(User user, String login) throws Exception;
    boolean isLastAdmin(User user) throws Exception;
    void deleteUser(User user) throws Exception;
    /** Same as {@link #authenticate(String, String, String)} without per-address throttling. */
    boolean authenticate(String username, String password) throws Exception;

    /**
     * @param clientAddress address the attempt came from, used for in-memory throttling; null or blank to skip it
     */
    boolean authenticate(String username, String password, String clientAddress) throws Exception;
    /**
     * Whether the user may be logged in without asking for anything (the {@code lingollm-autologin} start argument): they
     * exist, are not deleted or locked and have no password. A user with a password is never logged in this way.
     */
    boolean canAutologin(String username) throws Exception;
    User changePassword(User user, String password) throws Exception;
    User clearPassword(User user) throws Exception;
    User unlock(User user) throws Exception;
    PersistedLoginInfo authenticateFromCookie(User user, String cookieIdentifier, String cookieSecret) throws Exception;

    void deletePersistedLoginInfo(User user, PersistedLoginInfo persistedLoginInfo) throws Exception;
    List<PersistedLoginInfo> getPersistedLoginInfo(User user) throws Exception;
    PersistedLoginInfo generateNewPersistentInfo(User user) throws Exception;
    void addPersistedLoginInfo(User user, PersistedLoginInfo persistedLoginInfo) throws Exception;
}
