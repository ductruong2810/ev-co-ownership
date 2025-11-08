package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.*;
import com.group8.evcoownership.dto.IncidentResponseDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.BookingStatus;
import com.group8.evcoownership.enums.FundType;
import com.group8.evcoownership.repository.*;
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
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final UsageBookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final SharedFundRepository sharedFundRepository;

    // CREATE INCIDENT
    public IncidentResponseDTO create(IncidentCreateRequestDTO req, String username) {
        User reporter = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        UsageBooking booking = bookingRepository.findById(req.getBookingId())
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));

        if (!booking.getUser().getEmail().equals(username)) {
            throw new IllegalStateException("You can only report incidents for your own bookings.");
        }

        // Cho phép report nếu đang IN_USE, CONFIRMED, hoặc COMPLETED trong 24h
        if (!List.of(BookingStatus.CONFIRMED, BookingStatus.AWAITING_REVIEW,
                BookingStatus.NEEDS_ATTENTION, BookingStatus.COMPLETED)
                .contains(booking.getStatus())) {
            throw new IllegalStateException("Booking not eligible for incident reporting.");
        }


        if (booking.getStatus() == BookingStatus.COMPLETED &&
                booking.getEndDateTime().isBefore(LocalDateTime.now().minusHours(24))) {
            throw new IllegalStateException("Incident report expired (over 24h after booking).");
        }


        Incident incident = Incident.builder()
                .booking(booking)
                .reportedBy(reporter)
                .description(req.getDescription())
                .actualCost(req.getActualCost())
                .imageUrls(req.getImageUrls())
                .status("PENDING")
                .build();

        incidentRepository.save(incident);
        return mapToDTO(incident);
    }

    // UPDATE INCIDENT (PENDING ONLY)
    public IncidentResponseDTO update(Long id, IncidentUpdateRequestDTO req, String username) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found"));

        if (!"PENDING".equals(incident.getStatus())) {
            throw new IllegalStateException("Only PENDING incidents can be updated.");
        }

        if (!incident.getReportedBy().getEmail().equals(username)) {
            throw new IllegalStateException("You can only update your own incident.");
        }

        incident.setDescription(req.getDescription());
        incident.setActualCost(req.getActualCost());
        incident.setImageUrls(req.getImageUrls());
        incident.setUpdatedAt(LocalDateTime.now());
        incidentRepository.save(incident);

        return mapToDTO(incident);
    }

    // ===============================================================
    // STAFF/ADMIN — UPDATE APPROVE INCIDENT
    // ===============================================================
    public IncidentResponseDTO approveIncident(Long id, String username) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found"));

        if (!"PENDING".equals(incident.getStatus())) {
            throw new IllegalStateException("Only PENDING incidents can be approved.");
        }

        User approver = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        // LAY GROUPiD DE BO VAO GOI HAM TIM QUY
        Long groupId = incident.getBooking()
                .getVehicle()
                .getOwnershipGroup()
                .getGroupId();
        // ✅ Lấy đúng quỹ OPERATING (chi được) của group
        SharedFund fund = sharedFundRepository
                .findByGroup_GroupIdAndFundType(groupId, FundType.OPERATING)
                .orElseThrow(() -> new EntityNotFoundException("Operating fund not found for group: " + groupId));

        if (!fund.isSpendable()) {
            throw new IllegalStateException("Operating fund is not spendable");
        }

        // ✅ Tạo Expense, thêm recipientUserId = người báo cáo sự cố
        Expense expense = Expense.builder()
                .fund(fund)
                .sourceType("INCIDENT")
                .sourceId(incident.getId())
                .description("Incident approved: " + incident.getDescription())
                .amount(incident.getActualCost())
                .approvedBy(approver)
                .recipientUser(incident.getReportedBy())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        expenseRepository.save(expense);

        // ✅ Cập nhật incident
        incident.setStatus("APPROVED");
        incident.setApprovedBy(approver);
        incident.setUpdatedAt(LocalDateTime.now());
        incidentRepository.save(incident);

        return mapToDTO(incident);
    }


    // ===============================================================
    // STAFF/ADMIN — UPDATE REJECT  INCIDENT
    // ===============================================================
    public IncidentResponseDTO rejectIncident(Long id, IncidentRejectRequestDTO req, String username) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found"));

        if (!"PENDING".equals(incident.getStatus())) {
            throw new IllegalStateException("Only PENDING incidents can be rejected.");
        }

        if (req.getRejectionCategory() == null ) {
            throw new IllegalArgumentException("Rejection category is required.");
        }

        User approver = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        incident.setStatus("REJECTED");
        incident.setApprovedBy(approver);
        incident.setRejectionCategory(req.getRejectionCategory());
        incident.setRejectionReason(req.getRejectionReason());
        incident.setUpdatedAt(LocalDateTime.now());

        incidentRepository.save(incident);
        return mapToDTO(incident);
    }

    // GET ALL (STAFF/ADMIN)
    public List<IncidentResponseDTO> getAll() {
        return incidentRepository.findAll()
                .stream().map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // GET MY INCIDENTS (CO_OWNER)
    public List<IncidentResponseDTO> getMyIncidents(String username) {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        return incidentRepository.findByReportedBy_UserId(user.getUserId())
                .stream().map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // GET ONE
    public IncidentResponseDTO getOne(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Incident not found"));
        return mapToDTO(incident);
    }

    // Get Filtered
    public Page<IncidentResponseDTO> getFiltered(String status, String startDate, String endDate, int page, int size) {
        Pageable pageable = PageRequest.of(page, size); // sort đã nằm trong query nên không cần thêm Sort ở đây

        Page<Incident> incidents;

        boolean noFilter = (status == null || status.isBlank()) &&
                (startDate == null || startDate.isBlank()) &&
                (endDate == null || endDate.isBlank());

        if (noFilter) {
            incidents = incidentRepository.findAll(pageable); // vẫn sort theo ID mặc định
        } else {
            incidents = incidentRepository.findByFiltersOrdered(status, startDate, endDate, pageable);
        }

        return incidents.map(this::mapToDTO);
    }


    // Mapping helper
    private IncidentResponseDTO mapToDTO(Incident i) {
        return IncidentResponseDTO.builder()
                .id(i.getId())
                .bookingId(i.getBooking().getId())
                .reportedById(i.getReportedBy().getUserId())
                .reportedByName(i.getReportedBy().getFullName())
                .description(i.getDescription())
                .actualCost(i.getActualCost())
                .imageUrls(i.getImageUrls())
                .status(i.getStatus())
                .approvedById(i.getApprovedBy() != null ? i.getApprovedBy().getUserId() : null)
                .approvedByName(i.getApprovedBy() != null ? i.getApprovedBy().getFullName() : null)
                .rejectionCategory(i.getRejectionCategory())
                .rejectionReason(i.getRejectionReason())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }
}

