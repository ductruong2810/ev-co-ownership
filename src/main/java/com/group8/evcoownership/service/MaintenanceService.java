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
                .nextDueDate(req.getMaintenanceDate()) // Map maintenanceDate từ request vào nextDueDate
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
    public MaintenanceResponseDTO approve(Long id, String staffEmail) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        LocalDateTime now = LocalDateTime.now();
        maintenance.setStatus("APPROVED");
        maintenance.setApprovedBy(staff);
        maintenance.setApprovalDate(now);
        
        // If nextDueDate is not set, calculate it from approvalDate + 3 months
        if (maintenance.getNextDueDate() == null) {
            maintenance.setNextDueDate(now.toLocalDate().plusMonths(3));
        }
        
        maintenanceRepository.save(maintenance);

        return mapToDTO(maintenance);
    }
    // =================== REJECT ===================
    public MaintenanceResponseDTO reject(Long id, String staffEmail) {
        Maintenance maintenance = maintenanceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Maintenance not found"));

        User staff = userRepository.findByEmail(staffEmail)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

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
                .maintenanceDate(m.getNextDueDate()) // Map nextDueDate to maintenanceDate for backward compatibility
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }
}
