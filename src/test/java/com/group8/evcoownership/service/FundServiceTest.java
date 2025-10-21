package com.group8.evcoownership.service;

import com.group8.evcoownership.dto.FundBalanceResponse;
import com.group8.evcoownership.dto.SharedFundCreateRequest;
import com.group8.evcoownership.dto.SharedFundDto;
import com.group8.evcoownership.dto.SharedFundUpdateRequest;
import com.group8.evcoownership.entity.OwnershipGroup;
import com.group8.evcoownership.entity.SharedFund;
import com.group8.evcoownership.repository.OwnershipGroupRepository;
import com.group8.evcoownership.repository.SharedFundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FundServiceTest {

    @Mock
    private SharedFundRepository fundRepo;

    @Mock
    private OwnershipGroupRepository groupRepo;

    @InjectMocks
    private FundService fundService;

    private OwnershipGroup testGroup;
    private SharedFund testFund;

    @BeforeEach
    void setUp() {
        // Setup test group
        testGroup = OwnershipGroup.builder()
                .groupId(1L)
                .groupName("Test Group")
                .description("Test Description")
                .memberCapacity(5)
                .build();

        // Setup test fund
        testFund = SharedFund.builder()
                .fundId(1L)
                .group(testGroup)
                .balance(BigDecimal.ZERO)
                .targetAmount(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    void createOrGroup_ShouldCreateFundWithTargetAmountZero_Success() {
        // Given
        Long groupId = 1L;
        when(fundRepo.existsByGroup_GroupId(groupId)).thenReturn(false);
        when(groupRepo.findById(groupId)).thenReturn(Optional.of(testGroup));
        when(fundRepo.save(any(SharedFund.class))).thenReturn(testFund);

        // When
        SharedFund result = fundService.createOrGroup(groupId);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(BigDecimal.ZERO, result.getTargetAmount());
        assertEquals(testGroup, result.getGroup());

        verify(fundRepo).existsByGroup_GroupId(groupId);
        verify(groupRepo).findById(groupId);
        verify(fundRepo).save(any(SharedFund.class));
    }

    @Test
    void getBalanceByGroupId_ShouldReturnTargetAmount_Success() {
        // Given
        Long groupId = 1L;
        when(fundRepo.findByGroup_GroupId(groupId)).thenReturn(Optional.of(testFund));

        // When
        FundBalanceResponse result = fundService.getBalanceByGroupId(groupId);

        // Then
        assertNotNull(result);
        assertEquals(1L, result.getFundId());
        assertEquals(1L, result.getGroupId());
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(BigDecimal.ZERO, result.getTargetAmount());

        verify(fundRepo).findByGroup_GroupId(groupId);
    }

    @Test
    void updateBalance_ShouldUpdateTargetAmount_Success() {
        // Given
        Long fundId = 1L;
        BigDecimal newBalance = new BigDecimal("1000000");
        BigDecimal newTargetAmount = new BigDecimal("5000000");
        
        SharedFundUpdateRequest request = new SharedFundUpdateRequest();
        request.setBalance(newBalance);
        request.setTargetAmount(newTargetAmount);

        when(fundRepo.findById(fundId)).thenReturn(Optional.of(testFund));
        when(fundRepo.save(any(SharedFund.class))).thenReturn(testFund);

        // When
        SharedFund result = fundService.updateBalance(fundId, request);

        // Then
        assertNotNull(result);
        verify(fundRepo).findById(fundId);
        verify(fundRepo).save(any(SharedFund.class));
    }

    @Test
    void create_WithTargetAmount_ShouldCreateFundWithCustomTargetAmount_Success() {
        // Given
        BigDecimal customTargetAmount = new BigDecimal("5000000");
        SharedFundCreateRequest request = new SharedFundCreateRequest();
        request.setGroupId(1L);
        request.setTargetAmount(customTargetAmount);

        // Tạo mock fund với targetAmount tùy chỉnh
        SharedFund customFund = SharedFund.builder()
                .fundId(1L)
                .group(testGroup)
                .balance(BigDecimal.ZERO)
                .targetAmount(customTargetAmount)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(fundRepo.existsByGroup_GroupId(1L)).thenReturn(false);
        when(groupRepo.findById(1L)).thenReturn(Optional.of(testGroup));
        when(fundRepo.save(any(SharedFund.class))).thenReturn(customFund);

        // When
        SharedFund result = fundService.create(request);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(customTargetAmount, result.getTargetAmount());
        assertEquals(testGroup, result.getGroup());

        verify(fundRepo).existsByGroup_GroupId(1L);
        verify(groupRepo).findById(1L);
        verify(fundRepo).save(any(SharedFund.class));
    }

    @Test
    void create_WithoutTargetAmount_ShouldCreateFundWithDefaultTargetAmount_Success() {
        // Given
        SharedFundCreateRequest request = new SharedFundCreateRequest();
        request.setGroupId(1L);
        request.setTargetAmount(null); // Không nhập targetAmount

        when(fundRepo.existsByGroup_GroupId(1L)).thenReturn(false);
        when(groupRepo.findById(1L)).thenReturn(Optional.of(testGroup));
        when(fundRepo.save(any(SharedFund.class))).thenReturn(testFund);

        // When
        SharedFund result = fundService.create(request);

        // Then
        assertNotNull(result);
        assertEquals(BigDecimal.ZERO, result.getBalance());
        assertEquals(BigDecimal.ZERO, result.getTargetAmount()); // Mặc định = 0
        assertEquals(testGroup, result.getGroup());

        verify(fundRepo).existsByGroup_GroupId(1L);
        verify(groupRepo).findById(1L);
        verify(fundRepo).save(any(SharedFund.class));
    }

    @Test
    void list_ShouldIncludeTargetAmount_Success() {
        // Given
        Pageable pageable = mock(Pageable.class);
        when(fundRepo.findAll(pageable)).thenReturn(new PageImpl<>(List.of(testFund)));

        // When
        List<SharedFundDto> result = fundService.list(pageable);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        
        SharedFundDto dto = result.get(0);
        assertEquals(1L, dto.fundId());
        assertEquals(1L, dto.groupId());
        assertEquals(BigDecimal.ZERO, dto.balance());
        assertEquals(BigDecimal.ZERO, dto.targetAmount());
        assertNotNull(dto.createdAt());
        assertNotNull(dto.updatedAt());

        verify(fundRepo).findAll(pageable);
    }
}
