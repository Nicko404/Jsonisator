package ru.clevertec.jsonisator.utils;

import java.util.List;

public class JsonUtils {

    private final static List<String> CLASS_TYPE_NOT_NEED_QUOTES =
            List.of("Short", "Integer", "Long", "Float", "Double");

    private final static List<String> CLASS_TYPE_OF_BIG_DIGIT =
            List.of("BigInteger", "BigDecimal");

    private final static List<String> CLASS_TYPE_OF_COLLECTIONS =
            List.of("List", "Set");

    private final static List<String> CLASS_TYPE_OF_DATES =
            List.of("Date", "Time", "Timestamp", "LocalDate", "LocalDateTime", "OffsetDateTime");

    private JsonUtils() {
    }

    public static boolean isDigit(String classType) {
        return CLASS_TYPE_NOT_NEED_QUOTES.contains(classType);
    }

    public static boolean isCollection(String classType) {
        return CLASS_TYPE_OF_COLLECTIONS.contains(classType);
    }

    public static boolean isBigDigit(String classType) {
        return CLASS_TYPE_OF_BIG_DIGIT.contains(classType);
    }

    public static boolean isMap(String classType) {
        return classType.equals("Map");
    }

    public static boolean isDate(String classType) {
        return CLASS_TYPE_OF_DATES.contains(classType);
    }

    public static boolean isCustomObject(String classType, Class<?> clazz) {
        return !(isDigit(classType) || isCollection(classType) || isMap(classType) || isDate(classType) ||
                isBigDigit(classType) || clazz.isPrimitive() || classType.equals("String") || classType.equals("UUID"));
    }

    public static String tabulation(int tabulationCount) {
        return " ".repeat(Math.max(0, tabulationCount));
    }
}
