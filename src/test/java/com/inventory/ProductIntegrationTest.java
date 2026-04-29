package com.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.dto.ProductRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration test against H2 (dev profile).
 * Exercises: register → login → create product → read product.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class ProductIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void obtainToken() throws Exception {
        // Register a fresh user each test run (H2 resets between test class runs)
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"integrationuser\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getContentAsString();

        token = objectMapper.readTree(body).get("token").asText();
    }

    @Test
    void createProduct_thenRetrieve_returnsCorrectData() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .sku("INT-SKU-001")
                .name("Integration Widget")
                .description("Created in integration test")
                .price(BigDecimal.valueOf(19.99))
                .quantity(50)
                .build();

        // Create — requires auth
        String createBody = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("INT-SKU-001"))
                .andExpect(jsonPath("$.quantity").value(50))
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(createBody).get("id").asLong();

        // Retrieve — public, no token needed
        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("INT-SKU-001"))
                .andExpect(jsonPath("$.name").value("Integration Widget"))
                .andExpect(jsonPath("$.version").value(0));
    }
}
