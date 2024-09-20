package ru.clevertec.jsonisator.model;

import lombok.Data;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Data
public class Product {

    private UUID id;
    private String name;
    private Double price;
    private Map<UUID, BigDecimal> stock = new HashMap<>();

    public void addStock(UUID id, BigDecimal amount) {
        stock.put(id, amount);
    }
}
