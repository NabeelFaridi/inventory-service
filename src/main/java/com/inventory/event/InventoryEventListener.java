package com.inventory.event;

import com.inventory.entity.InventoryEvent;
import com.inventory.repository.InventoryEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventListener {

    private final InventoryEventRepository eventRepository;

    /**
     * AFTER_COMMIT means this only runs if the triggering transaction committed.
     * A rolled-back stock update therefore produces no audit row — correct behaviour.
     *
     * REQUIRES_NEW is required: by AFTER_COMMIT the original transaction has already committed,
     * so a plain save() would join it and never be flushed — the audit row would be silently lost.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStockChanged(StockChangedEvent event) {
        InventoryEvent row = InventoryEvent.builder()
                .productId(event.productId())
                .eventType(event.eventType())
                .quantityChange(event.quantityChange())
                .userId(event.userId())
                .timestamp(Instant.now())
                .build();

        eventRepository.save(row);

        log.info("Inventory event persisted: product={} type={} delta={} user={}",
                event.productId(), event.eventType(), event.quantityChange(), event.userId());
    }
}
