package ru.clevertec.jsonisator.model;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class Customer {

    private UUID id;
    private String firstName;
    private String lastName;
    private LocalDate birthDate;
    private List<Order> orders = new ArrayList<>();

    public void addOrder(Order order) {
        orders.add(order);
    }
}
