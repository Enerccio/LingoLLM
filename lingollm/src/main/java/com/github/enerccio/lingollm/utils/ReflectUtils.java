package com.github.enerccio.lingollm.utils;

import com.github.enerccio.tools.Pair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ReflectUtils {

    private static final Map<Class<?>, Map<Pair<String, List<Class<?>>>, Method>> methodCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<String, Field>> fieldCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Class<? extends Annotation>, List<Field>>> fieldAnnotCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Class<? extends Annotation>, List<Method>>> methodAnnotCache = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<Class<?>, List<Field>>> fieldTypeCache = new ConcurrentHashMap<>();

    public static List<Field> getAnnotatedFields(Class<?> clazz, Class<? extends Annotation> annotation) {
        if (clazz == null || annotation == null)
            return Collections.emptyList();
        if (fieldAnnotCache.containsKey(clazz) && fieldAnnotCache.get(clazz).containsKey(annotation))
            return fieldAnnotCache.get(clazz).get(annotation);

        List<Field> fields = new ArrayList<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(annotation)) {
                field.setAccessible(true);
                fields.add(field);
            }
        }

        if (clazz.getSuperclass() != null)
            fields.addAll(getAnnotatedFields(clazz.getSuperclass(), annotation));

        fieldAnnotCache.computeIfAbsent(clazz, cls -> new HashMap<>())
                .put(annotation, fields);

        return fields;
     }

    public static List<Method> getAnnotatedMethods(Class<?> clazz, Class<? extends Annotation> annotation) {
        if (clazz == null || annotation == null)
            return Collections.emptyList();
        if (methodAnnotCache.containsKey(clazz) && methodAnnotCache.get(clazz).containsKey(annotation)) {
            return methodAnnotCache.get(clazz).get(annotation);
        }

        List<Method> methods = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotation)) {
                method.setAccessible(true);
                methods.add(method);
            }
        }

        if (clazz.getSuperclass() != null)
            methods.addAll(getAnnotatedMethods(clazz.getSuperclass(), annotation));

        methodAnnotCache.computeIfAbsent(clazz, cls -> new HashMap<>())
                .put(annotation, methods);

        return methods;
    }

    public static Object getFieldValue(Field f, Object object) throws IllegalAccessException {
        if (object == null)
            return null;
        f.setAccessible(true);
        return f.get(object);
    }

    public static Object getFieldValue(Object object, String fieldName) throws Exception {
        return getFieldValue(object, object.getClass(), fieldName);
    }

    public static Object getFieldValue(Object object, Class<?> clazz, String fieldName) throws Exception {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            return getFieldValue(f, object);
        }
        catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null)
                return getFieldValue(object, clazz.getSuperclass(), fieldName);
            else
                throw e;
        }
    }

    public static Object getFieldValueStatic(Field f) throws IllegalAccessException {
        f.setAccessible(true);
        return f.get(null);
    }

    public static Object getFieldValueStatic(Class<?> clazz, String fieldName) throws Exception {
        try {
            Field f = clazz.getDeclaredField(fieldName);
            return getFieldValueStatic(f);
        }
        catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null)
                return getFieldValueStatic(clazz.getSuperclass(), fieldName);
            else
                throw e;
        }
    }

    public static Field getField(Class<?> clazz, String fieldName) throws Exception {
        if (clazz == null)
            return null;
        if (fieldCache.containsKey(clazz) && fieldCache.get(clazz).containsKey(fieldName)) {
            return fieldCache.get(clazz).get(fieldName);
        }

        Map<String, Field> fields = new HashMap<>();

        Class<?> p = clazz;
        while (p != null) {
            for (Field field : p.getDeclaredFields()) {
                field.setAccessible(true);
                fields.put(field.getName(), field);
            }
            p = p.getSuperclass();
        }

        fieldCache.put(clazz, fields);

        return fieldCache.get(clazz).get(fieldName);
    }

    public static void setFieldValue(Object object, Class<?> clazz, String fieldName, Object fieldValue) throws Exception {
        Field f = getField(clazz, fieldName);

        f.setAccessible(true);
        f.set(object, fieldValue);
    }

    public static Object invokeGetter(Object object, String methodName) throws Exception {
        return invokeGetter(object, object.getClass(), methodName);
    }

    public static Object invokeGetter(Object object, Class<?> clazz, String methodName) throws Exception {
        try {
            Method m = clazz.getDeclaredMethod(methodName);
            return invokeGetter(m, object);
        }
        catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null)
                return invokeGetter(object, clazz.getSuperclass(), methodName);
            else
                throw e;
        }
    }

    public static Object invokeGetter(Method m, Object object) throws Exception {
        if (object == null)
            return null;
        m.setAccessible(true);
        return m.invoke(object);
    }

    @SuppressWarnings("unchecked")
    public static <T> T getFieldValueWithType(Object object, String field, Class<? extends T> clazz) throws Exception {
        return (T) getFieldValue(object, field);
    }

    public static boolean isTypeOf(Class<?> clazz, Class<?> type) {
        if (clazz.equals(type))
            return true;

        while((clazz = clazz.getSuperclass()) != null) {
            if (clazz.equals(type))
                return true;
        }

        return false;
    }

    public static List<Field> getFieldsOfType(Class<?> clazz, Class<?> type) {
        if (clazz == null)
            return new ArrayList<>();

        if (fieldTypeCache.containsKey(clazz) && fieldTypeCache.get(clazz).containsKey(type))
            return fieldTypeCache.get(clazz).get(type);

        Field[] fields = clazz.getDeclaredFields();
        List<Field> fieldsInstanceOf = new ArrayList<>();

        for (Field field : fields) {
            if (isTypeOf(field.getType(), type)) {
                field.setAccessible(true);
                fieldsInstanceOf.add(field);
            }
        }

        fieldsInstanceOf.addAll(getFieldsOfType(clazz.getSuperclass(), type));

        fieldTypeCache.computeIfAbsent(clazz, cls -> new HashMap<>())
                .put(type, fieldsInstanceOf);

        return fieldsInstanceOf;
    }

    public static Method getMethod(Class<?> clazz, String methodName, Class<?>[] methodArguments) throws Exception {
        if (clazz == null)
            return null;
        Pair<String, List<Class<?>>> key = Pair.of(methodName, List.of(methodArguments));
        if (methodCache.containsKey(clazz) && methodCache.get(clazz).containsKey(key)) {
            return methodCache.get(clazz).get(key);
        }

        Map<Pair<String, List<Class<?>>>, Method> methods = new HashMap<>();

        Class<?> p = clazz;
        while (p != null) {
            for (Method method : p.getDeclaredMethods()) {
                method.setAccessible(true);
                methods.put(Pair.of(method.getName(), List.of(method.getParameterTypes())), method);
            }
            p = p.getSuperclass();
        }

        methodCache.put(clazz, methods);

        return methodCache.get(clazz).get(key);
    }
}
