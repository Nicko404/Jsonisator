package ru.clevertec.jsonisator.model;

import lombok.Data;

@Data
public class Model {

    private String name;
    private InnerModel innerModel;
}
