package com.inventory.repository;

import com.inventory.entity.InventoryEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InventoryEventRepository extends JpaRepository<InventoryEvent, Long> {
    List<InventoryEvent> findByProductIdOrderByTimestampDesc(Long productId);
}
