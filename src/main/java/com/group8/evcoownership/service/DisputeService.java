package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.DisputeCreateRequestDTO;
import com.group8.evcoownership.dto.DisputeResolveRequestDTO;
import com.group8.evcoownership.dto.DisputeResponseDTO;
import com.group8.evcoownership.entity.Dispute;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.enums.DisputeStatus;
import com.group8.evcoownership.enums.DisputeType;
import com.group8.evcoownership.repository.DisputeRepository;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class DisputeService {

    private final DisputeRepository disputeRepository;
    private final OwnershipGroupRepository groupRepository;
    private final UserRepository userRepository;
    private final OwnershipShareRepository ownershipShareRepository;

    /**
     * Tạo tranh chấp mới (CO_OWNER)
     */
    public DisputeResponseDTO create(DisputeCreateRequestDTO req, String username) {
        User creator = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        OwnershipGroup group = groupRepository.findById(req.getGroupId())
                .orElseThrow(() -> new EntityNotFoundException("Group not found"));

        // Kiểm tra user có phải là member của group không
        boolean isMember = ownershipShareRepository
                .findById_UserIdAndGroup_GroupId(creator.getUserId(), req.getGroupId())
                .isPresent();

        if (!isMember) {
            throw new IllegalStateException("You must be a member of the group to create a dispute.");
        }

        Dispute dispute = Dispute.builder()
                .group(group)
                .createdBy(creator)
                .disputeType(req.getDisputeType())
                .status(DisputeStatus.OPEN)
                .title(req.getTitle())
                .description(req.getDescription())
                .build();

        disputeRepository.save(dispute);
        return mapToDTO(dispute);
    }

    /**
     * Lấy danh sách tranh chấp với lọc (STAFF/ADMIN)
     */
    public Page<DisputeResponseDTO> getFiltered(
            DisputeStatus status,
            DisputeType disputeType,
            Long groupId,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Dispute> disputes = disputeRepository.findByFiltersOrdered(
                status, disputeType, groupId, from, to, pageable
        );

        return disputes.map(this::mapToDTO);
    }

    /**
     * Lấy danh sách tranh chấp chờ xử lý (OPEN)
     */
    public List<DisputeResponseDTO> getPendingDisputes() {
        return disputeRepository.findByStatusOrderByCreatedAtDesc(DisputeStatus.OPEN)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy chi tiết một tranh chấp
     */
    public DisputeResponseDTO getOne(Long id) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dispute not found"));
        return mapToDTO(dispute);
    }

    /**
     * Lấy tranh chấp của user (CO_OWNER)
     */
    public List<DisputeResponseDTO> getMyDisputes(String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        return disputeRepository.findByCreatedBy_UserId(user.getUserId())
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Lấy tranh chấp theo nhóm
     */
    public List<DisputeResponseDTO> getDisputesByGroup(Long groupId) {
        return disputeRepository.findByGroup_GroupId(groupId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Giải quyết tranh chấp (STAFF/ADMIN)
     */
    public DisputeResponseDTO resolveDispute(Long id, DisputeResolveRequestDTO req, String username) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dispute not found"));

        if (dispute.getStatus() != DisputeStatus.OPEN) {
            throw new IllegalStateException("Only OPEN disputes can be resolved.");
        }

        if (req.getStatus() == DisputeStatus.OPEN) {
            throw new IllegalArgumentException("Cannot set status to OPEN when resolving.");
        }

        User resolver = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        dispute.setStatus(req.getStatus());
        dispute.setResolvedBy(resolver);
        dispute.setResolutionNote(req.getResolutionNote());
        dispute.setResolvedAt(LocalDateTime.now());
        dispute.setUpdatedAt(LocalDateTime.now());

        disputeRepository.save(dispute);
        return mapToDTO(dispute);
    }

    /**
     * Cập nhật trạng thái tranh chấp (STAFF/ADMIN)
     */
    public DisputeResponseDTO updateStatus(Long id, DisputeStatus status, String username) {
        Dispute dispute = disputeRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Dispute not found"));

        User updater = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        dispute.setStatus(status);
        dispute.setUpdatedAt(LocalDateTime.now());

        // Nếu chuyển sang RESOLVED hoặc REJECTED, ghi nhận người giải quyết
        if (status == DisputeStatus.RESOLVED || status == DisputeStatus.REJECTED) {
            if (dispute.getResolvedBy() == null) {
                dispute.setResolvedBy(updater);
            }
            if (dispute.getResolvedAt() == null) {
                dispute.setResolvedAt(LocalDateTime.now());
            }
        }

        disputeRepository.save(dispute);
        return mapToDTO(dispute);
    }

    /**
     * Mapping helper
     */
    private DisputeResponseDTO mapToDTO(Dispute d) {
        return DisputeResponseDTO.builder()
                .id(d.getId())
                .groupId(d.getGroup().getGroupId())
                .groupName(d.getGroup().getGroupName())
                .createdById(d.getCreatedBy().getUserId())
                .createdByName(d.getCreatedBy().getFullName())
                .createdByEmail(d.getCreatedBy().getEmail())
                .disputeType(d.getDisputeType())
                .status(d.getStatus())
                .title(d.getTitle())
                .description(d.getDescription())
                .resolvedById(d.getResolvedBy() != null ? d.getResolvedBy().getUserId() : null)
                .resolvedByName(d.getResolvedBy() != null ? d.getResolvedBy().getFullName() : null)
                .resolutionNote(d.getResolutionNote())
                .resolvedAt(d.getResolvedAt())
                .createdAt(d.getCreatedAt())
                .updatedAt(d.getUpdatedAt())
                .build();
    }
}

