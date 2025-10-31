package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.MaintenanceCreateRequest;
import com.group8.evcoownership.dto.MaintenanceResponseDTO;
import com.group8.evcoownership.entity.Maintenance;
import com.group8.evcoownership.entity.User;
import com.group8.evcoownership.entity.Vehicle;
import com.group8.evcoownership.repository.MaintenanceRepository;
import com.group8.evcoownership.repository.UserRepository;
import com.group8.evcoownership.repository.VehicleRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    public MaintenanceResponseDTO create(MaintenanceCreateRequest req, String username){
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
    public List<MaintenanceResponseDTO> getAll(){
        return maintenanceRepository.findAll()
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }
    // =================== APPROVE ===================
    public MaintenanceResponseDTO approve(Long id, String staffEmail, LocalDate nextDueDate) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // Chỉ được duyệt khi đang ở trạng thái PENDING
        if (!"PENDING".equals(maintenance.getStatus())) {
            throw new IllegalStateException("Only PENDING maintenance requests can be approved.");
        }

        // Bắt buộc staff phải nhập nextDueDate
        if (nextDueDate == null) {
            throw new IllegalArgumentException("Next due date must be provided when approving maintenance.");
        }
        if (nextDueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Next due date must be in the future.");
        }

        // Cập nhật trạng thái
        LocalDateTime now = LocalDateTime.now();
        maintenance.setStatus("APPROVED");
        maintenance.setApprovedBy(staff);
        maintenance.setApprovalDate(now);
        maintenance.setNextDueDate(nextDueDate);

        maintenanceRepository.save(maintenance);

        return mapToDTO(maintenance);
    }

    // =================== REJECT ===================
    public MaintenanceResponseDTO reject(Long id, String staffEmail) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

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
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

}
