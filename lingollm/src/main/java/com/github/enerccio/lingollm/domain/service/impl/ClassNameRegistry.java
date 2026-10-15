package com.github.enerccio.lingollm.domain.service.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stateless objects by the name of their class, the name being what is stored in the database. A name that is not
 * registered is loaded by the application if it is a {@code type} with a public constructor without arguments, and
 * cached.
 */
class ClassNameRegistry<F> {

    private static final Logger log = LoggerFactory.getLogger(ClassNameRegistry.class);

    private final Class<F> type;
    private final Map<String, F> instances = new ConcurrentHashMap<>();
    // names that are known not to load, so an object of a missing extension doesn't retry on every access
    private final Map<String, Boolean> unknown = new ConcurrentHashMap<>();

    ClassNameRegistry(Class<F> type) {
        this.type = type;
    }

    F get(String className) {
        if (className == null || className.isBlank()) {
            return null;
        }
        F instance = instances.get(className);
        if (instance != null || unknown.containsKey(className)) {
            return instance;
        }
        instance = load(className);
        if (instance == null) {
            unknown.put(className, Boolean.TRUE);
            return null;
        }
        F existing = instances.putIfAbsent(className, instance);
        return existing != null ? existing : instance;
    }

    void register(F instance) {
        String name = instance.getClass().getName();
        instances.put(name, instance);
        unknown.remove(name);
    }

    void unregister(Class<? extends F> clazz) {
        instances.remove(clazz.getName());
    }

    Collection<F> all() {
        return new ArrayList<>(instances.values());
    }

    private F load(String className) {
        try {
            Class<?> loaded = Class.forName(className, true, ClassNameRegistry.class.getClassLoader());
            if (!type.isAssignableFrom(loaded)) {
                // the name comes from the database (backups, imports), never create what is not wanted
                log.warn("{} is not a {}", className, type.getSimpleName());
                return null;
            }
            return type.cast(loaded.getConstructor().newInstance());
        } catch (ReflectiveOperationException | LinkageError e) {
            log.warn("{} {} can't be loaded: {}", type.getSimpleName(), className, e.toString());
            return null;
        }
    }

}
