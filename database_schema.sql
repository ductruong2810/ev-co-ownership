-- =============================================
-- EV Co-ownership Database Schema for SQL Server
-- =============================================

-- Create Database
USE master;
GO

-- Drop database if exists
IF EXISTS (SELECT name FROM sys.databases WHERE name = 'EVShare')
BEGIN
    ALTER DATABASE EVShare SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE EVShare;
END
GO

-- Create database
CREATE DATABASE EVShare;
GO

USE EVShare;
GO

-- =============================================
-- 1. ROLES TABLE
-- =============================================
CREATE TABLE Roles (
    RoleId BIGINT IDENTITY(1,1) PRIMARY KEY,
    RoleName NVARCHAR(30) NOT NULL UNIQUE
);
GO

-- =============================================
-- 2. USERS TABLE
-- =============================================
CREATE TABLE Users (
    UserId BIGINT IDENTITY(1,1) PRIMARY KEY,
    FullName NVARCHAR(100) NOT NULL,
    Email NVARCHAR(100) NOT NULL UNIQUE,
    PasswordHash NVARCHAR(255) NOT NULL,
    PhoneNumber NVARCHAR(20),
    AvatarUrl NVARCHAR(500),
    RoleId BIGINT,
    Status NVARCHAR(20) DEFAULT 'ACTIVE',
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    UpdatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (RoleId) REFERENCES Roles(RoleId)
);
GO

-- =============================================
-- 3. OWNERSHIP GROUP TABLE
-- =============================================
CREATE TABLE OwnershipGroup (
    GroupId BIGINT IDENTITY(1,1) PRIMARY KEY,
    GroupName NVARCHAR(100) NOT NULL,
    Status NVARCHAR(20) DEFAULT 'ACTIVE',
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    UpdatedAt DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- 4. OWNERSHIP SHARE TABLE
-- =============================================
CREATE TABLE OwnershipShare (
    ShareId BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserId BIGINT NOT NULL,
    GroupId BIGINT NOT NULL,
    SharePercentage DECIMAL(5,2) NOT NULL CHECK (SharePercentage > 0 AND SharePercentage <= 100),
    FOREIGN KEY (UserId) REFERENCES Users(UserId),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup(GroupId),
    UNIQUE(UserId, GroupId)
);
GO

-- =============================================
-- 5. VEHICLE TABLE
-- =============================================
CREATE TABLE Vehicle (
    VehicleId BIGINT IDENTITY(1,1) PRIMARY KEY,
    Brand NVARCHAR(100),
    Model NVARCHAR(100),
    LicensePlate NVARCHAR(20),
    QrCode NVARCHAR(255),
    GroupId BIGINT,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    UpdatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup(GroupId)
);
GO

-- =============================================
-- 6. CONTRACT TABLE
-- =============================================
CREATE TABLE Contract (
    ContractId BIGINT IDENTITY(1,1) PRIMARY KEY,
    GroupId BIGINT NOT NULL,
    ContractContent NVARCHAR(MAX),
    ContractUrl NVARCHAR(500),
    SignedAt DATETIME2,
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup(GroupId)
);
GO

-- =============================================
-- 7. SHARED FUND TABLE
-- =============================================
CREATE TABLE SharedFund (
    FundId BIGINT IDENTITY(1,1) PRIMARY KEY,
    GroupId BIGINT NOT NULL,
    Balance DECIMAL(15,2) DEFAULT 0,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    UpdatedAt DATETIME2 DEFAULT GETDATE(),
    Version BIGINT DEFAULT 1,
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup(GroupId)
);
GO

-- =============================================
-- 8. USAGE BOOKING TABLE
-- =============================================
CREATE TABLE UsageBooking (
    BookingId BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserId BIGINT NOT NULL,
    VehicleId BIGINT NOT NULL,
    StartDateTime DATETIME2 NOT NULL,
    EndDateTime DATETIME2 NOT NULL,
    Status NVARCHAR(20) DEFAULT 'PENDING',
    Purpose NVARCHAR(500),
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    UpdatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (UserId) REFERENCES Users(UserId),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle(VehicleId)
);
GO

-- =============================================
-- 9. MAINTENANCE TABLE
-- =============================================
CREATE TABLE Maintenance (
    MaintenanceId BIGINT IDENTITY(1,1) PRIMARY KEY,
    VehicleId BIGINT NOT NULL,
    RequestedBy BIGINT NOT NULL,
    ApprovedBy BIGINT,
    RequestDate DATETIME2 DEFAULT GETDATE(),
    ApprovalDate DATETIME2,
    NextDueDate DATE,
    Description NVARCHAR(MAX),
    EstimatedCost DECIMAL(12,2),
    ActualCost DECIMAL(12,2),
    MaintenanceStatus NVARCHAR(20) DEFAULT 'PENDING',
    FOREIGN KEY (VehicleId) REFERENCES Vehicle(VehicleId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users(UserId)
);
GO

-- =============================================
-- 10. NOTIFICATION TABLE
-- =============================================
CREATE TABLE Notification (
    NotificationId BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserId BIGINT,
    Title NVARCHAR(255),
    Message NVARCHAR(MAX),
    NotificationType NVARCHAR(50),
    IsRead BIT DEFAULT 0,
    IsDelivered BIT DEFAULT 0,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (UserId) REFERENCES Users(UserId)
);
GO

-- =============================================
-- 11. INCIDENT TABLE
-- =============================================
CREATE TABLE Incident (
    IncidentId BIGINT IDENTITY(1,1) PRIMARY KEY,
    BookingId BIGINT NOT NULL,
    IncidentType NVARCHAR(50),
    Description NVARCHAR(MAX),
    EstimatedCost DECIMAL(12,2),
    ActualCost DECIMAL(12,2),
    Status NVARCHAR(20) DEFAULT 'REPORTED',
    ImageUrls NVARCHAR(MAX),
    IncidentDate DATETIME2 DEFAULT GETDATE(),
    ResolvedDate DATETIME2,
    ResolvedBy BIGINT,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking(BookingId),
    FOREIGN KEY (ResolvedBy) REFERENCES Users(UserId)
);
GO

-- =============================================
-- 12. VEHICLE CHECK TABLE
-- =============================================
CREATE TABLE VehicleCheck (
    Id BIGINT IDENTITY(1,1) PRIMARY KEY,
    BookingId BIGINT,
    CheckType NVARCHAR(20),
    Odometer INT,
    BatteryLevel DECIMAL(5,2),
    Cleanliness NVARCHAR(20),
    Notes NVARCHAR(MAX),
    Issues NVARCHAR(MAX),
    Status NVARCHAR(20),
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking(BookingId)
);
GO

-- =============================================
-- 13. USER DOCUMENT TABLE
-- =============================================
CREATE TABLE UserDocument (
    DocumentId BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserId BIGINT NOT NULL,
    DocumentType NVARCHAR(20),
    Side NVARCHAR(10),
    ImageUrl NVARCHAR(500) NOT NULL,
    Status NVARCHAR(20) DEFAULT 'PENDING',
    ReviewNote NVARCHAR(MAX),
    ReviewedBy BIGINT,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    UpdatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (ReviewedBy) REFERENCES Users(UserId)
);
GO

-- =============================================
-- 14. VOTING TABLE
-- =============================================
CREATE TABLE Voting (
    VotingId BIGINT IDENTITY(1,1) PRIMARY KEY,
    GroupId BIGINT,
    Title NVARCHAR(255),
    Description NVARCHAR(MAX),
    VotingType NVARCHAR(50),
    Options NVARCHAR(MAX),
    Results NVARCHAR(MAX),
    Deadline DATETIME2,
    Status NVARCHAR(20) DEFAULT 'ACTIVE',
    CreatedBy BIGINT,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup(GroupId)
);
GO

-- =============================================
-- 15. DISPUTE TABLE
-- =============================================
CREATE TABLE Dispute (
    DisputeId BIGINT IDENTITY(1,1) PRIMARY KEY,
    FundId BIGINT NOT NULL,
    CreatedBy BIGINT,
    DisputeType NVARCHAR(50),
    RelatedEntityType NVARCHAR(50),
    RelatedEntityId BIGINT,
    Description NVARCHAR(MAX),
    DisputedAmount DECIMAL(12,2),
    Resolution NVARCHAR(MAX),
    ResolutionAmount DECIMAL(12,2),
    Status NVARCHAR(20) DEFAULT 'OPEN',
    ResolvedBy BIGINT,
    CreatedAt DATETIME2 DEFAULT GETDATE(),
    ResolvedAt DATETIME2,
    FOREIGN KEY (FundId) REFERENCES SharedFund(FundId),
    FOREIGN KEY (ResolvedBy) REFERENCES Users(UserId)
);
GO

-- =============================================
-- 16. EXPENSE TABLE
-- =============================================
CREATE TABLE Expense (
    ExpenseId BIGINT IDENTITY(1,1) PRIMARY KEY,
    FundId BIGINT,
    SourceType NVARCHAR(50),
    SourceId BIGINT,
    Description NVARCHAR(MAX),
    Amount DECIMAL(12,2) NOT NULL,
    ExpenseDate DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (FundId) REFERENCES SharedFund(FundId)
);
GO

-- =============================================
-- 17. PAYMENT TABLE
-- =============================================
CREATE TABLE Payment (
    PaymentId BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserId BIGINT NOT NULL,
    FundID BIGINT NOT NULL,
    Amount DECIMAL(12,2) NOT NULL,
    PaymentDate DATETIME2 DEFAULT GETDATE(),
    PaymentMethod NVARCHAR(50),
    Status NVARCHAR(20) DEFAULT 'PENDING',
    TransactionCode NVARCHAR(100),
    ProviderResponse NVARCHAR(MAX),
    PaymentType NVARCHAR(20),
    FOREIGN KEY (UserId) REFERENCES Users(UserId),
    FOREIGN KEY (FundID) REFERENCES SharedFund(FundId)
);
GO

-- =============================================
-- 18. OTP TOKEN TABLE
-- =============================================
CREATE TABLE OtpToken (
    TokenId BIGINT IDENTITY(1,1) PRIMARY KEY,
    Email NVARCHAR(100) NOT NULL,
    OtpCode NVARCHAR(6) NOT NULL,
    ExpiresAt DATETIME2 NOT NULL,
    IsUsed BIT DEFAULT 0,
    CreatedAt DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- CREATE INDEXES
-- =============================================

-- User indexes
CREATE INDEX IX_Users_Email ON Users(Email);
CREATE INDEX IX_Users_RoleId ON Users(RoleId);
GO

-- Ownership indexes
CREATE INDEX IX_OwnershipShare_UserId ON OwnershipShare(UserId);
CREATE INDEX IX_OwnershipShare_GroupId ON OwnershipShare(GroupId);
GO

-- Vehicle indexes
CREATE INDEX IX_Vehicle_GroupId ON Vehicle(GroupId);
CREATE INDEX IX_Vehicle_LicensePlate ON Vehicle(LicensePlate);
GO

-- Booking indexes
CREATE INDEX IX_UsageBooking_UserId ON UsageBooking(UserId);
CREATE INDEX IX_UsageBooking_VehicleId ON UsageBooking(VehicleId);
CREATE INDEX IX_UsageBooking_StartDateTime ON UsageBooking(StartDateTime);
CREATE INDEX IX_UsageBooking_Status ON UsageBooking(Status);
GO

-- Maintenance indexes
CREATE INDEX IX_Maintenance_VehicleId ON Maintenance(VehicleId);
CREATE INDEX IX_Maintenance_RequestedBy ON Maintenance(RequestedBy);
CREATE INDEX IX_Maintenance_Status ON Maintenance(MaintenanceStatus);
GO

-- Notification indexes
CREATE INDEX IX_Notification_UserId ON Notification(UserId);
CREATE INDEX IX_Notification_IsRead ON Notification(IsRead);
GO

-- Incident indexes
CREATE INDEX IX_Incident_BookingId ON Incident(BookingId);
CREATE INDEX IX_Incident_UserId ON Incident(UserId);
CREATE INDEX IX_Incident_Status ON Incident(Status);
GO

-- VehicleCheck indexes
CREATE INDEX IX_VehicleCheck_BookingId ON VehicleCheck(BookingId);
CREATE INDEX IX_VehicleCheck_UserId ON VehicleCheck(UserId);
GO

-- UserDocument indexes
CREATE INDEX IX_UserDocument_UserId ON UserDocument(UserId);
CREATE INDEX IX_UserDocument_Status ON UserDocument(Status);
GO

-- Voting indexes
CREATE INDEX IX_Voting_GroupId ON Voting(GroupId);
CREATE INDEX IX_Voting_Status ON Voting(Status);
GO

-- Dispute indexes
CREATE INDEX IX_Dispute_FundId ON Dispute(FundId);
CREATE INDEX IX_Dispute_Status ON Dispute(Status);
GO

-- =============================================
-- INSERT SAMPLE DATA
-- =============================================

-- Insert roles
INSERT INTO Roles (RoleName) VALUES 
('Admin'),
('User'),
('Technician');
GO

-- Insert sample users
INSERT INTO Users (FullName, Email, PasswordHash, PhoneNumber, RoleId, Status) VALUES 
('Admin User', 'admin@evshare.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi', '0123456789', 1, 'ACTIVE'),
('John Doe', 'john@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi', '0987654321', 2, 'ACTIVE'),
('Jane Smith', 'jane@example.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi', '0555666777', 2, 'ACTIVE'),
('Tech Mike', 'tech@evshare.com', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVEFDi', '0333444555', 3, 'ACTIVE');
GO

-- Insert ownership group
INSERT INTO OwnershipGroup (GroupName, Status) VALUES 
('Tesla Model 3 Group', 'ACTIVE');
GO

-- Insert ownership shares
INSERT INTO OwnershipShare (UserId, GroupId, SharePercentage) VALUES 
(2, 1, 50.00),
(3, 1, 50.00);
GO

-- Insert vehicle
INSERT INTO Vehicle (Brand, Model, LicensePlate, QrCode, GroupId) VALUES 
('Tesla', 'Model 3', '30A-12345', 'QR_CODE_123', 1);
GO

-- Insert shared fund
INSERT INTO SharedFund (GroupId, Balance) VALUES 
(1, 1000000.00);
GO

-- Insert contract
INSERT INTO Contract (GroupId, ContractContent, ContractUrl, SignedAt) VALUES 
(1, 'Co-ownership agreement for Tesla Model 3', 'https://example.com/contract.pdf', GETDATE());
GO

-- Insert sample notifications
INSERT INTO Notification (UserId, Title, Message, NotificationType, IsRead) VALUES 
(2, 'Welcome to EVShare', 'Welcome to the Tesla Model 3 co-ownership group!', 'SYSTEM', 0),
(3, 'Welcome to EVShare', 'Welcome to the Tesla Model 3 co-ownership group!', 'SYSTEM', 0);
GO

-- Insert sample booking
INSERT INTO UsageBooking (UserId, VehicleId, StartDateTime, EndDateTime, Status, Purpose) VALUES 
(2, 1, DATEADD(HOUR, 1, GETDATE()), DATEADD(HOUR, 3, GETDATE()), 'CONFIRMED', 'Weekend trip');
GO

-- Insert sample maintenance
INSERT INTO Maintenance (VehicleId, RequestedBy, Description, EstimatedCost, MaintenanceStatus) VALUES 
(1, 4, 'Regular battery check', 500000.00, 'PENDING');
GO

-- Insert sample incident
INSERT INTO Incident (BookingId, IncidentType, Description, EstimatedCost, Status) VALUES 
(1, 'BATTERY_FAILURE', 'Battery drained faster than expected', 200000.00, 'REPORTED');
GO

-- Insert sample vehicle check
INSERT INTO VehicleCheck (BookingId, CheckType, Odometer, BatteryLevel, Cleanliness, Status) VALUES 
(1, 'PRE_USE', 15000, 85.50, 'CLEAN', 'PASSED');
GO

-- Insert sample voting
INSERT INTO Voting (GroupId, Title, Description, VotingType, Options, Status, CreatedBy) VALUES 
(1, 'Battery Upgrade Decision', 'Should we upgrade to a higher capacity battery?', 'BATTERY_UPGRADE', '["Yes", "No", "Maybe"]', 'ACTIVE', 2);
GO

-- Insert sample dispute
INSERT INTO Dispute (FundId, CreatedBy, DisputeType, Description, DisputedAmount, Status) VALUES 
(1, 2, 'FINANCIAL', 'Dispute over maintenance cost allocation', 100000.00, 'OPEN');
GO

-- Insert sample expense
INSERT INTO Expense (FundId, SourceType, SourceId, Description, Amount) VALUES 
(1, 'MAINTENANCE', 1, 'Battery check service', 500000.00);
GO

-- Insert sample payment
INSERT INTO Payment (UserId, FundID, Amount, PaymentMethod, Status, PaymentType) VALUES 
(2, 1, 500000.00, 'BANK_TRANSFER', 'COMPLETED', 'CONTRIBUTION');
GO

-- Insert sample OTP token
INSERT INTO OtpToken (Email, OtpCode, ExpiresAt) VALUES 
('john@example.com', '123456', DATEADD(MINUTE, 5, GETDATE()));
GO

-- Insert sample user document
INSERT INTO UserDocument (UserId, DocumentType, Side, ImageUrl, Status) VALUES 
(2, 'CITIZEN_ID', 'FRONT', 'https://example.com/citizen_id_front.jpg', 'PENDING'),
(2, 'CITIZEN_ID', 'BACK', 'https://example.com/citizen_id_back.jpg', 'PENDING'),
(2, 'DRIVER_LICENSE', 'FRONT', 'https://example.com/driver_license_front.jpg', 'PENDING'),
(2, 'DRIVER_LICENSE', 'BACK', 'https://example.com/driver_license_back.jpg', 'PENDING');
GO
