package com.github.enerccio.lingollm.domain.security;

import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * Server side check that the current user is an administrator, for services that only the administration screens use.
 * <p>
 * The UI already hides these screens from other users, this keeps the services safe if they are ever reachable
 * another way (an API, an extension). The current user is loaded from the database - the session copy of the user is
 * not trusted for {@code admin} - so a user demoted or deleted during a session loses access immediately.
 * <p>
 * Needs the current user, so it can't guard code that runs without a request (the backup schedule, extension
 * loading on start); such methods are not guarded.
 */
public class AdminGuard {

    @Autowired
    @Lazy
    private UserService userService;

    @Autowired
    private User currentUser;

    /**
     * @throws SecurityException when nobody is logged in, or the user is deleted or not an administrator
     */
    public void requireAdmin() throws Exception {
        if (!isAdmin()) {
            throw new SecurityException("Only administrators can do this");
        }
    }

    /**
     * For services that behave differently for administrators, the same check as {@link #requireAdmin()}.
     */
    public boolean isAdmin() throws Exception {
        User user = currentUser.getId() == null ? null : userService.find(currentUser.getId());
        return user != null && !user.isDeleted() && user.isAdmin();
    }
}
