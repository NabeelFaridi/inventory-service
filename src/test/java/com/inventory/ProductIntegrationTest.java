package com.inventory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inventory.dto.ProductRequest;
import com.inventory.entity.InventoryEvent;
import com.inventory.repository.InventoryEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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

    @Autowired InventoryEventRepository eventRepository;

    private String username;
    private String token;

    @BeforeEach
    void obtainToken() throws Exception {
        // Unique user per test — the H2 database is shared across tests in this class
        username = "user-" + UUID.randomUUID().toString().substring(0, 8);
        String body = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
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

    @Test
    void stockChanges_areRecordedInAuditTrail() throws Exception {
        ProductRequest request = ProductRequest.builder()
                .sku("INT-SKU-AUDIT")
                .name("Audited Widget")
                .price(BigDecimal.valueOf(5.00))
                .quantity(100)
                .build();

        String createBody = mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long productId = objectMapper.readTree(createBody).get("id").asLong();

        request.setQuantity(60);
        mockMvc.perform(put("/api/products/" + productId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1));

        // Listener runs AFTER_COMMIT on the request thread, so rows exist once the call returns
        List<InventoryEvent> events = eventRepository.findByProductIdOrderByTimestampDesc(productId);
        assertThat(events).extracting(InventoryEvent::getEventType, InventoryEvent::getQuantityChange)
                .containsExactlyInAnyOrder(
                        tuple(InventoryEvent.EventType.STOCK_DECREASE, 40),
                        tuple(InventoryEvent.EventType.STOCK_SET, 100));
        assertThat(events).allSatisfy(e -> assertThat(e.getUserId()).isNotNull());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void createProduct_malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/api/products")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
                .andExpect(status().isBadRequest());
    }
}
