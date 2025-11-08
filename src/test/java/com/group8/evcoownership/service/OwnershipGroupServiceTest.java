package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.OwnershipGroupCreateRequestDTO;
import com.group8.evcoownership.dto.OwnershipGroupResponseDTO;
import com.group8.evcoownership.entity.*;
import com.group8.evcoownership.enums.DepositStatus;
import com.group8.evcoownership.enums.GroupRole;
import com.group8.evcoownership.enums.GroupStatus;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.OwnershipShareRepository;
import com.group8.evcoownership.repository.UserDocumentRepository;
import com.group8.evcoownership.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OwnershipGroupServiceTest {

    @Mock
    private OwnershipGroupRepository ownershipGroupRepository;

    @Mock
    private OwnershipShareRepository ownershipShareRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserDocumentRepository userDocumentRepository;

    @Mock
    private VehicleService vehicleService;

    @Mock
    private NotificationOrchestrator notificationOrchestrator;

    @Mock
    private FundService fundService;

    @InjectMocks
    private OwnershipGroupService ownershipGroupService;

    private User testUser;
    private OwnershipGroup testGroup;
    private OwnershipShare testShare;
    private SharedFund testFund;

    @BeforeEach
    void setUp() {
        // Setup test user
        testUser = User.builder()
                .userId(1L)
                .fullName("Test User")
                .email("test@example.com")
                .build();

        // Setup test group
        testGroup = OwnershipGroup.builder()
                .groupId(1L)
                .groupName("Test Group")
                .description("Test Description")
                .memberCapacity(5)
                .status(GroupStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Setup test ownership share
        testShare = OwnershipShare.builder()
                .id(new OwnershipShareId(1L, 1L))
                .user(testUser)
                .group(testGroup)
                .groupRole(GroupRole.ADMIN)
                .ownershipPercentage(BigDecimal.valueOf(100.00))
                .depositStatus(DepositStatus.PENDING)
                .joinDate(LocalDateTime.now())
                .build();

        // Setup test fund
        testFund = SharedFund.builder()
                .fundId(1L)
                .group(testGroup)
                .balance(BigDecimal.ZERO)
                .build();
    }

    @Test
    void create_ShouldCreateGroupAndFund_Success() {
        // Given
        OwnershipGroupCreateRequestDTO request = new OwnershipGroupCreateRequestDTO(
                "Test Group", "Test Description", 5);
        String userEmail = "test@example.com";

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.of(testUser));
        when(ownershipGroupRepository.existsByGroupNameIgnoreCase("Test Group")).thenReturn(false);
        //when(ownershipGroupRepository.save(any(OwnershipGroup.class))).thenReturn(testGroup);
        OwnershipGroup testGroup = OwnershipGroup.builder()
                .groupName("Test Group")
                .description("Test Description")
                .memberCapacity(5)
                .status(GroupStatus.PENDING)
                .build();
        testGroup.setGroupId(1L); // 👈 PHẢI có ID

        when(ownershipGroupRepository.save(any(OwnershipGroup.class)))
                .thenReturn(testGroup);
        when(ownershipShareRepository.save(any(OwnershipShare.class))).thenReturn(testShare);
        //when(fundService.createOrGroup(1L)).thenReturn(testFund);

        // When
        OwnershipGroupResponseDTO result = ownershipGroupService.create(request, userEmail);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.groupId());
        assertEquals("Test Group", result.groupName());
        assertEquals("Test Description", result.description());
        assertEquals(5, result.memberCapacity());
        assertEquals(GroupStatus.PENDING, result.status());

        // Verify that fund was created
        // verify(fundService).createOrGroup(1L);
        verify(fundService).initTwoFundsIfMissing(1L);
        verify(ownershipGroupRepository).save(any(OwnershipGroup.class));
        verify(ownershipShareRepository).save(any(OwnershipShare.class));
    }

    @Test
    void create_GroupNameAlreadyExists_ShouldThrowException() {
        // Given
        OwnershipGroupCreateRequestDTO request = new OwnershipGroupCreateRequestDTO(
                "Existing Group", "Test Description", 5);
        String userEmail = "test@example.com";

        when(ownershipGroupRepository.existsByGroupNameIgnoreCase("Existing Group")).thenReturn(true);

        // When & Then
        assertThrows(IllegalStateException.class, () ->
                ownershipGroupService.create(request, userEmail));

        verify(ownershipGroupRepository).existsByGroupNameIgnoreCase("Existing Group");
        verify(ownershipGroupRepository, never()).save(any(OwnershipGroup.class));
        verify(fundService, never()).createOrGroup(any());
    }

    @Test
    void create_UserNotFound_ShouldThrowException() {
        // Given
        OwnershipGroupCreateRequestDTO request = new OwnershipGroupCreateRequestDTO(
                "Test Group", "Test Description", 5);
        String userEmail = "nonexistent@example.com";

        when(userRepository.findByEmail(userEmail)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(Exception.class, () ->
                ownershipGroupService.create(request, userEmail));

        verify(userRepository).findByEmail(userEmail);
        verify(ownershipGroupRepository, never()).save(any(OwnershipGroup.class));
        verify(fundService, never()).createOrGroup(any());
    }
}
