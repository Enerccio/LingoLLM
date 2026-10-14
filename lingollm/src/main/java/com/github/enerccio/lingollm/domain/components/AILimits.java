package com.github.enerccio.lingollm.domain.components;

import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The limits of an AI as stored in {@link AI#getLimits()}: a JSON object with the {@link AILimit} of each user under
 * the user's uuid. Users without an entry are not limited.
 */
public final class AILimits {

    private static final Gson gson = new Gson();

    private AILimits() {
    }

    /**
     * @return the limits by user uuid, in the stored order; never null
     */
    public static Map<String, AILimit> read(AI ai) {
        Map<String, AILimit> limits = new LinkedHashMap<>();
        if (ai == null || StringUtils.isBlank(ai.getLimits())) {
            return limits;
        }
        try {
            JsonObject root = gson.fromJson(ai.getLimits(), JsonObject.class);
            if (root != null) {
                root.entrySet().forEach(e -> limits.put(e.getKey(), gson.fromJson(e.getValue(), AILimit.class)));
            }
        } catch (JsonParseException e) {
            // a broken value limits nobody rather than everybody
        }
        return limits;
    }

    /**
     * Replaces the limits of the AI, not saving it.
     */
    public static void write(AI ai, Map<String, AILimit> limits) {
        ai.setLimits(limits.isEmpty() ? null : gson.toJson(limits));
    }

    /**
     * @return the limit of the user, {@link AILimit#unlimited()} when there is none
     */
    public static AILimit of(AI ai, String userUuid) {
        AILimit limit = userUuid == null ? null : read(ai).get(userUuid);
        return limit == null ? AILimit.unlimited() : limit;
    }
}
