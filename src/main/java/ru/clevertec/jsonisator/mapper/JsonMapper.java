package ru.clevertec.jsonisator.mapper;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import ru.clevertec.jsonisator.utils.JsonUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            result.append(this.write(o, tabulationCount + 2));
            if (i < objects.length - 1) result.append(",\n");
        }

        result.append("\n")
                .append(JsonUtils.tabulation(tabulationCount))
                .append("]");


        return result.toString();
    }

    @SneakyThrows
    private String generateObjectLine(String fieldName, Object value, int tabulationCount) {
        StringBuilder result = new StringBuilder();
        result.append(JsonUtils.tabulation(tabulationCount))
                .append("\"")
                .append(fieldName)
                .append("\": ")
                .append(write(value, tabulationCount).trim());

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
            boolean isString = v.toString().matches("[\\d]*.[\\d]");
            result.append(generateSingleLine(k.toString(), v.toString(), !isString, tabulationCount + 2));
            result.append(",\n");
        });

        result.deleteCharAt(result.length() - 2);

        result.append(JsonUtils.tabulation(tabulationCount))
                .append("}");
        return result.toString();
    }

    public void close() {
        try {
            writer.close();
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }
}