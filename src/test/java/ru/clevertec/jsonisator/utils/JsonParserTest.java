package ru.clevertec.jsonisator.utils;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.clevertec.jsonisator.mapper.JsonMapper;
import ru.clevertec.jsonisator.model.Customer;
import ru.clevertec.jsonisator.model.Order;
import ru.clevertec.jsonisator.model.Product;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;


class JsonParserTest {

    private Customer customer;
    private ObjectMapper jacksonMapper;
    private JsonMapper myMapper;

    @BeforeEach
    public void init() {
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

        customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setFirstName("first name");
        customer.setLastName("last name");
        customer.setBirthDate(LocalDate.now().minusYears(20));
        customer.addOrder(order);

        jacksonMapper = new ObjectMapper();
        jacksonMapper.registerModule(new JavaTimeModule());
        jacksonMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        jacksonMapper.disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);

        myMapper = new JsonMapper();
    }

    @Test
    public void whenParseToObject() throws IOException {
        String jacksonJson = jacksonMapper.writeValueAsString(customer);

        String myJson = myMapper.objectToJson(customer);

        assertEquals(jacksonMapper.readTree(jacksonJson), jacksonMapper.readTree(myJson));
    }

    @Test
    public void whenParseToJson() throws IOException {

        String path = "src/test/resources/data.json";
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String json = reader.lines().collect(Collectors.joining());

        reader.close();

        Customer jacksonCustomer = jacksonMapper.readValue(json, Customer.class);
        Customer myCustomer = myMapper.parseObject(json, Customer.class);

        assertEquals(jacksonCustomer, myCustomer);
    }
}