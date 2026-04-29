package com.inventory.event;

import com.inventory.entity.InventoryEvent;

/**
 * Published via ApplicationEventPublisher whenever product quantity changes.
 * Kept as a plain record — no need to extend ApplicationEvent in Spring 6+.
 */
public record StockChangedEvent(
        Long productId,
        InventoryEvent.EventType eventType,
        int quantityChange,
        Long userId
) {}
