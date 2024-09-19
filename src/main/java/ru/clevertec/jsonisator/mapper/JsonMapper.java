package ru.clevertec.jsonisator.mapper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import ru.clevertec.jsonisator.utils.JsonUtils;
import ru.clevertec.jsonisator.utils.Node;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JsonMapper<T> {

    private final String path;
    private final boolean append;
    private List<T> buffer = new ArrayList<>();
    private BufferedWriter writer;

    public void write(T t) {
        buffer.add(t);
    }

    @SneakyThrows
    public void flush() {
        StringBuilder result = new StringBuilder();
        if (buffer.size() > 1) {
            result.append("[\n");
            for (T t : buffer) {
                if (result.length() > 2) result.append(",\n");
                result.append(write(t, 2));
            }
            result.append("\n]");
        } else {
            result.append(write(buffer.getFirst(), 0));
        }
        writer = new BufferedWriter(new FileWriter(path, append));
        writer.write(result.toString());
        writer.flush();
        buffer = new ArrayList<>();
    }

    @SneakyThrows
    private String write(Object object, int tabulationCount) {
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

            if (isMap) {
                result.append(generateLineFromMap(fieldName, fieldValue, tabulationCount + 2));
            } else if (isCollection) {
                result.append(generateLineFromList(fieldName, fieldValue, tabulationCount + 2));
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
            result.append(this.write(o, tabulationCount + 2));
            if (i < objects.length - 1) result.append(",\n");
        }

        result.append("\n")
                .append(JsonUtils.tabulation(tabulationCount))
                .append("]");


        return result.toString();
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
            result.append(generateSingleLine(k.toString(), v.toString(), true, tabulationCount + 2));
            result.append(",\n");
        });

        result.deleteCharAt(result.length() - 2);

        result.append(JsonUtils.tabulation(tabulationCount))
                .append("}");
        return result.toString();
    }

    @SneakyThrows
    public T read(Class<T> tClass) {
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String json = reader.lines().collect(Collectors.joining("\n"));
        return parse(json, tClass);
    }

    @SneakyThrows
    private T parse(String json, Class<T> tClass) {
        List<String> lines = Arrays.stream(json.split("\n")).toList();
        T objectInstance = tClass.getDeclaredConstructor().newInstance();
        List<Field> fields = Arrays.stream(tClass.getDeclaredFields()).toList();
        for (String line : lines) {
            if (isTrash(line)) continue;
            if (isField(line)) {
                Node node = createNode(line);
                Optional<Field> fieldOptional = fields.stream()
                        .filter(f -> f.getName().equals(node.getKey()))
                        .findFirst();
                if (fieldOptional.isPresent()) {
                    Field field = fieldOptional.get();
                    field.setAccessible(true);
                    setValueToObject(objectInstance, node.getValue(), field);
                }
            } else {
                int start = json.indexOf(line);
                int end = json.lastIndexOf("]") + 1;
                String subJson = json.substring(start, end);
                Node node = createNodeNoValue(line);
                Optional<Field> fieldOptional = fields.stream()
                        .filter(f -> f.getName().equals(node.getKey()))
                        .findFirst();

                if (fieldOptional.isPresent()) {
                    Field field = fieldOptional.get();
                    field.setAccessible(true);
                    Object o = field.get(objectInstance);
                    System.out.println(o.getClass().getSimpleName());
                }
            }
        }
        return objectInstance;
    }


//    @SneakyThrows
//    public T read(Class<T> tclass) {
//        BufferedReader reader = new BufferedReader(new FileReader(path));
//        String json = reader.lines().collect(Collectors.joining("\n"));
//        Object result = selectLayout(json.substring(1, json.length() - 1), tclass);
//
//        return (T) result;
//    }
//
//    @SneakyThrows
//    private Object selectLayout(String data, Class<?> tClass) {
//        Field[] fields = tClass.getDeclaredFields();
//        Object object = createObjectFromClass(tClass);
//
//        for (Field field : fields) {
//            String fieldType = field.getType().getSimpleName();
//            String fieldName = field.getName();
//            String[] lines = data.split(",\n", fields.length);
//
//            for (String line : lines) {
//                if (line.contains(fieldName)) {
//                    if (line.contains("[") || line.contains("{")) {
//                        if (JsonUtils.isCollection(field.getType().getSimpleName()) ||
//                                JsonUtils.isMap(field.getType().getSimpleName())) {
//                            Pattern pattern = Pattern.compile("\\{[\\d\\D]*}");
//                            Matcher matcher = pattern.matcher(data);
//                            matcher.find();
//                            String subData = matcher.group();
//
//                            int firstIndex = subData.indexOf("{");
//                            int lstIndex = subData.lastIndexOf("}");
//
//                            subData = subData.substring(firstIndex + 1, lstIndex);
//
//                            if (JsonUtils.isMap(field.getType().getSimpleName())) continue;
//
//                            String genericType = JsonUtils.selectGenericFromClassName(field.getAnnotatedType().toString());
//                            Class<?> aClass = Class.forName(genericType);
//                            Object o = aClass.getDeclaredConstructor().newInstance();
//                            Object inO = selectLayout(subData, o.getClass());
//                            o.getClass().getDeclaredMethod("add", inO.getClass()).invoke(o, inO);
//                            continue;
//                        }
//                        Object o = selectLayout(line.substring(1, line.length() - 1), field.getType());
//                        field.setAccessible(true);
//                        field.set(object, o);
//                        continue;
//                    }
//                    String value = getValueFromJsonLine(line);
//                    field.setAccessible(true);
//                    setValueToObject(object, value, field);
//                    break;
//                }
//            }
//        }
//        return object;
//    }

    @SneakyThrows
    private void setValueToObject(Object object, String value, Field field) {
        String fieldTypeName = field.getType().getSimpleName();
        field.setAccessible(true);

        if (fieldTypeName.equals("UUID")) {
            field.set(object, UUID.fromString(value));
        } else if (fieldTypeName.equals("String")) {
            field.set(object, value);
        } else if (JsonUtils.isDigit(fieldTypeName)) {
            Object result = field.getType().getDeclaredMethod("valueOf", String.class).invoke(object, value);
            field.set(object, result);
        } else if (JsonUtils.isDate(fieldTypeName)) {
            Class<?> dateClass = field.getType();
            Object dateObject = dateClass.getDeclaredMethod("parse", CharSequence.class).invoke(object, value);
            field.set(object, dateObject);
        }
    }

    @SneakyThrows
    private Object createObjectFromClass(Class<?> tclass) {
        Object result = null;
        if (tclass.getSimpleName().equals("List")) {
            result = new ArrayList<>();
        } else if (tclass.getSimpleName().equals("Map")) {
            result = new HashMap<>();
        } else {
            result = tclass.getDeclaredConstructor().newInstance();
        }
        return result;
    }

    private String getValueFromJsonLine(String jsonLine) {
        String[] split = jsonLine.split(":");
        String value = split[1].trim();
        if (value.contains("\"")) value = value.substring(1, value.length() - 1);
        return value;
    }


    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    private boolean isField(String line) {
        return !(line.contains("[") || line.contains("]") || line.contains("{") || line.contains("}"));
    }

    private Node createNode(String line) {
        String[] parts = line.split(":");
        String key = parts[0].replaceAll("\"", "").trim();
        String value = parts[1].replaceAll("\"?,?", "").trim();
        Node node = new Node();
        node.setKey(key);
        node.setValue(value);
        return node;
    }

    private boolean isTrash(String line) {
//        return line.matches("[\\s]|[{}\\[\\]]") || line.matches("\\{}");
        return line.matches("\\s*[{}\\S\\s*]|\\s*],");
    }

    private Node createNodeNoValue(String line) {
        String[] parts = line.split(":");
        String key = parts[0].replaceAll("\"", "").trim();
        Node node = new Node();
        node.setKey(key);

        return node;
    }
}