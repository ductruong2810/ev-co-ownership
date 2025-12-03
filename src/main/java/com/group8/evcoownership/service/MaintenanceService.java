package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.MaintenanceCreateRequestDTO;
import com.group8.evcoownership.dto.MaintenanceResponseDTO;
import com.group8.evcoownership.dto.MaintenanceUpdateRequestDTO;
import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.enums.MaintenanceCoverageType;
import com.group8.evcoownership.repository.MaintenanceRepository;
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
public class MaintenanceService {

    private final MaintenanceRepository maintenanceRepository;
    private final VehicleRepository vehicleRepository;
    private final UserRepository userRepository;

    // =================== CREATE ===================
    public MaintenanceResponseDTO create(MaintenanceCreateRequestDTO req, String username) {
        User technician = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Vehicle vehicle = vehicleRepository.findById(req.getVehicleId())
                .orElseThrow(() -> new EntityNotFoundException("Vehicle not found"));

        LocalDateTime now = LocalDateTime.now();
        Maintenance maintenance = Maintenance.builder()
                .vehicle(vehicle)
                .requestedBy(technician)
                .description(req.getDescription())
                .actualCost(req.getCost())
                .status("PENDING")
                .coverageType(MaintenanceCoverageType.GROUP) // bao duong dinh ky, thi group chiu chung
                .requestDate(now)
                .build();

        maintenance = maintenanceRepository.save(maintenance);
        return mapToDTO(maintenance);
    }

    // =================== GET ONE ===================
    public MaintenanceResponseDTO getOne(Long id) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));
        return mapToDTO(maintenance);
    }

    // =================== GET ALL ===================
    public List<MaintenanceResponseDTO> getAll() {
        return maintenanceRepository.findAllSorted()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // ================ GET MY MAINTENANCE ===========
    public List<MaintenanceResponseDTO> getMyRequests(String username) {
        return maintenanceRepository.findAllByTechnicianEmailSorted(username)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }


    // =================== UPDATE ===================
    public MaintenanceResponseDTO update(Long id, MaintenanceUpdateRequestDTO req, String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        if (!"PENDING".equals(maintenance.getStatus())) {
            throw new IllegalStateException("Only PENDING maintenance requests can be updated.");
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


    // =================== APPROVE ===================
    public MaintenanceResponseDTO approve(Long id, String staffEmail) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // chi co bao duong dinh ky -> moi co status APPROVED
        // maintenance after check out -> di tu pending -> funded
        if (maintenance.getCoverageType() != MaintenanceCoverageType.GROUP) {
            throw new IllegalStateException("Only GROUP_FUND maintenance can be approved here.");
        }

        // Chỉ được duyệt khi đang ở trạng thái PENDING
        if (!"PENDING".equals(maintenance.getStatus())) {
            throw new IllegalStateException("Only PENDING maintenance requests can be approved.");
        }

        // Cập nhật trạng thái
        LocalDateTime now = LocalDateTime.now();
        maintenance.setStatus("APPROVED");
        maintenance.setApprovedBy(staff);
        maintenance.setApprovalDate(now);
        maintenanceRepository.save(maintenance);


        // ===== TÍNH AI ĐÓNG BAO NHIÊU =====

//        var group = m.getVehicle().getOwnershipGroup();
//        var shares = ownershipShareRepository
//                .findByOwnershipGroup_GroupId(group.getGroupId());
//
//        BigDecimal totalCost = m.getActualCost();
//
//        List<MaintenancePayerShareDTO> payerShareDTOs = shares.stream()
//                .map(share -> {
//                    BigDecimal ratio = share.getOwnershipRatio(); // vd: 0.40
//                    BigDecimal amount = totalCost
//                            .multiply(ratio)
//                            .setScale(2, BigDecimal.ROUND_HALF_UP);
//
//                    return MaintenancePayerShareDTO.builder()
//                            .userId(share.getUser().getUserId())
//                            .fullName(share.getUser().getFullName())
//                            .ownershipRatio(ratio)
//                            .amount(amount)
//                            .build();
//                })
//                .toList();
//
//        MaintenanceResponseDTO dto = mapToDTO(m);
//        dto.setPayerShares(payerShareDTOs);
//
//        return dto;

        return mapToDTO(maintenance);
    }

    // =================== REJECT ===================
    public MaintenanceResponseDTO reject(Long id, String staffEmail) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        // chi co bao duong dinh ky -> moi co status REJECTED
        // maintenance after check out -> di tu pending -> funded
        if (maintenance.getCoverageType() != MaintenanceCoverageType.GROUP) {
            throw new IllegalStateException("Only GROUP_FUND maintenance can be rejected here.");
        }

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!"PENDING".equals(maintenance.getStatus())) {
            throw new IllegalStateException("Only PENDING maintenance requests can be rejected.");
        }

        LocalDateTime now = LocalDateTime.now();
        maintenance.setStatus("REJECTED");
        maintenance.setApprovedBy(staff);
        maintenance.setApprovalDate(now); // Set approvalDate when rejected (staff action date)
        maintenanceRepository.save(maintenance);

        return mapToDTO(maintenance);
    }

    /**
     * cac api moi de hoan thien flow maintenance
     * status Funded -> In_progress
     * status In_progress -> Completed
     */
    // =================== START (FUNDED → IN_PROGRESS) ===================
    public MaintenanceResponseDTO startMaintenance(Long id, String staffEmail) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

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

    // =================== COMPLETE (IN_PROGRESS → COMPLETED) ===================
    public MaintenanceResponseDTO completeMaintenance(Long id, LocalDate nextDueDate, String staffEmail) {
        Maintenance m = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!"IN_PROGRESS".equals(m.getStatus())) {
            throw new IllegalStateException("Only IN_PROGRESS maintenance can be completed.");
        }

        LocalDateTime now = LocalDateTime.now();
        m.setStatus("COMPLETED");
        m.setMaintenanceCompletedAt(now);

        if (nextDueDate != null) {
            if (!nextDueDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Next due date must be in the future.");
            }
            m.setNextDueDate(nextDueDate);
        }

        m.setUpdatedAt(now);
        maintenanceRepository.save(m);

        return mapToDTO(m);
    }


    /**
     * HELPER - MAPPING SECTION
     */
    // =================== MAPPING ===================
    private MaintenanceResponseDTO mapToDTO(Maintenance m) {
        return MaintenanceResponseDTO.builder()
                .id(m.getId())
                .vehicleId(m.getVehicle().getId())
                .vehicleModel(m.getVehicle().getModel())
                .requestedByName(m.getRequestedBy().getFullName())
                .approvedByName(m.getApprovedBy() != null ? m.getApprovedBy().getFullName() : null)
                .description(m.getDescription())
                .actualCost(m.getActualCost())
                .status(m.getStatus())
                .requestDate(m.getRequestDate())
                .approvalDate(m.getApprovalDate())
                .nextDueDate(m.getNextDueDate())

                // ===== thời gian bảo trì =====
                .estimatedDurationDays(m.getEstimatedDurationDays())
                .maintenanceStartAt(m.getMaintenanceStartAt())
                .expectedFinishAt(m.getExpectedFinishAt())
                .maintenanceCompletedAt(m.getMaintenanceCompletedAt())

                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

}
