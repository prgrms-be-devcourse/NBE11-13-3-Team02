package com.gachisa.order.service;

import com.gachisa.global.exception.CustomException;
import com.gachisa.global.exception.ErrorCode;
import com.gachisa.global.util.TimeProvider;
import com.gachisa.order.dto.SavedDeliveryAddressRequest;
import com.gachisa.order.dto.SavedDeliveryAddressResponse;
import com.gachisa.order.entity.SavedDeliveryAddress;
import com.gachisa.order.repository.SavedDeliveryAddressRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavedDeliveryAddressService {

    private final SavedDeliveryAddressRepository repository;
    private final TimeProvider timeProvider;

    @Transactional(readOnly = true)
    public List<SavedDeliveryAddressResponse> getMyAddresses(Long buyerId) {
        return repository.findAllByBuyerIdOrderByUpdatedAtDesc(buyerId).stream()
                .map(SavedDeliveryAddressResponse::from)
                .toList();
    }

    @Transactional
    public SavedDeliveryAddressResponse create(Long buyerId, SavedDeliveryAddressRequest request) {
        LocalDateTime now = timeProvider.now();
        SavedDeliveryAddress saved = SavedDeliveryAddress.builder()
                .buyerId(buyerId)
                .addressName(request.addressName())
                .recipientName(request.recipientName())
                .recipientPhone(request.recipientPhone())
                .zipCode(request.zipCode())
                .address(request.address())
                .addressDetail(request.addressDetail())
                .deliveryRequest(request.deliveryRequest())
                .createdAt(now)
                .updatedAt(now)
                .build();
        return SavedDeliveryAddressResponse.from(repository.save(saved));
    }

    @Transactional
    public SavedDeliveryAddressResponse update(Long id, Long buyerId,
                                                SavedDeliveryAddressRequest request) {
        SavedDeliveryAddress saved = getOwned(id, buyerId);
        saved.update(request.addressName(), request.recipientName(), request.recipientPhone(),
                request.zipCode(), request.address(), request.addressDetail(),
                request.deliveryRequest(), timeProvider.now());
        return SavedDeliveryAddressResponse.from(saved);
    }

    @Transactional
    public void delete(Long id, Long buyerId) {
        repository.delete(getOwned(id, buyerId));
    }

    private SavedDeliveryAddress getOwned(Long id, Long buyerId) {
        return repository.findByIdAndBuyerId(id, buyerId)
                .orElseThrow(() -> new CustomException(ErrorCode.DELIVERY_ADDRESS_NOT_FOUND));
    }
}
