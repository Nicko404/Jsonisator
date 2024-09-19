package ru.clevertec.jsonisator.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Model {

    private Long id;


    private List<Product> products = new ArrayList<>();

    public void addProduct(Product product) {
        products.add(product);
    }
}
