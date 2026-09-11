package com.gachisa.order.repository;

import com.gachisa.order.entity.SavedDeliveryAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedDeliveryAddressRepository extends JpaRepository<SavedDeliveryAddress, Long> {
    List<SavedDeliveryAddress> findAllByBuyerIdOrderByUpdatedAtDesc(Long buyerId);
    Optional<SavedDeliveryAddress> findByIdAndBuyerId(Long id, Long buyerId);
}
