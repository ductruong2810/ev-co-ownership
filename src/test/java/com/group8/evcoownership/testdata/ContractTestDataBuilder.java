package com.group8.evcoownership.testdata;

import com.group8.evcoownership.entity.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Test data builder for contract-related entities
 */
public class ContractTestDataBuilder {

    public static class OwnershipGroupBuilder {
        private Long groupId = 1L;
        private String groupName = "Test EV Group";
        private String description = "Test group for EV co-ownership";
        private Integer memberCapacity = 5;
        private Boolean isActive = true;

        public OwnershipGroupBuilder withGroupId(Long groupId) {
            this.groupId = groupId;
            return this;
        }

        public OwnershipGroupBuilder withGroupName(String groupName) {
            this.groupName = groupName;
            return this;
        }

        public OwnershipGroupBuilder withDescription(String description) {
            this.description = description;
            return this;
        }

        public OwnershipGroupBuilder withMemberCapacity(Integer memberCapacity) {
            this.memberCapacity = memberCapacity;
            return this;
        }

        public OwnershipGroupBuilder withIsActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public OwnershipGroup build() {
            return OwnershipGroup.builder()
                    .groupId(groupId)
                    .groupName(groupName)
                    .description(description)
                    .memberCapacity(memberCapacity)
                    .build();
        }
    }

    public static class UserBuilder {
        private Long userId = 1L;
        private String fullName = "Test User";
        private String email = "test@example.com";
        private String phoneNumber = "0123456789";
        private String password = "TestPassword123!";
        private Boolean isActive = true;

        public UserBuilder withUserId(Long userId) {
            this.userId = userId;
            return this;
        }

        public UserBuilder withFullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public UserBuilder withEmail(String email) {
            this.email = email;
            return this;
        }

        public UserBuilder withPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public UserBuilder withPassword(String password) {
            this.password = password;
            return this;
        }

        public UserBuilder withIsActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public User build() {
            return User.builder()
                    .userId(userId)
                    .fullName(fullName)
                    .email(email)
                    .phoneNumber(phoneNumber)
                    .passwordHash(password)
                    .status(com.group8.evcoownership.enums.UserStatus.ACTIVE)
                    .build();
        }
    }

    public static class VehicleBuilder {
        private Long vehicleId = 1L;
        private String brand = "VinFast";
        private String model = "VF 8 Plus";
        private String licensePlate = "30A-123.45";
        private String chassisNumber = "RLVZZZ1EZBW000001";
        private BigDecimal vehicleValue = new BigDecimal("950000000");
        private OwnershipGroup ownershipGroup;

        public VehicleBuilder withVehicleId(Long vehicleId) {
            this.vehicleId = vehicleId;
            return this;
        }

        public VehicleBuilder withBrand(String brand) {
            this.brand = brand;
            return this;
        }

        public VehicleBuilder withModel(String model) {
            this.model = model;
            return this;
        }

        public VehicleBuilder withLicensePlate(String licensePlate) {
            this.licensePlate = licensePlate;
            return this;
        }

        public VehicleBuilder withChassisNumber(String chassisNumber) {
            this.chassisNumber = chassisNumber;
            return this;
        }

        public VehicleBuilder withVehicleValue(BigDecimal vehicleValue) {
            this.vehicleValue = vehicleValue;
            return this;
        }

        public VehicleBuilder withOwnershipGroup(OwnershipGroup ownershipGroup) {
            this.ownershipGroup = ownershipGroup;
            return this;
        }

        public Vehicle build() {
            return Vehicle.builder()
                    .Id(vehicleId)
                    .brand(brand)
                    .model(model)
                    .licensePlate(licensePlate)
                    .chassisNumber(chassisNumber)
                    .vehicleValue(vehicleValue)
                    .ownershipGroup(ownershipGroup)
                    .build();
        }
    }

    public static class OwnershipShareBuilder {
        private Long shareId = 1L;
        private OwnershipGroup group;
        private User user;
        private BigDecimal ownershipPercentage = new BigDecimal("20.00");

        public OwnershipShareBuilder withShareId(Long shareId) {
            this.shareId = shareId;
            return this;
        }

        public OwnershipShareBuilder withGroup(OwnershipGroup group) {
            this.group = group;
            return this;
        }

        public OwnershipShareBuilder withUser(User user) {
            this.user = user;
            return this;
        }

        public OwnershipShareBuilder withOwnershipPercentage(BigDecimal ownershipPercentage) {
            this.ownershipPercentage = ownershipPercentage;
            return this;
        }

        public OwnershipShare build() {
            return OwnershipShare.builder()
                    .id(new com.group8.evcoownership.entity.OwnershipShareId(user.getUserId(), group.getGroupId()))
                    .group(group)
                    .user(user)
                    .ownershipPercentage(ownershipPercentage)
                    .build();
        }
    }

    public static class ContractBuilder {
        private Long id = 1L;
        private OwnershipGroup group;
        private LocalDate startDate = LocalDate.now();
        private LocalDate endDate = LocalDate.now().plusYears(1);
        private String terms = "Test contract terms";
        private BigDecimal requiredDepositAmount = new BigDecimal("2000000");
        private Boolean isActive = true;
        private LocalDateTime createdAt = LocalDateTime.now();
        private LocalDateTime updatedAt = LocalDateTime.now();

        public ContractBuilder withId(Long id) {
            this.id = id;
            return this;
        }

        public ContractBuilder withGroup(OwnershipGroup group) {
            this.group = group;
            return this;
        }

        public ContractBuilder withStartDate(LocalDate startDate) {
            this.startDate = startDate;
            return this;
        }

        public ContractBuilder withEndDate(LocalDate endDate) {
            this.endDate = endDate;
            return this;
        }

        public ContractBuilder withTerms(String terms) {
            this.terms = terms;
            return this;
        }

        public ContractBuilder withRequiredDepositAmount(BigDecimal requiredDepositAmount) {
            this.requiredDepositAmount = requiredDepositAmount;
            return this;
        }

        public ContractBuilder withIsActive(Boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public ContractBuilder withCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public ContractBuilder withUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Contract build() {
            return Contract.builder()
                    .id(id)
                    .group(group)
                    .startDate(startDate)
                    .endDate(endDate)
                    .terms(terms)
                    .requiredDepositAmount(requiredDepositAmount)
                    .isActive(isActive)
                    .createdAt(createdAt)
                    .updatedAt(updatedAt)
                    .build();
        }
    }

    // Static factory methods for common test scenarios
    public static OwnershipGroupBuilder ownershipGroup() {
        return new OwnershipGroupBuilder();
    }

    public static UserBuilder user() {
        return new UserBuilder();
    }

    public static VehicleBuilder vehicle() {
        return new VehicleBuilder();
    }

    public static OwnershipShareBuilder ownershipShare() {
        return new OwnershipShareBuilder();
    }

    public static ContractBuilder contract() {
        return new ContractBuilder();
    }

    // Predefined test scenarios
    public static class TestScenarios {

        public static OwnershipGroup createBasicGroup() {
            return ownershipGroup().build();
        }

        public static User createBasicUser() {
            return user().build();
        }

        public static Vehicle createBasicVehicle(OwnershipGroup group) {
            return vehicle().withOwnershipGroup(group).build();
        }

        public static OwnershipShare createBasicShare(OwnershipGroup group, User user) {
            return ownershipShare()
                    .withGroup(group)
                    .withUser(user)
                    .build();
        }

        public static Contract createBasicContract(OwnershipGroup group) {
            return contract().withGroup(group).build();
        }

        public static Contract createExpiredContract(OwnershipGroup group) {
            return contract()
                    .withGroup(group)
                    .withStartDate(LocalDate.now().minusYears(2))
                    .withEndDate(LocalDate.now().minusYears(1))
                    .withIsActive(false)
                    .build();
        }

        public static Contract createFutureContract(OwnershipGroup group) {
            return contract()
                    .withGroup(group)
                    .withStartDate(LocalDate.now().plusMonths(1))
                    .withEndDate(LocalDate.now().plusYears(1).plusMonths(1))
                    .build();
        }
    }
}
