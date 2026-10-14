package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.collections.LimitStrategy;
import com.github.enerccio.lingollm.domain.components.AILimit;
import com.github.enerccio.lingollm.domain.components.AILimits;
import com.github.enerccio.lingollm.domain.components.LimitStatus;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.Usage;
import com.github.enerccio.lingollm.domain.security.AdminGuard;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.security.service.UserService;
import com.github.enerccio.lingollm.domain.service.AILimitService;
import com.github.enerccio.lingollm.domain.service.AIService;
import com.github.enerccio.lingollm.domain.service.InferenceException;
import com.github.enerccio.lingollm.domain.service.InferenceException.Type;
import com.github.enerccio.lingollm.domain.service.UsageService;
import com.github.enerccio.lingollm.domain.traits.CommonTx;
import com.github.enerccio.lingollm.domain.traits.CommonTxReadOnly;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class AILimitServiceImpl implements AILimitService {

    @Autowired
    private AIService aiService;

    @Autowired
    private UsageService usageService;

    @Autowired
    private UserService userService;

    @Autowired
    private AdminGuard adminGuard;

    @Autowired
    private User currentUser;

    @Override
    @CommonTxReadOnly
    public List<Entry> findLimits(AI ai) throws Exception {
        AI stored = loadForAdmin(ai);
        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<String, AILimit> e : AILimits.read(stored).entrySet()) {
            Long userId = userService.find(e.getKey());
            User user = userId == null ? null : userService.find(userId);
            if (user != null && !user.isDeleted()) {
                entries.add(new Entry(user, e.getValue()));
            }
        }
        return entries;
    }

    @Override
    @CommonTx
    public void setLimit(AI ai, User user, AILimit limit) throws Exception {
        AI stored = loadForAdmin(ai);
        if (!stored.isShared()) {
            throw new IllegalArgumentException("Limits are for shared AIs");
        }
        if (limit.validate() != null) {
            throw new IllegalArgumentException("Invalid limit: " + limit.validate());
        }
        Map<String, AILimit> limits = AILimits.read(stored);
        limits.put(user.getUuid(), limit);
        AILimits.write(stored, limits);
        aiService.save(stored);
    }

    @Override
    @CommonTx
    public void removeLimit(AI ai, User user) throws Exception {
        AI stored = loadForAdmin(ai);
        Map<String, AILimit> limits = AILimits.read(stored);
        if (limits.remove(user.getUuid()) != null) {
            AILimits.write(stored, limits);
            aiService.save(stored);
        }
    }

    @Override
    @CommonTxReadOnly
    public AILimit getLimit(AI ai) throws Exception {
        return AILimits.of(findAi(ai), currentUserUuid());
    }

    @Override
    @CommonTxReadOnly
    public LimitStatus getStatus(AI ai) throws Exception {
        AI stored = findAi(ai);
        AILimit limit = AILimits.of(stored, currentUserUuid());
        if (limit.getStrategy() != LimitStrategy.TOKEN_LIMIT) {
            return new LimitStatus(limit, 0, 0, null);
        }
        Date now = new Date();
        Date periodStart = limit.getResetStrategy().periodStart(now);
        Date resetsAt = limit.getResetStrategy().nextReset(now);
        Usage usage = usageService.findForUser(stored);
        // not used in this period: the counters are from an earlier one
        if (usage == null || usage.getLastUsed() == null || usage.getLastUsed().before(periodStart)) {
            return new LimitStatus(limit, 0, 0, resetsAt);
        }
        return new LimitStatus(limit, usage.getPeriodPrefillTokens(), usage.getPeriodGeneratedTokens(), resetsAt);
    }

    @Override
    @CommonTxReadOnly
    public void check(AI ai, long promptTokens, long reservedTokens) throws Exception {
        LimitStatus.Block block = getStatus(ai).block(promptTokens, reservedTokens);
        if (block == null) {
            return;
        }
        throw new InferenceException(block == LimitStatus.Block.NOT_AVAILABLE ? Type.NOT_AVAILABLE : Type.LIMIT_REACHED,
                null, null, null);
    }

    private AI findAi(AI ai) throws Exception {
        return ai == null || ai.getId() == null ? null : aiService.find(ai.getId());
    }

    /**
     * The uuid is not in the session's copy of the user.
     */
    private String currentUserUuid() throws Exception {
        User user = currentUser.getId() == null ? null : userService.find(currentUser.getId());
        return user == null ? null : user.getUuid();
    }

    private AI loadForAdmin(AI ai) throws Exception {
        adminGuard.requireAdmin();
        AI stored = findAi(ai);
        if (stored == null) {
            throw new IllegalArgumentException("Unknown AI");
        }
        if (!aiService.isOwnedByCurrentUser(stored)) {
            throw new SecurityException("Only the owner can change an AI");
        }
        return stored;
    }
}
