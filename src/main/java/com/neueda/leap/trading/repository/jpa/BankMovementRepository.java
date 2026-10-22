package com.neueda.leap.trading.repository.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.neueda.leap.trading.domain.BankMovement;

public interface BankMovementRepository extends JpaRepository<BankMovement, Integer> {
    List<BankMovement> findByBankIdInOrderByMovementDateDesc(List<Integer> bankIds);
}
