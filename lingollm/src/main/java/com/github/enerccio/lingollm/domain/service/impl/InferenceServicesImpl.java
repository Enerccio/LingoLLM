package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.collections.AIType;
import com.github.enerccio.lingollm.domain.model.impl.AI;
import com.github.enerccio.lingollm.domain.service.FunctionRegistry;
import com.github.enerccio.lingollm.domain.service.InferenceService;
import com.github.enerccio.lingollm.domain.service.InferenceServices;
import com.github.enerccio.lingollm.domain.traits.SupportedAI;
import org.springframework.beans.factory.InitializingBean;

import java.lang.reflect.Constructor;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class InferenceServicesImpl implements InferenceServices, InitializingBean {

    private List<Class<? extends InferenceService>> inferenceProviders;
    private final Map<AIType, Constructor<? extends InferenceService>> providerMap = new EnumMap<>(AIType.class);
    private final FunctionRegistry functionRegistry = new FunctionRegistryImpl();
    private final List<ToolRoleResolver> toolRoleResolvers = new CopyOnWriteArrayList<>();

    public List<Class<? extends InferenceService>> getInferenceProviders() {
        return inferenceProviders;
    }

    public void setInferenceProviders(List<Class<? extends InferenceService>> services) {
        this.inferenceProviders = services;
    }

    @Override
    public FunctionRegistry getFunctionRegistry() {
        return functionRegistry;
    }

    @Override
    public void addToolRoleResolver(ToolRoleResolver resolver) {
        toolRoleResolvers.add(resolver);
    }

    @Override
    public void removeToolRoleResolver(ToolRoleResolver resolver) {
        toolRoleResolvers.remove(resolver);
    }

    @Override
    public String resolveToolRole(AI ai) {
        for (ToolRoleResolver resolver : toolRoleResolvers) {
            String role = resolver.toolRole(ai);
            if (role != null && !role.isBlank()) {
                return role;
            }
        }
        return DEFAULT_TOOL_ROLE;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (inferenceProviders == null) {
            return;
        }

        for (Class<? extends InferenceService> clazz : inferenceProviders) {
            SupportedAI annotation = clazz.getAnnotation(SupportedAI.class);
            if (annotation == null) {
                throw new IllegalStateException("Class " + clazz.getName() + " missing @SupportedAI annotation");
            }

            Constructor<? extends InferenceService> constructor = clazz.getConstructor(AI.class);
            providerMap.put(annotation.value(), constructor);
        }
    }

    @Override
    public InferenceService forAI(AI ai) throws Exception {
        if (ai == null)
            return null;

        if (ai.getInferenceService() != null) {
            return ai.getInferenceService();
        }

        Constructor<? extends InferenceService> constructor = providerMap.get(ai.getAiType());
        if (constructor == null) {
            throw new IllegalArgumentException("Unknown provider for AI type: " + ai.getAiType());
        }

        InferenceService service = constructor.newInstance(ai);
        ai.setInferenceService(service);
        return service;
    }
}