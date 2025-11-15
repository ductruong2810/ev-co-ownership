package com.group8.evcoownership.service;


import com.group8.evcoownership.dto.MaintenanceAfterCheckOutCreateRequestDTO;
import com.group8.evcoownership.dto.MaintenanceResponseDTO;
import com.group8.evcoownership.dto.MaintenanceUpdateRequestDTO;
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

import java.math.BigDecimal;
import java.time.LocalDate;
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
     * Technician
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
                .findByGroupGroupId(group.getGroupId());

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

    // =================== UPDATE (TECHNICIAN UPDATE KHI CÒN PENDING) ===================
    public MaintenanceResponseDTO update(Long id, MaintenanceUpdateRequestDTO req, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        if (!"PENDING".equals(maintenance.getStatus())) {
            throw new IllegalStateException("Only PENDING maintenance requests can be updated.");
        }

        // Chỉ technician (người tạo) mới được sửa
        if (!maintenance.getRequestedBy().getUserId().equals(user.getUserId())) {
            throw new SecurityException("Only the technician who created this request can update it.");
        }

        if (maintenance.getCoverageType() != MaintenanceCoverageType.PERSONAL) {
            throw new IllegalStateException("This update is only for PERSONAL maintenance.");
        }


        if (req.getDescription() != null && !req.getDescription().isBlank()) {
            maintenance.setDescription(req.getDescription());
        }

        if (req.getCost() != null && req.getCost().compareTo(BigDecimal.ZERO) > 0) {
            maintenance.setActualCost(req.getCost());
        }

        if (req.getNextDueDate() != null && req.getNextDueDate().isAfter(LocalDate.now())) {
            maintenance.setNextDueDate(req.getNextDueDate());
        }

        if (req.getEstimatedDurationDays() != null && req.getEstimatedDurationDays() > 0) {
            maintenance.setEstimatedDurationDays(req.getEstimatedDurationDays());
        }

        maintenance.setUpdatedAt(LocalDateTime.now());
        maintenanceRepository.save(maintenance);

        return mapToDTO(maintenance);
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
    // =============== 1. GET DETAIL 1 MAINTENANCE (PERSONAL HOẶC GROUP) ===============
    public MaintenanceResponseDTO getOne(Long id) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));
        return mapToDTO(m);
    }

    // ==========================================================
    // 2. STAFF/ADMIN: PENDING -> APPROVED (PERSONAL, không chia tiền)
    // ==========================================================
    public MaintenanceResponseDTO approveAfterCheckOut(Long id, String staffEmail) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (m.getCoverageType() != MaintenanceCoverageType.PERSONAL) {
            throw new IllegalStateException("This approve method is only for PERSONAL maintenance.");
        }

        if (!"PENDING".equals(m.getStatus())) {
            throw new IllegalStateException("Only PENDING maintenance can be approved.");
        }

        LocalDateTime now = LocalDateTime.now();
        m.setStatus("APPROVED");
        m.setApprovedBy(staff);
        m.setApprovalDate(now);
        m.setUpdatedAt(now);

        maintenanceRepository.save(m);
        return mapToDTO(m);
    }

    // ==========================================================
    // 3. STAFF/ADMIN: PENDING -> REJECTED (PERSONAL)
    // ==========================================================
    public MaintenanceResponseDTO rejectAfterCheckOut(Long id, String staffEmail) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (m.getCoverageType() != MaintenanceCoverageType.PERSONAL) {
            throw new IllegalStateException("This reject method is only for PERSONAL maintenance.");
        }

        if (!"PENDING".equals(m.getStatus())) {
            throw new IllegalStateException("Only PENDING maintenance can be rejected.");
        }

        LocalDateTime now = LocalDateTime.now();
        m.setStatus("REJECTED");
        m.setApprovedBy(staff);
        m.setApprovalDate(now);
        m.setUpdatedAt(now);

        maintenanceRepository.save(m);
        return mapToDTO(m);
    }

    // ==========================================================
    // 4. STAFF: FUNDED -> IN_PROGRESS (PERSONAL sau checkout)
    // ==========================================================
    public MaintenanceResponseDTO startAfterCheckOut(Long id, String staffEmail) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (m.getCoverageType() != MaintenanceCoverageType.PERSONAL) {
            throw new IllegalStateException("This start method is only for PERSONAL maintenance.");
        }

        if (!"FUNDED".equals(m.getStatus())) {
            throw new IllegalStateException("Only FUNDED maintenance can be started.");
        }

        LocalDateTime now = LocalDateTime.now();
        m.setStatus("IN_PROGRESS");
        m.setMaintenanceStartAt(now);

        if (m.getEstimatedDurationDays() != null && m.getEstimatedDurationDays() > 0) {
            m.setExpectedFinishAt(now.plusDays(m.getEstimatedDurationDays()));
        }

        m.setUpdatedAt(now);
        maintenanceRepository.save(m);

        return mapToDTO(m);
    }

    // ==========================================================
    // 5. STAFF: IN_PROGRESS -> COMPLETED (PERSONAL sau checkout)
    // ==========================================================
    public MaintenanceResponseDTO completeAfterCheckOut(Long id, String staffEmail) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (m.getCoverageType() != MaintenanceCoverageType.PERSONAL) {
            throw new IllegalStateException("This complete method is only for PERSONAL maintenance.");
        }

        if (!"IN_PROGRESS".equals(m.getStatus())) {
            throw new IllegalStateException("Only IN_PROGRESS maintenance can be completed.");
        }

        LocalDateTime now = LocalDateTime.now();
        m.setStatus("COMPLETED");
        m.setMaintenanceCompletedAt(now);
        m.setUpdatedAt(now);

        // Case after checkout là sự cố, không phải bảo trì định kỳ
        // nên thường không set nextDueDate (để null là đúng)

        maintenanceRepository.save(m);
        return mapToDTO(m);
    }
    // ==========================================================
    // 6. STAFF / ADMIN SEARCH BY MANY CRITERIAS
    // ==========================================================
    @Transactional(readOnly = true)
    public List<MaintenanceResponseDTO> searchPersonalForStaff(
            String status,
            Long groupId,
            Long vehicleId,
            Long liableUserId,
            Long requestedById,
            LocalDate fromRequestDate,
            LocalDate toRequestDate,
            BigDecimal costFrom,
            BigDecimal costTo
    ) {
        var list = maintenanceRepository
                .findByCoverageTypeOrderByRequestDateDesc(MaintenanceCoverageType.PERSONAL);

        return list.stream()
                .filter(m -> status == null
                        || m.getStatus().equalsIgnoreCase(status))
                .filter(m -> groupId == null
                        || (m.getVehicle().getOwnershipGroup() != null
                        && m.getVehicle().getOwnershipGroup().getGroupId().equals(groupId)))
                .filter(m -> vehicleId == null
                        || m.getVehicle().getId().equals(vehicleId))
                .filter(m -> liableUserId == null
                        || (m.getLiableUser() != null
                        && m.getLiableUser().getUserId().equals(liableUserId)))
                .filter(m -> requestedById == null
                        || m.getRequestedBy().getUserId().equals(requestedById))
                .filter(m -> {
                    if (fromRequestDate == null && toRequestDate == null) return true;
                    var d = m.getRequestDate().toLocalDate();
                    if (fromRequestDate != null && d.isBefore(fromRequestDate)) return false;
                    if (toRequestDate != null && d.isAfter(toRequestDate)) return false;
                    return true;
                })
                .filter(m -> {
                    if (costFrom == null && costTo == null) return true;
                    var cost = m.getActualCost();
                    if (cost == null) return false;
                    if (costFrom != null && cost.compareTo(costFrom) < 0) return false;
                    if (costTo != null && cost.compareTo(costTo) > 0) return false;
                    return true;
                })
                .map(this::mapToDTO)
                .toList();
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
