package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.MaintenanceAfterCheckOutCreateRequestDTO;
import com.group8.evcoownership.dto.MaintenanceResponseDTO;
import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.enums.MaintenanceCoverageType;
import com.group8.evcoownership.repository.MaintenanceRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class MaintenanceAfterCheckOutService {

    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;
    private final OwnershipShareRepository ownershipShareRepository;

    /**
     * Create
     * UPDATE WHEN PENDING
     */
    // =============== CREATE PERSONAL MAINTENANCE SAU CHECKOUT ===============
    public MaintenanceResponseDTO createAfterCheckOut(MaintenanceAfterCheckOutCreateRequestDTO req,
                                                      String technicianEmail) {

        User technician = userRepository.findByEmail(technicianEmail)
                .orElseThrow(() -> new EntityNotFoundException("Technician not found"));

        Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        User liableUser = userRepository.findById(req.getLiableUserId())
                .orElseThrow(() -> new EntityNotFoundException("Liable user not found"));

        // ===== CHECK: liableUser phải là co-owner của group sở hữu vehicle =====
        var group = vehicle.getOwnershipGroup();
        if (group == null) {
            throw new IllegalStateException("Vehicle does not belong to any ownership group.");
        }

        var shares = ownershipShareRepository
                .findByOwnershipGroup_GroupId(group.getGroupId());

        boolean isMember = shares.stream()
                .anyMatch(share -> share.getUser().getUserId().equals(liableUser.getUserId()));

        if (!isMember) {
            throw new IllegalArgumentException("Liable user must be a co-owner of this vehicle's group.");
        }
        // =======================================================================

        LocalDateTime now = LocalDateTime.now();

        Maintenance m = Maintenance.builder()
                .vehicle(vehicle)
                .requestedBy(technician)
                .liableUser(liableUser)
                .description(req.getDescription())
                .actualCost(req.getCost())
                .estimatedDurationDays(req.getEstimatedDurationDays())
                .status("PENDING")
                .coverageType(MaintenanceCoverageType.PERSONAL) // flow sau checkout = PERSONAL
                .requestDate(now)
                .createdAt(now)
                .updatedAt(now)
                .build();

        m = maintenanceRepository.save(m);

        // TODO (optional): tạo Payment PERSONAL cho maintenance này tại đây

        return mapToDTO(m);
    }

    /**
     * CO-OWNER
     * Get List
     */
    // =============== LIST CỦA CHÍNH NGƯỜI PHẢI TRẢ (CO-OWNER) ===============
    // =============== CO-OWNER ==============
    public List<MaintenanceResponseDTO> getMyLiabilities(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // goi repo de lay tat ca maintenance loai PERSONAL cua co-owner
        return maintenanceRepository
                .findByCoverageTypeAndLiableUser_UserIdOrderByRequestDateDesc(
                        MaintenanceCoverageType.PERSONAL,
                        user.getUserId()
                )
                .stream()
                .map(this::mapToDTO)
                .toList();
    }

    /**
     * STAFF/ADMIN
     * GET ONE DETAIL
     * GET ALL REQUESTED MAINTENANCE
     * UPDATE PENDING -> APPROVED/REJECTED
     * APPROVED -> FUNDED
     * UPDATE FUNDED -> IN_PROGRESS
     * UPDATE IN_PROGRESS -> COMPLETED
     */
    // =============== GET DETAIL 1 MAINTENANCE (PERSONAL HOẶC GROUP) ===============
    public MaintenanceResponseDTO getOne(Long id) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));
        return mapToDTO(m);
    }

    /**
     * Helper - Mapping
     */
    // =================== MAPPING ===================
    private MaintenanceResponseDTO mapToDTO(Maintenance m) {
        return MaintenanceResponseDTO.builder()
                .id(m.getId())
                .vehicleId(m.getVehicle().getId())
                .vehicleModel(m.getVehicle().getModel())
                .requestedByName(m.getRequestedBy().getFullName())
                .approvedByName(m.getApprovedBy() != null ? m.getApprovedBy().getFullName() : null)

                .liableUserName(m.getLiableUser() != null ? m.getLiableUser().getFullName() : null)
                .coverageType(m.getCoverageType())

                .description(m.getDescription())
                .actualCost(m.getActualCost())
                .status(m.getStatus())
                .requestDate(m.getRequestDate())
                .approvalDate(m.getApprovalDate())
                .nextDueDate(m.getNextDueDate())

                .estimatedDurationDays(m.getEstimatedDurationDays())
                .maintenanceStartAt(m.getMaintenanceStartAt())
                .expectedFinishAt(m.getExpectedFinishAt())
                .maintenanceCompletedAt(m.getMaintenanceCompletedAt())

                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                // nếu sau này muốn show thêm liableUserName, coverageType thì bổ sung field trong DTO
                .build();
    }
}
