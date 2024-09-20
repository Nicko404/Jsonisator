package ru.clevertec.jsonisator.model;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class Order {

    private UUID id;
    private List<Product> products = new ArrayList<>();
    private OffsetDateTime createdDate;

    public void addProduct(Product product) {
        products.add(product);
    }
}
