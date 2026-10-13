package com.inventory.service;

import com.inventory.dto.ProductRequest;
import com.inventory.dto.ProductResponse;
import com.inventory.entity.InventoryEvent;
import com.inventory.entity.Product;
import com.inventory.event.StockChangedEvent;
import com.inventory.exception.DuplicateSkuException;
import com.inventory.exception.ResourceNotFoundException;
import com.inventory.mapper.ProductMapper;
import com.inventory.repository.ProductRepository;
import com.inventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRepository userRepository;

    public List<ProductResponse> findAll() {
        return productRepository.findAll().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    public ProductResponse findById(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        Product saved = productRepository.save(productMapper.toEntity(request));

        eventPublisher.publishEvent(new StockChangedEvent(
                saved.getId(),
                InventoryEvent.EventType.STOCK_SET,
                saved.getQuantity(),
                currentUserId()
        ));

        return productMapper.toResponse(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getSku().equals(request.getSku()) && productRepository.existsBySku(request.getSku())) {
            throw new DuplicateSkuException(request.getSku());
        }

        int oldQty = product.getQuantity();
        productMapper.updateEntity(request, product);
        // Flush so the @Version increment is reflected in the response
        Product saved = productRepository.saveAndFlush(product);
        int newQty = saved.getQuantity();

        if (newQty != oldQty) {
            int delta = Math.abs(newQty - oldQty);
            InventoryEvent.EventType type = newQty > oldQty
                    ? InventoryEvent.EventType.STOCK_INCREASE
                    : InventoryEvent.EventType.STOCK_DECREASE;

            eventPublisher.publishEvent(new StockChangedEvent(saved.getId(), type, delta, currentUserId()));
        }

        return productMapper.toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product not found with id: " + id);
        }
        productRepository.deleteById(id);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return userRepository.findByUsername(auth.getName())
                .map(com.inventory.entity.User::getId)
                .orElse(null);
    }
}
