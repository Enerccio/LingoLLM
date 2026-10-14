package com.github.enerccio.lingollm.domain.listener;

import com.github.enerccio.lingollm.domain.model.ExtendableEntity;
import com.github.enerccio.lingollm.domain.traits.ExtendedAttribute;
import com.github.enerccio.lingollm.domain.traits.Fulltextable;
import com.github.enerccio.lingollm.utils.ReflectUtils;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.persistence.PostLoad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ExtendableEntityListener {
    private static final Logger log = LoggerFactory.getLogger(ExtendableEntityListener.class);

    private static final ConcurrentHashMap<Class<?>, AttributesAccessor> accessorMap = new ConcurrentHashMap<>();

    public void serialize(ExtendableEntity entity) throws Exception {
        accessorFor(entity).serialize(entity);
    }

    /**
     * Rebuilds {@code _fulltext} from the {@link Fulltextable} fields without touching {@code extendedContent}, used
     * to fill the column for rows saved before it existed.
     */
    public void updateFulltext(ExtendableEntity entity) throws Exception {
        accessorFor(entity).updateFulltext(entity);
    }

    @PostLoad
    public void deserialize(ExtendableEntity entity) throws Exception {
        accessorFor(entity).deserialize(entity);
    }

    private static AttributesAccessor accessorFor(ExtendableEntity entity) {
        return accessorMap.computeIfAbsent(entity.getClass(), key -> {
            try {
                return new AttributesAccessor(entity);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private static class AttributesAccessor {
        private final Gson gson = new GsonBuilder().serializeNulls().create();
        // format used before ISO-8601: the 'Z' is a literal, values are in the JVM's time zone
        private static final DateTimeFormatter LEGACY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd'Z'HH:mm:ss.SSS");

        private final Class<?> clazz;
        private final Map<String, Field> attributes = new HashMap<>();
        private final List<Field> fulltextFields;

        public AttributesAccessor(Object handle) throws Exception {
            clazz = handle.getClass();
            fulltextFields = ReflectUtils.getAnnotatedFields(clazz, Fulltextable.class);
            combClass();
        }

        private void combClass() throws Exception {
            List<Field> fields = ReflectUtils.getAnnotatedFields(clazz, ExtendedAttribute.class);
            for (Field f : fields) {
                attributes.put(f.getName(), f);
            }
        }

        /**
         * Dates are stored as ISO-8601 instants in UTC, e.g. {@code 2026-10-09T08:15:30.123Z}.
         */
        static String formatDate(Date date) {
            return DateTimeFormatter.ISO_INSTANT.format(date.toInstant());
        }

        /**
         * Reads ISO-8601 instants and, for values stored by older versions, the legacy format in the JVM's time zone.
         */
        static Date parseDate(String value) {
            try {
                return Date.from(Instant.parse(value));
            } catch (DateTimeParseException e) {
                return Date.from(LocalDateTime.parse(value, LEGACY_DATE_FORMAT).atZone(ZoneId.systemDefault()).toInstant());
            }
        }

        public void serialize(ExtendableEntity entity) throws Exception {
            JsonObject root = new JsonObject();
            for (String field : attributes.keySet()) {
                Field f = attributes.get(field);
                ExtendedAttribute extAttrAnnot = f.getAnnotation(ExtendedAttribute.class);

                if (!extAttrAnnot.inject()) {
                    Object value = f.get(entity);
                    String strValue = value == null ? null : value.toString();
                    if (strValue == null)
                        continue;

                    if (value instanceof Date)
                        strValue = formatDate((Date) value);

                    root.addProperty(field, strValue);
                } else {
                    Object value = f.get(entity);
                    if (value == null) {
                        continue;
                    }

                    JsonElement injected = value instanceof JsonElement element ? element : gson.toJsonTree(value, f.getGenericType());
                    if (injected.isJsonObject()) {
                        JsonObject object = injected.getAsJsonObject();
                        for (String key : object.keySet()) {
                            root.add(extAttrAnnot.injectPrefix() + "_" + key, object.get(key));
                        }
                    } else if (!injected.isJsonNull()) {
                        // not an object (list, string, ...): cannot be flattened, stored as a single value
                        root.add(extAttrAnnot.injectPrefix(), injected);
                    }
                }
            }

            entity.setExtendedContent(gson.toJson(root).getBytes(StandardCharsets.UTF_8));
            updateFulltext(entity);
        }

        /**
         * Text of all {@link Fulltextable} fields, one per line in declaration order, in {@code _fulltext};
         * null when the entity has no such field or all are empty.
         */
        public void updateFulltext(ExtendableEntity entity) throws Exception {
            if (fulltextFields.isEmpty()) {
                return;
            }

            StringJoiner text = new StringJoiner("\n");
            for (Field f : fulltextFields) {
                Object value = f.get(entity);
                if (value == null) {
                    continue;
                }
                String strValue = value instanceof Date date ? formatDate(date) : value.toString();
                if (!strValue.isBlank()) {
                    text.add(strValue);
                }
            }
            entity.set_fulltext(text.length() == 0 ? null : text.toString());
        }

        public void deserialize(ExtendableEntity entity) throws Exception {
            if (entity == null || entity.getExtendedContent() == null || entity.getExtendedContent().length < 2)
                return;

            JsonObject root = gson.fromJson(new String(entity.getExtendedContent(), StandardCharsets.UTF_8), JsonObject.class);
            Map<String, JsonObject> injectedObjects = new HashMap<>();

            for (String field : attributes.keySet()) {
                Field f = attributes.get(field);
                ExtendedAttribute extAttrAnnot = f.getAnnotation(ExtendedAttribute.class);
                if (extAttrAnnot.inject()) {
                    injectedObjects.put(field, new JsonObject());
                    continue; // second pass
                }

                JsonElement element = root.get(field);
                processElement(f, element, entity);
            }

            for (String field : injectedObjects.keySet()) {
                JsonObject object = injectedObjects.get(field);
                Field f = attributes.get(field);
                ExtendedAttribute extAttrAnnot = f.getAnnotation(ExtendedAttribute.class);
                String prefix = extAttrAnnot.injectPrefix() + "_";
                for (String key : root.keySet()) {
                    if (key.startsWith(prefix)) {
                        JsonElement element = root.get(key);
                        object.add(key.substring(prefix.length()), element);
                    }
                }
                if (JsonObject.class.equals(f.getType())) {
                    f.set(entity, object);
                    continue;
                }

                JsonElement source = object.size() > 0 ? object : root.get(extAttrAnnot.injectPrefix());
                if (source == null || source.isJsonNull()) {
                    continue; // keep the default value of the field
                }
                try {
                    f.set(entity, gson.fromJson(source, f.getGenericType()));
                } catch (Exception e) {
                    log.error("Error deserialize injected field {} of entity ({}), id: {}, error {}({})", field, entity.getClass().getSimpleName(), entity.getId(), e.getClass(), e.getMessage());
                }
            }
        }

        private void processElement(Field f, JsonElement element, ExtendableEntity entity) throws Exception {
            if (element != null) {
                String strVal = element.getAsString();

                try {
                    if (strVal == null || strVal.trim().isEmpty()) {
                        if (f.getType() == boolean.class) {
                            f.set(entity, false);
                        } else if (String.class.equals(f.getType())) {
                            f.set(entity, strVal != null ? strVal : "");
                        }
                        return;
                    }

                    Class<?> type = f.getType();

                    if (String.class.equals(type)) {
                        f.set(entity, strVal);
                    } else if (Integer.class.equals(type) || int.class.equals(type)) {
                        f.set(entity, Integer.valueOf(strVal));
                    } else if (Long.class.equals(type) || long.class.equals(type)) {
                        f.set(entity, Long.valueOf(strVal));
                    } else if (Double.class.equals(type) || double.class.equals(type)) {
                        f.set(entity, Double.valueOf(strVal));
                    } else if (Float.class.equals(type) || float.class.equals(type)) {
                        f.set(entity, Float.valueOf(strVal));
                    } else if (Date.class.equals(type)) {
                        f.set(entity, parseDate(strVal));
                    } else if (Boolean.class.equals(type) || boolean.class.equals(type)) {
                        f.set(entity, Boolean.valueOf(strVal));
                    } else if (Enum.class.isAssignableFrom(type)) {
                        //noinspection unchecked, rawtypes
                        f.set(entity, Enum.valueOf((Class) type, strVal));
                    }
                } catch (Exception e) {
                    log.error("Error deserialize entity ({}), id: {}, string value: {}, error {}({})", entity.getClass().getSimpleName(), entity.getId(), strVal, e.getClass(), e.getMessage());
                }
            }
        }
    }

}