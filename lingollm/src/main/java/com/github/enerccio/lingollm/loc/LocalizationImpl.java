package com.github.enerccio.lingollm.loc;


import com.github.enerccio.lingollm.bound.SessionPoint;
import com.github.enerccio.lingollm.domain.collections.*;
import com.github.enerccio.lingollm.domain.model.Setting;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.model.impl.FreeChat;
import com.github.enerccio.lingollm.domain.model.impl.FreeChatMessage;
import com.github.enerccio.lingollm.domain.model.impl.Resource;
import com.github.enerccio.lingollm.domain.security.model.User;
import com.github.enerccio.lingollm.domain.traits.CleanupReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.context.request.RequestContextHolder;

import java.text.Collator;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LocalizationImpl implements Localization, InitializingBean {

    private final static Logger log = LoggerFactory.getLogger(LocalizationImpl.class);
    private final Map<Language, LocalizationBundle> bundles = new ConcurrentHashMap<>();
    private Language defaultLanguage = Language.EN;

    @Autowired
    private SessionPoint sessionPoint;

    private final Map<AIType, L> aiTypes = new HashMap<>();
    private final Map<ReasoningEffort, L> reasoningEfforts = new HashMap<>();
    private final Map<LimitStrategy, L> limitStrategies = new HashMap<>();
    private final Map<ResetStrategy, L> resetStrategies = new HashMap<>();
    private final Map<CleanupReference.Policy, L> cleanupPolicies = new HashMap<>();
    private final Map<Languages, L> languageNames = new EnumMap<>(Languages.class);
    private final Map<Class<?>, L> domainObjects = new HashMap<>();

    public LocalizationImpl() {
        loadMaps();
    }

    public void setBundles(List<LocalizationBundle> bundles) {
        for (LocalizationBundle bundle : bundles) {
            registerBundle(bundle);
        }
    }

    public void setDefaultLanguage(Language defaultLanguage) {
        this.defaultLanguage = defaultLanguage;
    }

    @Override
    public void afterPropertiesSet() {
        LocalizationBundle bundle = bundles.get(defaultLanguage);
        if (bundle == null) {
            throw new IllegalStateException("No localization bundle for the default language " + defaultLanguage);
        }
        // the other bundles fall back to this one, so it has to be complete
        String missing = missingKeys(bundle);
        if (!missing.isEmpty()) {
            log.error("\n*********** LOCALIZATION IS MISSING! ***********\n"
                    + missing
                    + "\n************************************************\n");
            throw new IllegalStateException("The bundle of the default language " + defaultLanguage + " is incomplete");
        }
    }

    private void loadMaps() {
        aiTypes.put(AIType.OPEN_AI_COMPATIBLE, L.ENUM_AI_TYPE_OPEN_AI_COMPATIBLE);


        reasoningEfforts.put(ReasoningEffort.NONE, L.ENUM_REASONING_NONE);
        reasoningEfforts.put(ReasoningEffort.LOW, L.ENUM_REASONING_LOW);
        reasoningEfforts.put(ReasoningEffort.MEDIUM, L.ENUM_REASONING_MEDIUM);
        reasoningEfforts.put(ReasoningEffort.HIGH, L.ENUM_REASONING_HIGH);

        limitStrategies.put(LimitStrategy.UNLIMITED, L.ENUM_LIMIT_UNLIMITED);
        limitStrategies.put(LimitStrategy.TOKEN_LIMIT, L.ENUM_LIMIT_TOKEN_LIMIT);
        limitStrategies.put(LimitStrategy.NOT_AVAILABLE, L.ENUM_LIMIT_NOT_AVAILABLE);

        resetStrategies.put(ResetStrategy.DAILY, L.ENUM_RESET_DAILY);
        resetStrategies.put(ResetStrategy.WEEKLY, L.ENUM_RESET_WEEKLY);
        resetStrategies.put(ResetStrategy.MONTHLY, L.ENUM_RESET_MONTHLY);




        cleanupPolicies.put(CleanupReference.Policy.STRONG, L.ENUM_CLEANUP_POLICY_STRONG);
        cleanupPolicies.put(CleanupReference.Policy.OWNED_BY, L.ENUM_CLEANUP_POLICY_OWNED_BY);
        cleanupPolicies.put(CleanupReference.Policy.OWNS, L.ENUM_CLEANUP_POLICY_OWNS);
        cleanupPolicies.put(CleanupReference.Policy.WEAK, L.ENUM_CLEANUP_POLICY_WEAK);



        languageNames.put(Languages.JAPANESE, L.ENUM_LANGUAGE_JAPANESE);
        languageNames.put(Languages.GERMAN, L.ENUM_LANGUAGE_GERMAN);

        domainObjects.put(AI.class, L.LABEL_MODEL);
        domainObjects.put(com.github.enerccio.lingollm.domain.model.impl.Language.class, L.LABEL_LANGUAGE_PROFILE);
        domainObjects.put(FreeChat.class, L.LABEL_FREE_CHAT);
        domainObjects.put(FreeChatMessage.class, L.ENUM_DOMAIN_MESSAGE);
        domainObjects.put(Resource.class, L.ENUM_DOMAIN_RESOURCE);
        domainObjects.put(Setting.class, L.ENUM_DOMAIN_SETTING);
        domainObjects.put(User.class, L.LABEL_USER);
    }

    private static String missingKeys(LocalizationBundle bundle) {
        StringBuilder missing = new StringBuilder();
        for (L key : L.values()) {
            if (bundle.getValue(key) == null) {
                missing.append(key.name()).append('\n');
            }
        }
        return missing.toString();
    }

    @Override
    public String getValue(L l) {
        String text = currentBundle().getValue(l);
        if (text == null) {
            LocalizationBundle fallback = bundles.get(defaultLanguage);
            text = fallback == null ? null : fallback.getValue(l);
        }
        if (text == null) {
            log.error("Key {} is not localized!", l.name());
            return "NOT LOCALIZED!";
        }
        return text;
    }

    @Override
    public L parseValue(String text) {
        for(Map.Entry<L, String> entry : currentBundle().getMessages().entrySet()) {
            if(entry.getValue().equals(text)) {
                return entry.getKey();
            }
        }

        return null;
    }

    @Override
    public Locale getLocale() {
        return currentBundle().getLocale();
    }

    @Override
    public DateFormat getDateFormat() {
        return currentBundle().getDateFormat();
    }

    @Override
    public DateFormat getHourFormat() {
        return currentBundle().getHourFormat();
    }

    @Override
    public DateFormat getDateHourFormat() {
        return currentBundle().getDateHourFormat();
    }

    @Override
    public Language getDefaultLanguage() {
        return defaultLanguage;
    }

    @Override
    public Language getCurrentLanguage() {
        Language language = sessionLanguage();
        return language != null && bundles.containsKey(language) ? language : defaultLanguage;
    }

    @Override
    public List<Language> getAvailableLanguages() {
        return bundles.values().stream()
                .sorted(Comparator.comparing(LocalizationBundle::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(LocalizationBundle::getLanguage)
                .toList();
    }

    @Override
    public String getDisplayName(Language language) {
        LocalizationBundle bundle = bundles.get(language);
        return bundle == null ? language.code() : bundle.getDisplayName();
    }

    @Override
    public Language resolveLanguage(String code) {
        if (code == null || code.isBlank()) {
            return defaultLanguage;
        }
        Language language = Language.of(code);
        return bundles.containsKey(language) ? language : defaultLanguage;
    }

    @Override
    public void registerBundle(LocalizationBundle bundle) {
        Language language = bundle.getLanguage();
        if (bundles.putIfAbsent(language, bundle) != null) {
            throw new IllegalArgumentException("Language " + language + " already has a localization bundle");
        }
        if (!language.equals(defaultLanguage)) {
            String missing = missingKeys(bundle);
            if (!missing.isEmpty()) {
                log.warn("Localization bundle {} lacks texts, the {} ones are used instead:\n{}", language,
                        defaultLanguage, missing);
            }
        }
    }

    @Override
    public void unregisterBundle(LocalizationBundle bundle) {
        Language language = bundle.getLanguage();
        if (!language.equals(defaultLanguage)) {
            bundles.remove(language, bundle);
        }
    }

    private LocalizationBundle currentBundle() {
        return bundles.get(getCurrentLanguage());
    }

    /**
     * Language stored in the session, {@code null} when there is no session to ask: the session scoped bean can be
     * used only in a request (or in a thread that got its attributes, see {@code ThreadCopyRequestAttributes}).
     */
    private Language sessionLanguage() {
        if (sessionPoint == null || RequestContextHolder.getRequestAttributes() == null) {
            return null;
        }
        try {
            return sessionPoint.getLanguage();
        } catch (RuntimeException e) {
            // the session is gone
            log.trace("No session to read the language from: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Comparator<String> createNaturalLanguageComparator() {
        return new NaturalOrderComparator(Collator.getInstance(getLocale()));
    }

    @Override
    public Comparator<String> createLocaleComparator() {
        Collator collator = Collator.getInstance(getLocale());
        return collator::compare;
    }

    @Override
    public L getAIType(AIType type) {
        return aiTypes.get(type);
    }

    @Override
    public L getReasoningEffort(ReasoningEffort type) {
        return reasoningEfforts.get(type);
    }
    @Override
    public L getLimitStrategy(LimitStrategy type) {
        return limitStrategies.get(type);
    }

    @Override
    public L getResetStrategy(ResetStrategy type) {
        return resetStrategies.get(type);
    }

    @Override
    public L getLanguageName(Languages language) {
        return languageNames.get(language);
    }

    @Override
    public L getCleanupPolicy(CleanupReference.Policy type) {
        return cleanupPolicies.get(type);
    }

    @Override
    public String localizeDomainObject(Class<?> clazz) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            L key = domainObjects.get(c);
            if (key != null) {
                return getValue(key);
            }
        }
        return clazz == null ? "" : clazz.getSimpleName();
    }
}
