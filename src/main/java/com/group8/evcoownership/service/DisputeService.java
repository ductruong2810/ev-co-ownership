package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.entity.Dispute;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.DisputeStatus;
import com.group8.evcoownership.enums.RelatedEntityType;
import com.group8.evcoownership.repository.DisputeRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DisputeService {

    private final DisputeRepository disputeRepo;
    private final SharedFundRepository fundRepo;
    private final UserRepository userRepo;

    /* ===== CREATE ===== */
    @Transactional
    public DisputeResponse create(DisputeCreateRequest req) {
        SharedFund fund = fundRepo.findById(req.fundId())
                .orElseThrow(() -> new EntityNotFoundException("SharedFund not found: " + req.fundId()));

        Dispute d = Dispute.builder()
                .fund(fund)
                .createdBy(req.createdBy())
                .description(req.description())
                .disputedAmount(req.amount())
                .disputeType(req.disputeType().name()) // enum -> String
                .status(DisputeStatus.OPEN.name())
                .build();

        d = disputeRepo.save(d);
        return toDto(d);
    }

    /* ===== READ ===== */
    @Transactional(readOnly = true)
    public DisputeResponse getById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<DisputeResponse> list(Integer page, Integer size, Long fundId, String status) {
        Pageable pageable = PageRequest.of(page == null ? 0 : page, size == null ? 20 : size,
                Sort.by("createdAt").descending());
        Page<Dispute> p;
        if (fundId != null && status != null) {
            p = disputeRepo.findByFund_FundIdAndStatusIgnoreCase(fundId, status, pageable);
        } else if (fundId != null) {
            p = disputeRepo.findByFund_FundId(fundId, pageable);
        } else if (status != null) {
            p = disputeRepo.findByStatusIgnoreCase(status, pageable);
        } else {
            p = disputeRepo.findAll(pageable);
        }
        return p.map(this::toDto);
    }

    /* ===== UPDATE (staff info) ===== */
    @Transactional
    public DisputeResponse staffUpdate(Long id, DisputeStaffUpdateRequest req, Long staffUserId) {
        // TODO: kiểm tra role staffUserId (STAFF/ADMIN)
        Dispute d = getOrThrow(id);

        // enum -> String
        RelatedEntityType relType = req.relatedEntityType();
        d.setRelatedEntityType(relType.name());
        d.setRelatedEntityId(req.relatedEntityId());
        d.setResolution(req.resolution());
        if (req.resolutionAmount() != null) d.setResolutionAmount(req.resolutionAmount());

        // (tuỳ chọn) verify tồn tại theo loại:
        // switch (relType) { case BOOKING -> if(!bookingRepo.existsById(...)) throw ... ; ... }

        return toDto(d);
    }

    /* ===== UPDATE STATUS: OPEN -> RESOLVED/REJECTED ===== */
    @Transactional
    public DisputeResponse updateStatus(Long id, DisputeStatusUpdateRequest req, Long staffUserId) {
        // TODO: kiểm tra role staffUserId (STAFF/ADMIN)
        Dispute d = getOrThrow(id);

        DisputeStatus from = DisputeStatus.from(d.getStatus());
        DisputeStatus to   = DisputeStatus.valueOf(req.status());

        if (from != DisputeStatus.OPEN || to == DisputeStatus.OPEN) {
            throw new IllegalStateException("Invalid transition: " + from + " -> " + to);
        }

        // Bảo đảm đã có đủ 3 trường từ bước staffUpdate
        if (d.getRelatedEntityType() == null || d.getRelatedEntityId() == null ||
                d.getResolution() == null || d.getResolution().isBlank()) {
            throw new IllegalStateException("Staff must set relatedEntityType, relatedEntityId, and resolution before closing.");
        }

        User resolver = userRepo.findById(req.resolvedById())
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + req.resolvedById()));
        // TODO: enforce role resolver là STAFF/ADMIN

        if (req.resolutionNote() != null && !req.resolutionNote().isBlank()) {
            String existing = d.getResolution();
            String note = req.resolutionNote().trim();
            d.setResolution(
                    (existing == null || existing.isBlank())
                            ? note
                            : existing + "\n---\n" + note
            );
        }

        d.setResolvedBy(resolver);
        if (d.getResolvedAt() == null) d.setResolvedAt(LocalDateTime.now());
        d.setStatus(to.name());

        return toDto(d);
    }

    /* ===== DELETE ===== */
    @Transactional
    public void delete(Long id) {
        if (!disputeRepo.existsById(id)) throw new EntityNotFoundException("Dispute not found: " + id);
        disputeRepo.deleteById(id);
    }

    /* ===== helpers ===== */
    private Dispute getOrThrow(Long id) {
        return disputeRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dispute not found: " + id));
    }

    private DisputeResponse toDto(Dispute d) {
        Long resolvedById = d.getResolvedBy() == null ? null : d.getResolvedBy().getUserId();
        return new DisputeResponse(
                d.getId(),
                d.getFund().getFundId(),
                d.getCreatedBy(),
                d.getDisputeType(),
                d.getRelatedEntityType(),
                d.getRelatedEntityId(),
                d.getDescription(),
                d.getDisputedAmount(),
                d.getResolution(),
                d.getResolutionAmount(),
                d.getStatus(),
                resolvedById,
                d.getCreatedAt(),
                d.getResolvedAt()
        );
    }
}

