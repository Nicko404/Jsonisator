package ru.clevertec.jsonisator.mapper;

import lombok.SneakyThrows;
import ru.clevertec.jsonisator.utils.JsonUtils;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonMapper {

    public String objectToJson(Object object) {
        return objectToJson(object, 0);
    }

    @SneakyThrows
    private String objectToJson(Object object, int tabulationCount) {
        Class<?> targetClass = object.getClass();
        Field[] fields = targetClass.getDeclaredFields();

        StringBuilder result = new StringBuilder();

        result.append(JsonUtils.tabulation(tabulationCount)).append("{\n");

        for (int i = 0; i < fields.length; i++) {
            Field field = fields[i];
            field.setAccessible(true);

            String fieldName = field.getName();
            Object fieldValue = field.get(object);
            String fieldType = field.getType().getSimpleName();

            boolean addQuotes = !JsonUtils.isDigit(fieldType);
            boolean isCollection = JsonUtils.isCollection(fieldType);
            boolean isMap = JsonUtils.isMap(fieldType);
            boolean isCustomObject = JsonUtils.isCustomObject(fieldType, field.getType());

            if (isMap) {
                result.append(generateLineFromMap(fieldName, fieldValue, tabulationCount + 2));
            } else if (isCollection) {
                result.append(generateLineFromList(fieldName, fieldValue, tabulationCount + 2));
            } else if (isCustomObject) {
                result.append(generateObjectLine(fieldName, fieldValue, tabulationCount + 2));
            } else {
                result.append(generateSingleLine(fieldName, fieldValue, addQuotes, tabulationCount + 2));
            }

            if (i < fields.length - 1) result.append(",");
            result.append("\n");
        }
        result.append(JsonUtils.tabulation(tabulationCount))
                .append("}");

        return result.toString();
    }

    private String generateSingleLine(String fieldName, Object fieldValue, boolean isString, int tabulationCount) {
        String value = fieldValue == null ? "" : fieldValue.toString();
        return JsonUtils.tabulation(tabulationCount) +
                "\"" +
                fieldName +
                "\": " +
                (isString ? "\"" : "") +
                value +
                (isString ? "\"" : "");
    }

    private String generateLineFromList(String fieldName, Object listObject, int tabulationCount) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (listObject == null) return generateSingleLine(fieldName, "[]", false, tabulationCount);

        StringBuilder result = new StringBuilder();
        Class<?> listClass = listObject.getClass();
        Method toArray = listClass.getMethod("toArray", (Class<?>[]) null);
        Object[] objects = (Object[]) toArray.invoke(listObject, (Object[]) null);

        result.append(JsonUtils.tabulation(tabulationCount))
                .append("\"")
                .append(fieldName)
                .append("\": [\n");

        for (int i = 0; i < objects.length; i++) {
            Object o = objects[i];
            result.append(objectToJson(o, tabulationCount + 2));
            if (i < objects.length - 1) result.append(",\n");
        }

        result.append("\n")
                .append(JsonUtils.tabulation(tabulationCount))
                .append("]");


        return result.toString();
    }

    @SneakyThrows
    private String generateObjectLine(String fieldName, Object value, int tabulationCount) {
        return JsonUtils.tabulation(tabulationCount) +
                "\"" +
                fieldName +
                "\": " +
                objectToJson(value, tabulationCount).trim();
    }

    @SneakyThrows
    private String generateLineFromMap(String fieldName, Object mapObject, int tabulationCount) {
        if (mapObject == null || ((Map) mapObject).isEmpty())
            return generateSingleLine(fieldName, "[]", false, tabulationCount);

        StringBuilder result = new StringBuilder();

        result.append(JsonUtils.tabulation(tabulationCount))
                .append("\"")
                .append(fieldName)
                .append("\"")
                .append(": {\n");

        Map map = (Map) mapObject;


        map.forEach((k, v) -> {
            boolean isString = v.toString().matches("\\d*.\\d");
            result.append(generateSingleLine(k.toString(), v.toString(), !isString, tabulationCount + 2));
            result.append(",\n");
        });

        result.deleteCharAt(result.length() - 2);

        result.append(JsonUtils.tabulation(tabulationCount))
                .append("}");
        return result.toString();
    }

    public <T> T parseObject(String json, Class<T> clazz) {
        Map<String, Object> nodes = new HashMap<>();
        Object instance = getInstanceOfClass(clazz);

        int documentStart = json.indexOf("{");
        int documentEnd = json.lastIndexOf("}");

        json = json.substring(documentStart + 1, documentEnd).trim();

        String[] lines = splitByFields(json);

        for (String line : lines) {
            if (isField(line)) {
                Node node = createNodeFromLine(line);
                nodes.put(node.key, node.value);

            } else if (isCollection(line)) {
                Node node = createNodeFromCollection(json, line, clazz);
                nodes.put(node.key, node.value);

            } else if (isObject(line)) {
                Node node = createNodeFromObject(json, line, clazz);
                nodes.put(node.key, node.value);
            }
        }

        try {
            Set<String> keys = nodes.keySet();
            for (String key : keys) {
                Field field = clazz.getDeclaredField(key);
                setValueToField(field, nodes.get(key), instance);
            }
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return (T) instance;
    }

    private Collection<Object> parseCollection(String json, Class<?> clazz, String collectionName) {
        Collection<Object> result = null;

        if (collectionName.equals("List")) result = new ArrayList<>();
        if (collectionName.equals("Set")) result = new HashSet<>();
        if (result == null) throw new RuntimeException();

        int start = json.indexOf("[");
        int end = json.lastIndexOf("]");

        if (end == -1) json = json.substring(start + 1).trim();
        else {
            int begin = json.indexOf("[");
            int en = json.lastIndexOf("]");
            if (en == -1) json = json.substring(begin + 1);
            else json = json.substring(json.indexOf("[") + 1, json.lastIndexOf("]")).trim();
        }

        String[] strings = collectionSplit(json);

        if (Arrays.stream(strings).anyMatch(s -> s.contains("["))) {
            result.add(parseObject(json, clazz));
        } else {
            if (json.contains("{")) {
                String[] objects = json.split("},");
                for (int i = 0; i < objects.length; i++) {
                    objects[i] = objects[i].trim();
                    objects[i] = addParenthesis(objects[i]);
                    if (!objects[i].endsWith("}")) {
                        objects[i] = objects[i] + "\n}";
                    }
                    result.add(parseObject(objects[i], clazz));
                }
            }
        }

        return result;
    }

    private Map<Object, Object> parseMap(String json, Class<?>[] classes) {
        Map<Object, Object> result = new HashMap<>();
        json = json.substring(json.indexOf("{") + 1, json.lastIndexOf("}"));
        String[] lines = json.split(",");
        for (String line : lines) {
            Node node = createNodeFromLine(line);
            Object key = createObject(node.key, classes[0]);
            Object value = createObject(node.value.toString(), classes[1]);
            result.put(key, value);
        }
        return result;
    }

    private Object createObject(String value, Class<?> clazz) {
        String className = clazz.getSimpleName();
        Object result = null;
        if (className.equals("String")) result = value;
        if (className.equals("UUID")) result = UUID.fromString(value);
        if (JsonUtils.isDigit(className)) result = createDigit(value, clazz);
        if (JsonUtils.isBigDigit(className)) result = createBigDigit(value, clazz);
        return result;
    }

    private Object createBigDigit(String value, Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor(String.class).newInstance(value);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                 NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Object createDigit(String value, Class<?> clazz) {
        Object o = new Object();
        try {
            return clazz.getDeclaredMethod("valueOf", String.class).invoke(o, value);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    private Object createDate(Object value, Class<?> clazz) {
        return switch (clazz.getSimpleName()) {
            case "LocalDate" -> LocalDate.parse(value.toString());
            case "LocalDateTime" -> LocalDateTime.parse(value.toString());
            case "OffsetTime" -> OffsetTime.parse(value.toString());
            case "OffsetDateTime" -> OffsetDateTime.parse(value.toString());
            default -> null;
        };
    }

    private Node createNodeFromLine(String line) {
        Node node = new Node();
        if (isDateLine(line)) {
            node.key = line.substring(0, line.indexOf(":")).replace("\"", "").trim();
            node.value = line.substring(line.indexOf(":") + 1).replace("\"", "").trim();
            return node;
        }
        String[] parts = line.split(":");
        node.key = parts[0].trim().replaceAll("\"", "");
        node.value = parts[1].trim().replaceAll("\"", "");
        return node;
    }

    private Node createNodeFromCollection(String json, String line, Class<?> clazz) {
        try {
            String key = line.split(":")[0].trim().replaceAll("\"", "");
            Class<?> type = clazz.getDeclaredField(key).getType();
            String collectionName = type.getSimpleName();

            String t = clazz.getDeclaredField(key).getGenericType().toString();
            Class<?> genericClass = selectGeneric(t);
            Collection<Object> value = parseCollection(json, genericClass, collectionName);

            Node node = new Node();
            node.key = key;
            node.value = value;
            return node;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    private Node createNodeFromObject(String json, String line, Class<?> clazz) {
        try {
            Node node = new Node();
            String key = line.split(":")[0].trim().replaceAll("\"", "");
            Class<?> type = clazz.getDeclaredField(key).getType();

            node.key = key;

            if (isMap(type.getSimpleName())) {
                String t = clazz.getDeclaredField(key).getGenericType().toString();
                Class<?>[] classes = selectGenericFromMap(t);

                node.value = parseMap(json, classes);
            } else {
                node.value = parseObject(json, clazz);
            }
            return node;
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }

    }

    private boolean isDateLine(String line) {
        Pattern pattern = Pattern.compile("\"[\\d-]*T\\d*:\\d*:\\d*.\\d*\\+\\d{2}:\\d{2}\"");
        Matcher matcher = pattern.matcher(line);
        return matcher.find();
    }

    private Object getInstanceOfClass(Class<?> clazz) {
        try {
            if (clazz.getSimpleName().equals("Map")) return new HashMap<>();
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            System.out.println("Can't instantiate class " + clazz.getName());
            throw new RuntimeException(e);
        }
    }

    private void setValueToField(Field field, Object value, Object instance) {
        field.setAccessible(true);
        String simpleName = field.getType().getSimpleName();
        try {
            if (simpleName.equals("UUID")) {
                field.set(instance, UUID.fromString((String) value));
            } else if (simpleName.equals("String")) {
                field.set(instance, value);
            } else if (JsonUtils.isDigit(simpleName)) {
                Object result = field.getType()
                        .getDeclaredMethod("valueOf", String.class).invoke(instance, value.toString());
                field.set(instance, result);
            } else if (JsonUtils.isDate(simpleName)) {
                field.set(instance, createDate(value, field.getType()));
            } else {
                field.set(instance, value);
            }

        } catch (IllegalArgumentException | IllegalAccessException | NoSuchMethodException |
                 InvocationTargetException e) {
            System.out.println("Can't set value to field " + field.getName());
            throw new RuntimeException(e);
        }
    }

    private boolean isField(String line) {
        return !(line.contains("{") || line.contains("}") || line.contains("[") || line.contains("]"));
    }

    private boolean isObject(String line) {
        return line.contains("{");
    }

    private boolean isCollection(String line) {
        return line.contains("[");
    }

    private boolean isMap(String className) {
        return className.equals("Map");
    }

    private String[] collectionSplit(String json) {
        Pattern pattern = Pattern.compile("\\[[\\s\\D\\d]*]");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String group = matcher.group();
            group = group.substring(group.indexOf("[") + 1);
            String s = json.replace(group, "");
            return s.split(",");
        }
        return json.split(",");
    }

    private String[] splitByFields(String json) {
        Pattern pattern = Pattern.compile("\\{[\\s\\D\\d]*}");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            String group = matcher.group();
            group = group.substring(group.indexOf("{") + 1);
            String s = json.replace(group, "");
            return s.split(",");
        } else {
            return json.split(",");
        }
    }

    private Class<?> selectGeneric(String line) {
        String className = line.substring(line.indexOf("<") + 1, line.indexOf(">"));
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?>[] selectGenericFromMap(String line) {
        String[] classNames = line.substring(line.indexOf("<") + 1, line.indexOf(">")).split(",");
        Class<?>[] result = new Class<?>[classNames.length];
        for (int i = 0; i < classNames.length; i++) {
            try {
                result[i] = Class.forName(classNames[i].trim());
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    private String addParenthesis(String line) {

        int openCount = 0;
        int closeCount = 0;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') openCount++;
            else if (c == '}') closeCount++;
        }

        if (openCount > closeCount) {
            line = line + "\n}".repeat(Math.max(0, openCount - closeCount));
        }
        return line;
    }

    private static class Node {

        String key;
        Object value;
    }

}