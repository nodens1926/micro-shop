package com.example.warehouse_service.repository;

import com.example.warehouse_service.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {
    List<Reservation> findByOrderId(UUID orderId);
    List<Reservation> findByOrderIdAndStatus(UUID orderId, String status);
}