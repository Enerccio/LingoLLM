package com.github.enerccio.lingollm.domain.service.impl;

import com.github.enerccio.lingollm.domain.service.ExtensionService;
import com.github.enerccio.lingollm.instruct.verify.ExtensionVerification;
import com.github.enerccio.lingollm.instruct.verify.ExtensionVerifier;
import com.github.enerccio.lingollm.utils.ReflectUtils;
import com.github.enerccio.tools.Pair;
import org.osgi.framework.Bundle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ExtensionServiceImpl implements ExtensionService {
    private static final Logger log = LoggerFactory.getLogger(ExtensionServiceImpl.class);

    private final Map<Pair<String, String>, List<ExtensionDecorator>> decoratorMap = new ConcurrentHashMap<>();

    @Override
    public ExtendableMethodContext createContextHolder(Object owner) {
        return new ContextImpl(owner);
    }

    @Override
    public void registerDecorator(ExtensionDecorator decorator, String className, String methodName) {
        Pair<String, String> identifier = Pair.of(className, methodName);
        decoratorMap.computeIfAbsent(identifier, _ -> new CopyOnWriteArrayList<>()).add(decorator);
    }

    @Override
    public void unregisterDecorator(ExtensionDecorator decorator) {
        for (List<ExtensionDecorator> decorators : decoratorMap.values()) {
            decorators.remove(decorator);
        }
    }

    @Override
    public ExtensionVerification verifyExtension(Bundle bundle, File jar) throws Exception {
        ExtensionVerification verification = new ExtensionVerifier(ExtensionServiceImpl.class.getClassLoader()).verify(bundle, jar);
        ExtensionVerifier.writeReport(jar, verification);
        return verification;
    }

    @Override
    public void onExtendableMethodEnter(Class<?> cls, Object extendableSelf, ExtendableMethodContext context, String method) {
        List<ExtensionDecorator> decorators = getDecorators(cls, method);
        if (decorators.isEmpty()) return;

        for (ExtensionDecorator decorator : decorators) {
            try {
                decorator.onMethodEnter(extendableSelf, context);
            } catch (Throwable t) {
                log.error("Unhandled exception in extension '{}' while decorating {}.{}:",
                        decorator.getClass().getName(),
                        extendableSelf.getClass().getSimpleName(), method, t);
            }
        }
    }

    @Override
    public void onExtendableMethodLeave(Class<?> cls, Object extendableSelf, ExtendableMethodContext context, String method, Throwable throwing) {
        List<ExtensionDecorator> decorators = getDecorators(cls, method);
        if (decorators.isEmpty()) return;

        for (ExtensionDecorator decorator : decorators) {
            try {
                decorator.onMethodLeave(extendableSelf, context, throwing);
            } catch (ModCompatibilityException e) {
                log.warn("Mod '{}' skipped on {}.{}: {}",
                        decorator.getClass().getName(),
                        extendableSelf.getClass().getName(),
                        method,
                        e.getMessage());
            } catch (Throwable t) {
                log.error("Unhandled exception in extension '{}' while decorating {}.{}:",
                        decorator.getClass().getName(),
                        extendableSelf.getClass().getSimpleName(), method, t);
            }
        }
    }

    private List<ExtensionDecorator> getDecorators(Class<?> cls, String method) {
        Pair<String, String> key = Pair.of(cls.getName(), method);
        List<ExtensionDecorator> decorators = decoratorMap.get(key);
        return decorators != null ? decorators : Collections.emptyList();
    }

    private static final Map<Class<?>, Class<?>> PRIMITIVE_WRAPPERS = Map.of(
            boolean.class, Boolean.class, byte.class, Byte.class, char.class, Character.class,
            short.class, Short.class, int.class, Integer.class, long.class, Long.class,
            float.class, Float.class, double.class, Double.class);

    @SuppressWarnings({"DuplicateExpressions", "unchecked"})
    private static class ContextImpl implements ExtendableMethodContext {
        private final Map<String, Pair<Object, Class<?>>> localVariables;
        private final Map<String, Pair<Object, Class<?>>> methodArguments;
        private final Object targetInstance;

        private boolean hasReturn;
        private Class<?> returnType;
        private Object originalReturn;
        private Object currentReturn;

        private ContextImpl(Object targetInstance) {
            this.targetInstance = targetInstance;
            this.localVariables = new HashMap<>();
            this.methodArguments = new HashMap<>();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getLocalVariable(String name, Class<T> expectedType) throws Exception {
            Pair<Object, Class<?>> registeredPair = localVariables.get(name);
            if (registeredPair == null) {
                throw new ModCompatibilityException(String.format(
                        """
                                Required component variable '%s' was not found in %s. \
                                The core UI code may have been refactored or renamed.""",
                        name, targetInstance.getClass().getSimpleName()
                ));
            }

            Object val = registeredPair.getA();
            Class<?> registeredClass = registeredPair.getB();

            if (!expectedType.isAssignableFrom(registeredClass)) {
                throw new ModCompatibilityException(String.format(
                        "Component variable '%s' in %s is of type %s, but extension expected %s.",
                        name, targetInstance.getClass().getSimpleName(),
                        registeredClass.getName(), expectedType.getName()
                ));
            }
            return (T) val;
        }

        @Override
        public void registerLocalVariable(String name, Object value, Class<?> cls) {
            localVariables.put(name, Pair.of(value, cls));
        }

        @Override
        public boolean hasLocalVariable(String name) {
            return localVariables.containsKey(name);
        }

        @Override
        public boolean hasLocalVariable(String name, Class<?> isOfType) {
            Pair<Object, Class<?>> registeredPair = localVariables.get(name);
            return registeredPair != null && isOfType.isAssignableFrom(registeredPair.getB());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getMethodArgument(String name, Class<T> expectedType) throws Exception {
            Pair<Object, Class<?>> registeredPair = methodArguments.get(name);
            if (registeredPair == null) {
                throw new ModCompatibilityException(String.format(
                        """
                                Required component argument '%s' was not found in %s. \
                                The core UI code may have been refactored or renamed.""",
                        name, targetInstance.getClass().getSimpleName()
                ));
            }

            Object val = registeredPair.getA();
            Class<?> registeredClass = registeredPair.getB();

            if (!expectedType.isAssignableFrom(registeredClass)) {
                throw new ModCompatibilityException(String.format(
                        "Component argument '%s' in %s is of type %s, but extension expected %s.",
                        name, targetInstance.getClass().getSimpleName(),
                        registeredClass.getName(), expectedType.getName()
                ));
            }
            return (T) val;
        }

        @Override
        public MethodArgumentRefresher registerMethodArgument(String name, Object value, Class<?> cls) {
            methodArguments.put(name, Pair.of(value, cls));
            return () -> methodArguments.get(name).getA();
        }

        @Override
        public boolean hasMethodArgument(String name) {
            return methodArguments.containsKey(name);
        }

        @Override
        public boolean hasMethodArgument(String name, Class<?> isOfType) {
            Pair<Object, Class<?>> registeredPair = methodArguments.get(name);
            return registeredPair != null && isOfType.isAssignableFrom(registeredPair.getB());
        }

        @Override
        public void registerReturnValue(Object value, Class<?> cls) {
            hasReturn = true;
            returnType = cls;
            originalReturn = value;
            currentReturn = value;
        }

        @Override
        public Object resolveReturnValue() {
            return currentReturn;
        }

        @Override
        public boolean hasReturn() {
            return hasReturn;
        }

        @Override
        public <T> T getOriginalReturn(Class<T> expectedType) {
            requireReturn("read");
            requireReturnType(expectedType);
            return (T) originalReturn;
        }

        @Override
        public <T> T getCurrentReturn(Class<T> expectedType) {
            requireReturn("read");
            requireReturnType(expectedType);
            return (T) currentReturn;
        }

        @Override
        public void setCurrentReturn(Object value) {
            requireReturn("replace");
            if (returnType.isPrimitive()) {
                if (value == null || !PRIMITIVE_WRAPPERS.get(returnType).isInstance(value)) {
                    throw new ModCompatibilityException(String.format(
                            "Return value of the method in %s is %s, the extension set %s.",
                            targetInstance.getClass().getSimpleName(), returnType.getName(),
                            value == null ? "null" : value.getClass().getName()));
                }
            } else if (value != null && !returnType.isInstance(value)) {
                throw new ModCompatibilityException(String.format(
                        "Return value of the method in %s is %s, the extension set %s.",
                        targetInstance.getClass().getSimpleName(), returnType.getName(), value.getClass().getName()));
            }
            currentReturn = value;
        }

        private void requireReturn(String action) {
            if (!hasReturn) {
                throw new ModCompatibilityException(String.format(
                        """
                                The extension wanted to %s the return value of a method of %s, which has none yet: the method \
                                is void, throws, or this is onMethodEnter.""",
                        action, targetInstance.getClass().getSimpleName()));
            }
        }

        private void requireReturnType(Class<?> expectedType) {
            if (!expectedType.isAssignableFrom(returnType)) {
                throw new ModCompatibilityException(String.format(
                        "Return value of the method in %s is of type %s, but extension expected %s.",
                        targetInstance.getClass().getSimpleName(), returnType.getName(), expectedType.getName()));
            }
        }

        @Override
        public <T> T callReflectiveMethod(Object object, String methodName, Class<?>[] argumentTypes, Object[] arguments, Class<T> returnType) {
            try {
                Method method = ReflectUtils.getMethod(object.getClass(), methodName, argumentTypes);
                if (method == null) {
                    throw new ModCompatibilityException(String.format(
                            """
                                    Required methodName '%s' was not found in %s. \
                                    The core UI code may have been refactored or renamed.""",
                            methodName, targetInstance.getClass().getSimpleName()
                    ));
                }
                if (!returnType.isAssignableFrom(method.getReturnType())) {
                    throw new ModCompatibilityException(String.format(
                            "Method '%s' in %s is of return type %s, but extension expected %s.",
                            methodName, targetInstance.getClass().getSimpleName(),
                            method.getReturnType().getName(), returnType.getName()
                    ));
                }
                try {
                    return (T) method.invoke(object, arguments);
                } catch (Throwable e) {
                    throw new ModCompatibilityException(String.format("Method %s.%s threw an exception",
                            targetInstance.getClass().getSimpleName(), methodName), e);
                }
            } catch (ModCompatibilityException e) {
                throw e;
            } catch (Throwable t) {
                throw new ModCompatibilityException(String.format(
                        """
                                Required methodName '%s' was not found in %s. \
                                The core UI code may have been refactored or renamed.""",
                        methodName, targetInstance.getClass().getSimpleName()
                ));
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T getReflectiveFieldValue(Object object, String field, Class<T> fieldReturnType) {
            try {
                Field f = ReflectUtils.getField(object.getClass(), field);
                if (f == null) {
                    throw new ModCompatibilityException(String.format(
                            """
                                    Required field '%s' was not found in %s. \
                                    The core UI code may have been refactored or renamed.""",
                            field, targetInstance.getClass().getSimpleName()
                    ));
                }
                if (!fieldReturnType.isAssignableFrom(f.getType())) {
                    throw new ModCompatibilityException(String.format(
                            "Field '%s' in %s is of type %s, but extension expected %s.",
                            field, targetInstance.getClass().getSimpleName(),
                            f.getType().getName(), fieldReturnType.getName()
                    ));
                }
                return (T) f.get(object);
            } catch (ModCompatibilityException e) {
                throw e;
            } catch (Throwable t) {
                throw new ModCompatibilityException(String.format(
                        """
                                Required field '%s' was not found in %s. \
                                The core UI code may have been refactored or renamed.""",
                        field, targetInstance.getClass().getSimpleName()
                ));
            }
        }

        @Override
        public <T> void setReflectiveFieldValue(Object object, String field, T value, Class<T> fieldReturnType) {
            try {
                Field f = ReflectUtils.getField(object.getClass(), field);
                if (f == null) {
                    throw new ModCompatibilityException(String.format(
                            """
                                    Required field '%s' was not found in %s. \
                                    The core UI code may have been refactored or renamed.""",
                            field, targetInstance.getClass().getSimpleName()
                    ));
                }
                if (!fieldReturnType.isAssignableFrom(f.getType())) {
                    throw new ModCompatibilityException(String.format(
                            "Field '%s' in %s is of type %s, but extension expected %s.",
                            field, targetInstance.getClass().getSimpleName(),
                            f.getType().getName(), fieldReturnType.getName()
                    ));
                }
                f.set(object, value);
            } catch (Throwable t) {
                throw new ModCompatibilityException(String.format(
                        """
                                Required field '%s' was not found in %s. \
                                The core UI code may have been refactored or renamed.""",
                        field, targetInstance.getClass().getSimpleName()
                ));
            }
        }
    }

    private static class ModCompatibilityException extends RuntimeException {
        public ModCompatibilityException(String text) {
            super(text);
        }

        public ModCompatibilityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}