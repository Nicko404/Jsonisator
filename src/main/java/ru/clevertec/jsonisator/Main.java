package ru.clevertec.jsonisator;

import lombok.SneakyThrows;
import ru.clevertec.jsonisator.mapper.JsonMapper;
import ru.clevertec.jsonisator.model.Customer;
import ru.clevertec.jsonisator.model.Model;
import ru.clevertec.jsonisator.model.Order;
import ru.clevertec.jsonisator.model.Product;
import ru.clevertec.jsonisator.utils.JsonParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) {
        write();
//        test();
    }

    public static void write() {
        JsonMapper<Customer> mapper = new JsonMapper<>("src/main/resources/result.json", false);

        Product product = new Product();
        product.setId(UUID.randomUUID());
        product.setName("product name");
        product.setPrice(12.3);
        product.addStock(UUID.randomUUID(), new BigDecimal(23));
        product.addStock(UUID.randomUUID(), new BigDecimal(54));

        Product product2 = new Product();
        product2.setId(UUID.randomUUID());
        product2.setName("product 2");
        product2.setPrice(32.1);
        product2.addStock(UUID.randomUUID(), new BigDecimal(23));
        product2.addStock(UUID.randomUUID(), new BigDecimal(54));

        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCreatedDate(OffsetDateTime.now());
        order.addProduct(product);
        order.addProduct(product2);

        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setFirstName("first name");
        customer.setLastName("last name");
        customer.setBirthDate(LocalDate.now().minusYears(20));
        customer.addOrder(order);

        mapper.write(customer);

        mapper.flush();
        mapper.close();
    }

    @SneakyThrows
    public static void test() {
        BufferedReader reader = new BufferedReader(new FileReader("src/main/resources/result.json"));
        String json = reader.lines().collect(Collectors.joining("\n"));
        JsonParser parser = new JsonParser();
        Object object = parser.parseObject(json, Customer.class);
        System.out.println(object);
    }
}
