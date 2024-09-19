package ru.clevertec.jsonisator.utils;


import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@RequiredArgsConstructor
public class JsonParser {

    public Object parseObject(String json, Class<?> clazz) {
        Map<String, Object> nodes = new HashMap<>();
        Object instance = getInstanceOfClass(clazz);

        int start = json.indexOf("{");
        int end = json.lastIndexOf("}");

        json = json.substring(start + 1, end).trim();

        String[] lines = split(json);

        try {
            for (String line : lines) {
                if (isField(line)) {
                    Node node = lineToNode(line);
                    nodes.put(node.key, node.value);
                } else if (isCollection(line)) {

                    String key = line.split(":")[0].trim().replaceAll("\"", "");
                    Class<?> type = clazz.getDeclaredField(key).getType();
                    String collectionName = type.getSimpleName();

                    String t = clazz.getDeclaredField(key).getGenericType().toString();
                    Class<?> genericClass = selectGeneric(t);
                    Collection<Object> value = parseCollection(json, genericClass, collectionName);
                    nodes.put(key, value);

                } else if (isObject(line)) {
                    String key = line.split(":")[0].trim().replaceAll("\"", "");
                    Class<?> type = clazz.getDeclaredField(key).getType();

                    if (isMap(type.getSimpleName())) {
                        String t = clazz.getDeclaredField(key).getGenericType().toString();
                        Class<?>[] classes = selectGenericFromMap(t);

                        Map<Object, Object> value = parseMap(json, classes);
                        nodes.put(key, value);
                    } else {
                        Object value = parseObject(json, type);
                        nodes.put(key, value);
                    }
                }
            }

            Set<String> keys = nodes.keySet();
            for (String key : keys) {
                Field field = clazz.getDeclaredField(key);
                setValueToField(field, nodes.get(key), instance);
            }
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
        return instance;
    }

    private Collection<Object> parseCollection(String json, Class<?> clazz, String collectionName) {
        Collection<Object> result = null;

        if (collectionName.equals("List")) result = new ArrayList<>();
        if (collectionName.equals("Set")) result = new HashSet<>();
        if (result == null) throw new RuntimeException();

        int start = json.indexOf("[");
        int end = json.lastIndexOf("]");

        if (end == -1) json = json.substring(start + 1);
        else {
            int begin = json.indexOf("[");
            int en = json.lastIndexOf("]");
            if (en == -1) json = json.substring(begin + 1);
            else json = json.substring(json.indexOf("[") + 1, json.lastIndexOf("]")).trim();
        }


        if (json.contains("{")) {
            String[] split = json.split("},");
            for (int i = 0; i < split.length; i++) {
                split[i] = split[i].trim();
                split[i] = addEnd(split[i]);
                if (!split[i].endsWith("}")) {
                    split[i] = split[i] + "\n}";
                }
                Object o = parseObject(split[i], clazz);
                result.add(o);
            }
        }
        return result;
    }

    public Map<Object, Object> parseMap(String json, Class<?>[] classes) {
        Map<Object, Object> result = new HashMap<>();
        json = json.substring(json.indexOf("{") + 1, json.lastIndexOf("}"));
        String[] lines = json.split(",");
        for (String line : lines) {
            Node node = lineToNode(line);
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
        Object result = null;
        if (clazz.getSimpleName().equals("LocalDate")) {
            result = LocalDate.parse(value.toString());
        }
        return result;
    }

    private Node lineToNode(String line) {
        Node node = new Node();
        String[] parts = line.split(":");
        node.key = parts[0].trim().replaceAll("\"", "");
        node.value = parts[1].trim().replaceAll("\"", "");
        return node;
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

    private String[] split(String json) {
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

    private String addEnd(String line) {

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
