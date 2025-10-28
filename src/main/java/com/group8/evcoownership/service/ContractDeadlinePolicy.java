package com.group8.evcoownership.service;

import com.group8.evcoownership.entity.Contract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ContractDeadlinePolicy {

    @Value("${contract.deposit.deadline.minutes:5}")
    private long depositDeadlineMinutes;

    public LocalDateTime computeDepositDeadline(Contract contract) {
        LocalDateTime reference = contract.getUpdatedAt() != null
                ? contract.getUpdatedAt()
                : (contract.getCreatedAt() != null ? contract.getCreatedAt() : LocalDateTime.now());
        return reference.plusMinutes(depositDeadlineMinutes);
    }
}


