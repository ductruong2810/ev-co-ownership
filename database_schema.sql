-- =============================================
-- EV Co-ownership Database Schema for SQL Server
-- =============================================

-- Create Database
DROP DATABASE IF EXISTS EVShare;
GO
CREATE DATABASE EVShare;
GO


-- =============================================
-- 1. ROLES TABLE
-- =============================================
CREATE TABLE Roles
(
    RoleId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    RoleName NVARCHAR(30) NOT NULL UNIQUE
);
GO

-- =============================================
-- 2. USERS TABLE
-- =============================================
CREATE TABLE Users
(
    UserId       BIGINT IDENTITY (1,1) PRIMARY KEY,
    FullName     NVARCHAR(100) NOT NULL,
    Email        NVARCHAR(100) NOT NULL UNIQUE,
    PasswordHash NVARCHAR(255) NOT NULL,
    PhoneNumber  NVARCHAR(20),
    AvatarUrl    NVARCHAR(500),
    RoleId       BIGINT,
    Status       NVARCHAR(20) DEFAULT 'Active',
    CreatedAt    DATETIME2    DEFAULT GETDATE(),
    UpdatedAt    DATETIME2    DEFAULT GETDATE(),
    FOREIGN KEY (RoleId) REFERENCES Roles (RoleId)
);
GO

-- =============================================
-- 3. OWNERSHIP GROUP TABLE
-- =============================================
CREATE TABLE OwnershipGroup
(
    GroupId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupName   NVARCHAR(100) NOT NULL,
    Status      NVARCHAR(20) DEFAULT 'Pending',
    Description NVARCHAR(MAX),
    MemberCapacity INT,
    CreatedAt   DATETIME2    DEFAULT GETDATE(),
    UpdatedAt   DATETIME2    DEFAULT GETDATE()
);
GO

-- =============================================
-- 4. OWNERSHIP SHARE TABLE
-- =============================================
CREATE TABLE OwnershipShare
(
    UserId              BIGINT        NOT NULL,
    GroupId             BIGINT        NOT NULL,
    GroupRole           NVARCHAR(50) DEFAULT 'Member',
    OwnershipPercentage DECIMAL(5, 2) NOT NULL CHECK (OwnershipPercentage > 0 AND OwnershipPercentage <= 100),
    JoinDate            DATETIME2    DEFAULT GETDATE(),
    UpdatedAt           DATETIME2    DEFAULT GETDATE(),
    CONSTRAINT PK_OwnershipShare PRIMARY KEY (UserId, GroupId),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- 5. VEHICLE TABLE
-- =============================================
CREATE TABLE Vehicle
(
    VehicleId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    Brand         NVARCHAR(100),
    Model         NVARCHAR(100),
    LicensePlate  NVARCHAR(20),
    ChassisNumber NVARCHAR(30),
    QrCode        NVARCHAR(255),
    GroupId       BIGINT,
    CreatedAt     DATETIME2 DEFAULT GETDATE(),
    UpdatedAt     DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- VEHICLE IMAGES TABLE
-- =============================================
CREATE TABLE VehicleImages
(
    ImageId    BIGINT IDENTITY (1,1) PRIMARY KEY,
    VehicleId  BIGINT        NOT NULL,
    ImageUrl   NVARCHAR(500) NOT NULL,
    ImageType  NVARCHAR(20)  NOT NULL,
    UploadedAt DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId)
);
GO

-- =============================================
-- 6. CONTRACT TABLE
-- =============================================
CREATE TABLE Contract
(
    ContractId      BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId         BIGINT NOT NULL,
    ContractContent NVARCHAR(MAX),
    ContractUrl     NVARCHAR(500),
    SignedAt        DATETIME2,
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- 7. SHARED FUND TABLE
-- =============================================
CREATE TABLE SharedFund
(
    FundId       BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId      BIGINT NOT NULL,
    Balance      DECIMAL(15, 2) DEFAULT 0,
    TargetAmount DECIMAL(15, 2) DEFAULT 0,
    CreatedAt    DATETIME2      DEFAULT GETDATE(),
    UpdatedAt    DATETIME2      DEFAULT GETDATE(),
    Version      BIGINT         DEFAULT 0,
    CONSTRAINT UQ_SharedFund_Group UNIQUE (GroupId),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- 8. USAGE BOOKING TABLE
-- =============================================
CREATE TABLE UsageBooking
(
    BookingId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId        BIGINT    NOT NULL,
    VehicleId     BIGINT    NOT NULL,
    StartDateTime DATETIME2 NOT NULL,
    EndDateTime   DATETIME2 NOT NULL,
    Status        NVARCHAR(20) DEFAULT 'Pending',
    TotalDuration INT,
    Priority      INT,
    CreatedAt     DATETIME2    DEFAULT GETDATE(),
    UpdatedAt     DATETIME2    DEFAULT GETDATE(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId)
);
GO

-- =============================================
-- 9. MAINTENANCE TABLE
-- =============================================
CREATE TABLE Maintenance
(
    MaintenanceId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    VehicleId         BIGINT NOT NULL,
    RequestedBy       BIGINT NOT NULL,
    ApprovedBy        BIGINT,
    RequestDate       DATETIME2    DEFAULT GETDATE(),
    ApprovalDate      DATETIME2,
    NextDueDate       DATE,
    Description       NVARCHAR(MAX),
    EstimatedCost     DECIMAL(12, 2),
    ActualCost        DECIMAL(12, 2),
    MaintenanceStatus NVARCHAR(20) DEFAULT 'Pending',
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 10. NOTIFICATION TABLE
-- =============================================
CREATE TABLE Notification
(
    NotificationId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId           BIGINT,
    Title            NVARCHAR(255),
    Message          NVARCHAR(MAX),
    NotificationType NVARCHAR(50),
    IsRead           BIT       DEFAULT 0,
    IsDelivered      BIT       DEFAULT 0,
    CreatedAt        DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 11. INCIDENT TABLE
-- =============================================
CREATE TABLE Incident
(
    IncidentId    BIGINT IDENTITY (1,1) PRIMARY KEY,
    BookingId     BIGINT NOT NULL,
    IncidentType  NVARCHAR(50),
    Description   NVARCHAR(MAX),
    EstimatedCost DECIMAL(12, 2),
    ActualCost    DECIMAL(12, 2),
    Status        NVARCHAR(20) DEFAULT 'Reported',
    ImageUrls     NVARCHAR(MAX),
    IncidentDate  DATETIME2    DEFAULT GETDATE(),
    ResolvedDate  DATETIME2,
    ResolvedBy    BIGINT,
    Notes         NVARCHAR(1000),
    CreatedAt     DATETIME2    DEFAULT GETDATE(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId),
    FOREIGN KEY (ResolvedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 12. VEHICLE CHECK TABLE
-- =============================================
CREATE TABLE VehicleCheck
(
    Id           BIGINT IDENTITY (1,1) PRIMARY KEY,
    BookingId    BIGINT,
    CheckType    NVARCHAR(20),
    Odometer     INT,
    BatteryLevel DECIMAL(5, 2),
    Cleanliness  NVARCHAR(20),
    Notes        NVARCHAR(MAX),
    Issues       NVARCHAR(MAX),
    Status       NVARCHAR(20),
    CreatedAt    DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId)
);
GO

-- =============================================
-- 13. USER DOCUMENT TABLE
-- =============================================
CREATE TABLE UserDocument
(
    DocumentId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId       BIGINT        NOT NULL,
    DocumentType NVARCHAR(20),
    Side         NVARCHAR(10),
    ImageUrl     NVARCHAR(500) NOT NULL,
    Status       NVARCHAR(20) DEFAULT 'Pending',
    ReviewNote   NVARCHAR(MAX),
    ReviewedBy   BIGINT,
    CreatedAt    DATETIME2    DEFAULT GETDATE(),
    UpdatedAt    DATETIME2    DEFAULT GETDATE(),
    FOREIGN KEY (ReviewedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 14. VOTING TABLE
-- =============================================
CREATE TABLE Voting
(
    VotingId    BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId     BIGINT,
    Title       NVARCHAR(255),
    Description NVARCHAR(MAX),
    VotingType  NVARCHAR(50),
    Options     NVARCHAR(MAX),
    Results     NVARCHAR(MAX),
    Deadline    DATETIME2,
    Status      NVARCHAR(20) DEFAULT 'Active',
    CreatedBy   BIGINT,
    CreatedAt   DATETIME2    DEFAULT GETDATE(),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- 15. DISPUTE TABLE
-- =============================================
CREATE TABLE Dispute
(
    DisputeId         BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId            BIGINT NOT NULL,
    CreatedBy         BIGINT,
    DisputeType       NVARCHAR(50),
    RelatedEntityType NVARCHAR(50),
    RelatedEntityId   BIGINT,
    Description       NVARCHAR(MAX),
    DisputedAmount    DECIMAL(12, 2),
    Notes             NVARCHAR(1000),
    ResolutionAmount  DECIMAL(12, 2),
    Status            NVARCHAR(20) DEFAULT 'Open',
    ResolvedBy        BIGINT,
    CreatedAt         DATETIME2    DEFAULT GETDATE(),
    UpdatedAt         DATETIME2    DEFAULT GETDATE(),
    ResolvedAt        DATETIME2,
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (ResolvedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 15b. DISPUTE ADD-ONS TABLES
-- =============================================
CREATE TABLE DisputeTicket
(
    TicketId           BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId          BIGINT         NOT NULL,
    Priority           NVARCHAR(20)   NOT NULL DEFAULT 'MEDIUM',
    AssignedTo         BIGINT,
    OpenedAt           DATETIME2      NOT NULL DEFAULT GETDATE(),
    DueFirstResponseAt DATETIME2,
    DueResolutionAt    DATETIME2,
    ClosedAt           DATETIME2,
    FOREIGN KEY (DisputeId) REFERENCES Dispute (DisputeId),
    FOREIGN KEY (AssignedTo) REFERENCES Users (UserId)
);
GO

CREATE TABLE DisputeEvent
(
    EventId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    TicketId    BIGINT,
    ActorUserId BIGINT,
    ActorRole   NVARCHAR(20),
    EventType   NVARCHAR(40)   NOT NULL,
    OldValue    NVARCHAR(200),
    NewValue    NVARCHAR(200),
    Note        NVARCHAR(MAX),
    CreatedAt   DATETIME2      NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (TicketId) REFERENCES DisputeTicket (TicketId)
);
GO

CREATE TABLE DisputeAttachment
(
    AttachmentId BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId    BIGINT       NOT NULL,
    FileName     NVARCHAR(255) NOT NULL,
    MimeType     NVARCHAR(100) NOT NULL,
    SizeBytes    BIGINT        NOT NULL,
    StorageUrl   NVARCHAR(1000) NOT NULL,
    Sha256       NVARCHAR(64),
    ThumbnailUrl NVARCHAR(1000),
    MetaJson     NVARCHAR(MAX),
    UploadedBy   BIGINT        NOT NULL,
    Visibility   NVARCHAR(20)  NOT NULL DEFAULT 'USER',
    Status       NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CreatedAt    DATETIME2     NOT NULL DEFAULT GETDATE(),
    DeletedAt    DATETIME2,
    FOREIGN KEY (DisputeId) REFERENCES Dispute (DisputeId),
    FOREIGN KEY (UploadedBy) REFERENCES Users (UserId)
);
GO

CREATE TABLE Refund
(
    RefundId           BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId          BIGINT         NOT NULL,
    Amount             DECIMAL(15, 2) NOT NULL,
    Method             NVARCHAR(30)   NOT NULL,
    TxnRef             NVARCHAR(100),
    Status             NVARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    CreatedAt          DATETIME2      NOT NULL DEFAULT GETDATE(),
    SettledAt          DATETIME2,
    Note               NVARCHAR(MAX),
    Provider           NVARCHAR(20)   DEFAULT 'VNPAY',
    ProviderTxnRef     NVARCHAR(100),
    ProviderRefundRef  NVARCHAR(100),
    ReasonCode         NVARCHAR(20),
    Channel            NVARCHAR(20),
    RawResponse        NVARCHAR(MAX),
    FOREIGN KEY (DisputeId) REFERENCES Dispute (DisputeId)
);
GO

CREATE TABLE JournalEntry
(
    EntryId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId   BIGINT       NOT NULL,
    FundId      BIGINT       NOT NULL,
    AccountCode NVARCHAR(50) NOT NULL,
    Debit       DECIMAL(15, 2) NOT NULL DEFAULT 0,
    Credit      DECIMAL(15, 2) NOT NULL DEFAULT 0,
    Memo        NVARCHAR(255),
    PostedAt    DATETIME2,
    CreatedAt   DATETIME2    NOT NULL DEFAULT GETDATE(),
    FOREIGN KEY (DisputeId) REFERENCES Dispute (DisputeId),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId)
);
GO

-- =============================================
-- 16. EXPENSE TABLE
-- =============================================
CREATE TABLE Expense
(
    ExpenseId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId      BIGINT,
    SourceType  NVARCHAR(50),
    SourceId    BIGINT,
    Description NVARCHAR(MAX),
    Amount      DECIMAL(12, 2) NOT NULL,
    ExpenseDate DATETIME2 DEFAULT GETDATE(),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId)
);
GO

-- =============================================
-- 17. PAYMENT TABLE
-- =============================================
CREATE TABLE Payment
(
    PaymentId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    PayerUserId      BIGINT         NOT NULL,
    FundId           BIGINT         NOT NULL,
    Amount           DECIMAL(12, 2) NOT NULL,
    PaymentDate      DATETIME2    DEFAULT GETDATE(),
    PaymentMethod    NVARCHAR(50),
    Status           NVARCHAR(20) DEFAULT 'Pending',
    TransactionCode  NVARCHAR(100),
    ProviderResponse NVARCHAR(MAX),
    PaymentType      NVARCHAR(20) NOT NULL,
    Version          BIGINT       DEFAULT 0,
    PaymentCategory  NVARCHAR(20) DEFAULT 'GROUP',
    ChargedUserId    BIGINT,
    SourceDisputeId  BIGINT,
    PersonalReason   NVARCHAR(MAX),
    FOREIGN KEY (PayerUserId) REFERENCES Users (UserId),
    FOREIGN KEY (ChargedUserId) REFERENCES Users (UserId),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (SourceDisputeId) REFERENCES Dispute (DisputeId)
);
GO

-- Enforce: when PaymentCategory = 'PERSONAL' then ChargedUserId must be NOT NULL
ALTER TABLE Payment ADD CONSTRAINT CK_Payment_Personal
CHECK (PaymentCategory <> 'PERSONAL' OR ChargedUserId IS NOT NULL);
GO

-- =============================================
-- 18. FINANCIAL REPORT TABLE
-- =============================================
CREATE TABLE FinancialReport
(
    ReportId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId       BIGINT         NOT NULL,
    ReportMonth  INT,
    ReportYear   INT,
    TotalIncome  DECIMAL(15, 2),
    TotalExpense DECIMAL(15, 2),
    GeneratedBy  BIGINT         NOT NULL,
    CreatedAt    DATETIME2    DEFAULT GETDATE(),
    UpdatedAt    DATETIME2    DEFAULT GETDATE(),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (GeneratedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 19. OTP TOKEN TABLE
-- =============================================
CREATE TABLE OtpToken
(
    TokenId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    Email     NVARCHAR(100) NOT NULL,
    OtpCode   NVARCHAR(6)   NOT NULL,
    ExpiresAt DATETIME2     NOT NULL,
    IsUsed    BIT       DEFAULT 0,
    CreatedAt DATETIME2 DEFAULT GETDATE()
);
GO

-- =============================================
-- CREATE INDEXES
-- =============================================

-- User indexes
CREATE INDEX IX_Users_Email ON Users (Email);
CREATE INDEX IX_Users_RoleId ON Users (RoleId);
GO

-- Ownership indexes (optional – PK already covers both)
CREATE INDEX IX_OwnershipShare_GroupId ON OwnershipShare (GroupId);
GO

-- Vehicle indexes
CREATE INDEX IX_Vehicle_GroupId ON Vehicle (GroupId);
CREATE INDEX IX_Vehicle_LicensePlate ON Vehicle (LicensePlate);
GO

-- VehicleImages indexes
CREATE INDEX IX_VehicleImages_VehicleId ON VehicleImages (VehicleId);
CREATE INDEX IX_VehicleImages_ImageType ON VehicleImages (ImageType);
GO

-- Booking indexes
CREATE INDEX IX_UsageBooking_UserId ON UsageBooking (UserId);
CREATE INDEX IX_UsageBooking_VehicleId ON UsageBooking (VehicleId);
CREATE INDEX IX_UsageBooking_StartDateTime ON UsageBooking (StartDateTime);
CREATE INDEX IX_UsageBooking_Status ON UsageBooking (Status);
GO

-- Maintenance indexes
CREATE INDEX IX_Maintenance_VehicleId ON Maintenance (VehicleId);
CREATE INDEX IX_Maintenance_RequestedBy ON Maintenance (RequestedBy);
CREATE INDEX IX_Maintenance_Status ON Maintenance (MaintenanceStatus);
GO

-- Notification indexes
CREATE INDEX IX_Notification_UserId ON Notification (UserId);
CREATE INDEX IX_Notification_IsRead ON Notification (IsRead);
GO

-- Incident indexes
CREATE INDEX IX_Incident_BookingId ON Incident (BookingId);
CREATE INDEX IX_Incident_ResolvedBy ON Incident (ResolvedBy);
CREATE INDEX IX_Incident_Status ON Incident (Status);
GO

-- VehicleCheck indexes
CREATE INDEX IX_VehicleCheck_BookingId ON VehicleCheck (BookingId);
CREATE INDEX IX_VehicleCheck_Status ON VehicleCheck (Status);
GO

-- UserDocument indexes
CREATE INDEX IX_UserDocument_UserId ON UserDocument (UserId);
CREATE INDEX IX_UserDocument_Status ON UserDocument (Status);
GO

-- Voting indexes
CREATE INDEX IX_Voting_GroupId ON Voting (GroupId);
CREATE INDEX IX_Voting_Status ON Voting (Status);
GO

-- Dispute indexes
CREATE INDEX IX_Dispute_FundId ON Dispute (FundId);
CREATE INDEX IX_Dispute_Status ON Dispute (Status);
GO

-- FinancialReport indexes
CREATE INDEX IX_FinancialReport_FundId ON FinancialReport (FundId);
CREATE INDEX IX_FinancialReport_GeneratedBy ON FinancialReport (GeneratedBy);
CREATE INDEX IX_FinancialReport_ReportYear_Month ON FinancialReport (ReportYear, ReportMonth);
GO

-- Payment indexes (DBML additions)
CREATE INDEX IX_Payment_Category ON Payment (PaymentCategory);
CREATE INDEX IX_Payment_ChargedUserId ON Payment (ChargedUserId);
CREATE INDEX IX_Payment_SourceDisputeId ON Payment (SourceDisputeId);
GO

-- Dispute add-ons indexes
CREATE INDEX IX_DisputeTicket_DisputeId ON DisputeTicket (DisputeId);
CREATE INDEX IX_DisputeTicket_AssignedTo ON DisputeTicket (AssignedTo);
CREATE INDEX IX_DisputeTicket_Open ON DisputeTicket (DisputeId, ClosedAt);
GO
CREATE INDEX IX_DisputeEvent_TicketId ON DisputeEvent (TicketId);
CREATE INDEX IX_DisputeEvent_EventType ON DisputeEvent (EventType);
GO
CREATE INDEX IX_DisputeAttachment_DisputeId ON DisputeAttachment (DisputeId);
CREATE INDEX IX_DisputeAttachment_UploadedBy ON DisputeAttachment (UploadedBy);
GO
CREATE INDEX IX_Refund_DisputeId ON Refund (DisputeId);
CREATE INDEX IX_Refund_Status ON Refund (Status);
GO
CREATE INDEX IX_Journal_FundId ON JournalEntry (FundId);
CREATE INDEX IX_Journal_DisputeId ON JournalEntry (DisputeId);
GO

-- =============================================
-- SEED DATA (basic, for demo)
-- =============================================

-- Roles
INSERT INTO Roles(RoleName)
VALUES ('Co_owner'),
       ('Staff'),
       ('Admin'),
       ('Technician');

-- Users
INSERT INTO Users(FullName, Email, PasswordHash, PhoneNumber, RoleId, Status)
VALUES ('Alice Co-owner', 'alice@example.com', '$2a$12$jDAn4z57D6u5Pr3Dlzu2mebpyxF4XiJjAYQQgUtpi2iMg3aqNeIN6',
        '0900000001', 1, 'ACTIVE'),
       ('Bob Staff', 'bob@example.com', '$2a$12$HOG0QPsIqB1xmu5GuxCeMugoxHxU2LHSDWsbges5uJ5WnNGBER8Qm', '0900000002', 2,
        'ACTIVE'),
       ('Carol Admin', 'carol@example.com', '$2a$12$zQ7SSv.Z6SHtN1jRWG4O9OQzqvgyO5kxH11ur/oO2yYpgl93VJLQW',
        '0900000003', 3, 'ACTIVE'),
       ('Terry Technician', 'terry@example.com', '$2a$12$gRZ7aGctqj.2.WN19t8X3uWQUFON/i9m18fMwifRewcJNurCmWEwS',
        '0900000004', 4, 'ACTIVE');

-- Group
INSERT INTO OwnershipGroup(GroupName, Status, Description)
VALUES ('EV Group A', 'ACTIVE', 'Nh F3m s F4 h EFu EV');

-- Shares (example A:50%, B:30%, C:20%)
-- Người tạo group là Admin của group
INSERT INTO OwnershipShare(UserId, GroupId, GroupRole, OwnershipPercentage)
VALUES (1, 1, 'Admin', 50.00);

-- Bổ sung tỉ lệ sở hữu cho user 2,3
-- Chỉ Co_owner mới được đồng sở hữu: tạo thêm 2 Co_owner mới (user 5,6)
INSERT INTO Users (FullName, Email, PasswordHash, PhoneNumber, RoleId, Status)
VALUES ('David Co-owner', 'david@example.com', '$2a$12$jxfqrPEtC6qidnrlRfYxSOM9JPdUj24DGnblLX.PnN7dckxaZkwIK',
        '0900000005', 1, 'Active'),
       ('Emma  Co-owner', 'emma@example.com', '$2a$12$wAfwDpecaFzwNh07OOieZOmSwbrP.Bf2B7dgC/58t0EV1QXNskxlW',
        '0900000006', 1, 'Active');

-- Gán tỉ lệ sở hữu cho user 5,6 thay vì Staff/Admin
INSERT INTO OwnershipShare(UserId, GroupId, GroupRole, OwnershipPercentage)
VALUES (5, 1, 'Member', 30.00),
       (6, 1, 'Member', 20.00);

-- Vehicle
INSERT INTO Vehicle(Brand, Model, LicensePlate, ChassisNumber, GroupId)
VALUES ('VinFast', 'VF e34', '29A-123.45', 'RLHRE7EXXXXXXXX', 1);

-- Images
INSERT INTO VehicleImages(VehicleId, ImageUrl, ImageType)
VALUES (1, 'https://example.com/vehicle.jpg', 'VEHICLE'),
       (1, 'https://example.com/scratch1.jpg', 'SCRATCH');

-- Fund
INSERT INTO SharedFund(GroupId, Balance, TargetAmount, Version)
VALUES (1, 0, 0, 0);

-- Booking sample
INSERT INTO UsageBooking(UserId, VehicleId, StartDateTime, EndDateTime, Status)
VALUES (1, 1, DATEADD(hour, -1, GETDATE()), DATEADD(hour, 2, GETDATE()), 'PENDING');

-- Payments demo (Pending, Completed, Failed, Refunded)
INSERT INTO Payment(PayerUserId, FundId, Amount, PaymentMethod, Status, PaymentType, TransactionCode, PaymentCategory)
VALUES (1, 1, 1000000, 'BANK_TRANSFER', 'Pending', 'CONTRIBUTION', 'TXN-P-001', 'GROUP'),
       (1, 1, 1500000, 'BANK_TRANSFER', 'Completed', 'CONTRIBUTION', 'TXN-C-001', 'GROUP'),
       (1, 1, 200000, 'BANK_TRANSFER', 'Failed', 'MAINTENANCE_FEE', 'TXN-F-001', 'GROUP'),
       (1, 1, 1500000, 'BANK_TRANSFER', 'Refunded', 'CONTRIBUTION', 'TXN-R-001', 'GROUP');

-- Expense demo
INSERT INTO Expense(FundId, SourceType, SourceId, Description, Amount)
VALUES (1, 'MAINTENANCE', NULL, N'Bảo dưỡng định kỳ', 300000);

-- Dispute demo
INSERT INTO Dispute(FundId, DisputeType, RelatedEntityType, Description, DisputedAmount, Status)
VALUES (1, 'FINANCIAL', 'PAYMENT', N'Tranh chấp thanh toán', 100000, 'Open');

-- Incident demo
INSERT INTO Incident(BookingId, IncidentType, Description, Status)
VALUES (1, 'DAMAGE', N'Trầy xước nhẹ', 'Reported');

-- VehicleCheck demo (sau khi trả xe)
INSERT INTO VehicleCheck(BookingId, CheckType, Odometer, BatteryLevel, Cleanliness, Status)
VALUES (1, 'POST_USE', 12000, 85.0, 'CLEAN', 'PASSED');

-- FinancialReport demo (báo cáo tài chính tháng 12/2024)
INSERT INTO FinancialReport(FundId, ReportMonth, ReportYear, TotalIncome, TotalExpense, GeneratedBy)
VALUES (1, 12, 2024, 5000000, 300000, 3);

-- FinancialReport demo (báo cáo tài chính tháng 11/2024)
INSERT INTO FinancialReport(FundId, ReportMonth, ReportYear, TotalIncome, TotalExpense, GeneratedBy)
VALUES (1, 11, 2024, 4500000, 250000, 3);


-- =============================================
-- END OF SCHEMA
-- =============================================
