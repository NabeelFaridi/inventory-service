package com.inventory.service;

import com.inventory.dto.ProductRequest;
import com.inventory.dto.ProductResponse;
import com.inventory.entity.Product;
import com.inventory.event.StockChangedEvent;
import com.inventory.exception.DuplicateSkuException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.ProductMapper;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository productRepository;
    @Mock ProductMapper productMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock UserRepository userRepository;
    @InjectMocks ProductService productService;

    @Test
    void create_newSku_savesAndPublishesEvent() {
        ProductRequest request = ProductRequest.builder()
                .sku("SKU-001").name("Widget").price(BigDecimal.valueOf(9.99)).quantity(100)
                .build();
        Product entity = Product.builder().sku("SKU-001").name("Widget").price(BigDecimal.valueOf(9.99)).quantity(100).build();
        Product saved  = Product.builder().id(1L).sku("SKU-001").name("Widget").price(BigDecimal.valueOf(9.99)).quantity(100).version(0L).build();
        ProductResponse response = ProductResponse.builder().id(1L).sku("SKU-001").build();

        when(productRepository.existsBySku("SKU-001")).thenReturn(false);
        when(productMapper.toEntity(request)).thenReturn(entity);
        when(productRepository.save(entity)).thenReturn(saved);
        when(productMapper.toResponse(saved)).thenReturn(response);

        ProductResponse result = productService.create(request);

        assertThat(result.getId()).isEqualTo(1L);
        verify(eventPublisher).publishEvent(any(StockChangedEvent.class));
    }

    @Test
    void create_duplicateSku_throwsAndNeverSaves() {
        ProductRequest request = ProductRequest.builder()
                .sku("SKU-001").name("Widget").price(BigDecimal.valueOf(9.99)).quantity(100)
                .build();

        when(productRepository.existsBySku("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("SKU-001");

        verify(productRepository, never()).save(any());
    }

    @Test
    void findById_existingId_returnsResponse() {
        Product product = Product.builder().id(1L).sku("SKU-001").build();
        ProductResponse response = ProductResponse.builder().id(1L).sku("SKU-001").build();

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productMapper.toResponse(product)).thenReturn(response);

        ProductResponse result = productService.findById(1L);

        assertThat(result.getSku()).isEqualTo("SKU-001");
    }

    @Test
    void findById_missingId_throwsResourceNotFoundException() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
