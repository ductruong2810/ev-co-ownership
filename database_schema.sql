-- =============================================
-- EV Co-ownership Database Schema (SQL Server)
-- Clean recreate (no dbo. prefix, GroupName UNIQUE, English-only seeds)
-- =============================================

-- Recreate Database (run from master)
USE master;
GO
IF DB_ID(N'EVShare') IS NOT NULL
    BEGIN
        ALTER DATABASE EVShare SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
        DROP DATABASE EVShare;
    END
GO
CREATE DATABASE EVShare;
GO
USE EVShare;
GO

-- =============================================
-- 1) ROLES
-- =============================================
CREATE TABLE Roles
(
    RoleId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    RoleName NVARCHAR(30) NOT NULL UNIQUE
);
GO

-- =============================================
-- 2) USERS
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
    Status       NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    CreatedAt    DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (RoleId) REFERENCES Roles (RoleId)
);
GO

-- =============================================
-- 3) OWNERSHIP GROUP  (GroupName must be unique)
-- =============================================
CREATE TABLE OwnershipGroup
(
    GroupId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupName      NVARCHAR(100) NOT NULL,
    Status         NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    Description    NVARCHAR(MAX),
    MemberCapacity INT           NULL,
    CreatedAt      DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt      DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_OwnershipGroup_GroupName UNIQUE (GroupName)
);
GO

-- =============================================
-- 4) OWNERSHIP SHARE
-- =============================================
CREATE TABLE OwnershipShare
(
    UserId              BIGINT        NOT NULL,
    GroupId             BIGINT        NOT NULL,
    GroupRole           NVARCHAR(50)  NOT NULL DEFAULT 'MEMBER',
    OwnershipPercentage DECIMAL(5, 2) NOT NULL
        CHECK (OwnershipPercentage >= 0 AND OwnershipPercentage <= 100),
    JoinDate            DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    DepositStatus       NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    UpdatedAt           DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_OwnershipShare PRIMARY KEY (UserId, GroupId),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- 5) VEHICLE
-- =============================================
CREATE TABLE Vehicle
(
    VehicleId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    Brand         NVARCHAR(100),
    Model         NVARCHAR(100),
    LicensePlate  NVARCHAR(20),
    ChassisNumber NVARCHAR(30),
    VehicleValue  DECIMAL(15, 2) NULL,
    GroupId       BIGINT,
    CreatedAt     DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt     DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- 6) VEHICLE IMAGES
-- =============================================
CREATE TABLE VehicleImages
(
    ImageId         BIGINT IDENTITY (1,1) PRIMARY KEY,
    VehicleId       BIGINT        NOT NULL,
    ImageUrl        NVARCHAR(500) NOT NULL,
    ImageType       NVARCHAR(20)  NOT NULL,
    ApprovalStatus  NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ApprovedBy      BIGINT        NULL,
    ApprovedAt      DATETIME2(7)  NULL,
    RejectionReason NVARCHAR(500) NULL,
    UploadedAt      DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 7) CONTRACT TEMPLATE
-- =============================================
CREATE TABLE ContractTemplate
(
    TemplateId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    TemplateName NVARCHAR(100) NOT NULL,
    Description  NVARCHAR(MAX),
    HtmlTemplate NVARCHAR(MAX) NOT NULL,
    IsActive     BIT           NOT NULL DEFAULT 1,
    CreatedAt    DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME()
);
GO

-- =============================================
-- 8) CONTRACT
-- =============================================
CREATE TABLE Contract
(
    ContractId            BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId               BIGINT        NOT NULL,
    TemplateId            BIGINT,
    StartDate             DATE,
    EndDate               DATE,
    Terms                 NVARCHAR(MAX),
    RequiredDepositAmount DECIMAL(15, 2),
    IsActive              BIT                    DEFAULT 1,
    CreatedAt             DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt             DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),

    -- Contract Approval Fields (added after approval workflow implementation)
    ApprovalStatus        NVARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING, APPROVED, REJECTED
    ApprovedBy            BIGINT        NULL,                       -- Staff/Admin who approved the contract
    ApprovedAt            DATETIME2(7)  NULL,                       -- When the contract was approved/rejected
    RejectionReason       NVARCHAR(500) NULL,                       -- Reason for rejection if status is REJECTED

    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId),
    FOREIGN KEY (TemplateId) REFERENCES ContractTemplate (TemplateId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 8) SHARED FUND
-- =============================================
CREATE TABLE SharedFund
(
    FundId       BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId      BIGINT         NOT NULL,
    Balance      DECIMAL(15, 2) NOT NULL DEFAULT 0,
    TargetAmount DECIMAL(15, 2) NOT NULL DEFAULT 0,
    CreatedAt    DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    Version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT UQ_SharedFund_Group UNIQUE (GroupId),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- =============================================
-- 9) USAGE BOOKING
-- =============================================
CREATE TABLE UsageBooking
(
    BookingId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId        BIGINT       NOT NULL,
    VehicleId     BIGINT       NOT NULL,
    StartDateTime DATETIME2(7) NOT NULL,
    EndDateTime   DATETIME2(7) NOT NULL,
    Status        NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
    TotalDuration INT,
    Priority      INT,
    CreatedAt     DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt     DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId)
);
GO

-- =============================================
-- 10) MAINTENANCE
-- =============================================
CREATE TABLE Maintenance
(
    MaintenanceId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    VehicleId         BIGINT       NOT NULL,
    RequestedBy       BIGINT       NOT NULL,
    ApprovedBy        BIGINT       NULL,
    RequestDate       DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    ApprovalDate      DATETIME2(7),
    NextDueDate       DATE,
    Description       NVARCHAR(MAX),
    EstimatedCost     DECIMAL(12, 2),
    ActualCost        DECIMAL(12, 2),
    MaintenanceStatus NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId),
    FOREIGN KEY (RequestedBy) REFERENCES Users (UserId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 11) NOTIFICATION
-- =============================================
CREATE TABLE Notification
(
    NotificationId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId           BIGINT,
    Title            NVARCHAR(255),
    [Message]        NVARCHAR(MAX),
    NotificationType NVARCHAR(50),
    IsRead           BIT          NOT NULL DEFAULT 0,
    IsDelivered      BIT          NOT NULL DEFAULT 0,
    CreatedAt        DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 12) INCIDENT
-- =============================================
CREATE TABLE Incident
(
    IncidentId    BIGINT IDENTITY (1,1) PRIMARY KEY,
    BookingId     BIGINT       NOT NULL,
    IncidentType  NVARCHAR(50),
    Description   NVARCHAR(MAX),
    EstimatedCost DECIMAL(12, 2),
    ActualCost    DECIMAL(12, 2),
    Status        NVARCHAR(20) NOT NULL DEFAULT 'REPORTED',
    ImageUrls     NVARCHAR(MAX),
    IncidentDate  DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    ResolvedDate  DATETIME2(7),
    ResolvedBy    BIGINT,
    Notes         NVARCHAR(1000),
    CreatedAt     DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId),
    FOREIGN KEY (ResolvedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 13) VEHICLE CHECK
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
    CreatedAt    DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId)
);
GO

-- =============================================
-- 14) USER DOCUMENT
-- =============================================
CREATE TABLE UserDocument
(
    DocumentId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId       BIGINT        NOT NULL,
    DocumentType NVARCHAR(20),
    Side         NVARCHAR(10),
    ImageUrl     NVARCHAR(500) NOT NULL,
    Status       NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    ReviewNote   NVARCHAR(MAX),
    ReviewedBy   BIGINT,
    CreatedAt    DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (ReviewedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 15) VOTING
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
    Deadline    DATETIME2(7),
    Status      NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CreatedBy   BIGINT,
    CreatedAt   DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId),
    FOREIGN KEY (CreatedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 16) DISPUTE
-- =============================================
CREATE TABLE Dispute
(
    DisputeId         BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId            BIGINT       NOT NULL,
    CreatedBy         BIGINT,
    DisputeType       NVARCHAR(50),
    RelatedEntityType NVARCHAR(50),
    RelatedEntityId   BIGINT,
    Description       NVARCHAR(MAX),
    DisputedAmount    DECIMAL(12, 2),
    Notes             NVARCHAR(1000),
    Resolution        NVARCHAR(MAX),
    ResolutionAmount  DECIMAL(12, 2),
    Status            NVARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ResolvedBy        BIGINT,
    CreatedAt         DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    ResolvedAt        DATETIME2(7),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (CreatedBy) REFERENCES Users (UserId),
    FOREIGN KEY (ResolvedBy) REFERENCES Users (UserId)
);
GO

-- 16b) DISPUTE ADD-ONS
CREATE TABLE DisputeTicket
(
    TicketId           BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId          BIGINT       NOT NULL,
    Priority           NVARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    AssignedTo         BIGINT,
    OpenedAt           DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    DueFirstResponseAt DATETIME2(7),
    DueResolutionAt    DATETIME2(7),
    ClosedAt           DATETIME2(7),
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
    EventType   NVARCHAR(40) NOT NULL,
    OldValue    NVARCHAR(200),
    NewValue    NVARCHAR(200),
    Note        NVARCHAR(MAX),
    CreatedAt   DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (TicketId) REFERENCES DisputeTicket (TicketId),
    FOREIGN KEY (ActorUserId) REFERENCES Users (UserId)
);
GO

CREATE TABLE DisputeAttachment
(
    AttachmentId BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId    BIGINT         NOT NULL,
    FileName     NVARCHAR(255)  NOT NULL,
    MimeType     NVARCHAR(100)  NOT NULL,
    SizeBytes    BIGINT         NOT NULL,
    StorageUrl   NVARCHAR(1000) NOT NULL,
    Sha256       NVARCHAR(64),
    ThumbnailUrl NVARCHAR(1000),
    MetaJson     NVARCHAR(MAX),
    UploadedBy   BIGINT         NOT NULL,
    Visibility   NVARCHAR(20)   NOT NULL DEFAULT 'USER',
    Status       NVARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    CreatedAt    DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    DeletedAt    DATETIME2(7),
    FOREIGN KEY (DisputeId) REFERENCES Dispute (DisputeId),
    FOREIGN KEY (UploadedBy) REFERENCES Users (UserId)
);
GO

CREATE TABLE Refund
(
    RefundId          BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId         BIGINT         NOT NULL,
    Amount            DECIMAL(15, 2) NOT NULL,
    Method            NVARCHAR(30)   NOT NULL,
    TxnRef            NVARCHAR(100),
    Status            NVARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    CreatedAt         DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    SettledAt         DATETIME2(7),
    Note              NVARCHAR(MAX),
    Provider          NVARCHAR(20)            DEFAULT 'VNPAY',
    ProviderTxnRef    NVARCHAR(100),
    ProviderRefundRef NVARCHAR(100),
    ReasonCode        NVARCHAR(20),
    Channel           NVARCHAR(20),
    RawResponse       NVARCHAR(MAX),
    FOREIGN KEY (DisputeId) REFERENCES Dispute (DisputeId)
);
GO

CREATE TABLE JournalEntry
(
    EntryId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    DisputeId   BIGINT         NOT NULL,
    FundId      BIGINT         NOT NULL,
    AccountCode NVARCHAR(50)   NOT NULL,
    Debit       DECIMAL(15, 2) NOT NULL DEFAULT 0,
    Credit      DECIMAL(15, 2) NOT NULL DEFAULT 0,
    Memo        NVARCHAR(255),
    PostedAt    DATETIME2(7),
    CreatedAt   DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (DisputeId) REFERENCES Dispute (DisputeId),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId)
);
GO

-- =============================================
-- 17) EXPENSE
-- =============================================
CREATE TABLE Expense
(
    ExpenseId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId      BIGINT         NOT NULL,
    SourceType  NVARCHAR(50),
    SourceId    BIGINT,
    Description NVARCHAR(MAX),
    Amount      DECIMAL(12, 2) NOT NULL,
    ExpenseDate DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId)
);
GO

-- =============================================
-- 18) PAYMENT
-- =============================================
CREATE TABLE Payment
(
    PaymentId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    PayerUserId      BIGINT         NOT NULL,
    FundId           BIGINT         NOT NULL,
    Amount           DECIMAL(12, 2) NOT NULL,
    PaymentDate      DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    PaymentMethod    NVARCHAR(50),
    Status           NVARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    TransactionCode  NVARCHAR(100),
    ProviderResponse NVARCHAR(MAX),
    PaymentType      NVARCHAR(20)   NOT NULL,
    Version          BIGINT         NOT NULL DEFAULT 0,
    PaymentCategory  NVARCHAR(20)   NOT NULL DEFAULT 'GROUP', -- GROUP | PERSONAL
    ChargedUserId    BIGINT,
    SourceDisputeId  BIGINT,
    PersonalReason   NVARCHAR(MAX),
    FOREIGN KEY (PayerUserId) REFERENCES Users (UserId),
    FOREIGN KEY (ChargedUserId) REFERENCES Users (UserId),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (SourceDisputeId) REFERENCES Dispute (DisputeId)
);
GO
ALTER TABLE Payment
    ADD CONSTRAINT CK_Payment_Personal
        CHECK (PaymentCategory <> 'PERSONAL' OR ChargedUserId IS NOT NULL);
GO

-- =============================================
-- 19) FINANCIAL REPORT
-- =============================================
CREATE TABLE FinancialReport
(
    ReportId     BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId       BIGINT       NOT NULL,
    ReportMonth  INT,
    ReportYear   INT,
    TotalIncome  DECIMAL(15, 2),
    TotalExpense DECIMAL(15, 2),
    GeneratedBy  BIGINT       NOT NULL,
    CreatedAt    DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (GeneratedBy) REFERENCES Users (UserId)
);
GO

CREATE TABLE VoteRecord
(
    VoteRecordId   BIGINT IDENTITY(1,1) PRIMARY KEY,
    VotingId       BIGINT       NOT NULL,
    UserId         BIGINT       NOT NULL,
    SelectedOption NVARCHAR(50) NOT NULL,
    VotedAt        DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (VotingId) REFERENCES Voting(VotingId),
    FOREIGN KEY (UserId) REFERENCES Users(UserId),
    CONSTRAINT UQ_VoteRecord UNIQUE (VotingId, UserId)
);

CREATE INDEX IX_VoteRecord_VotingId ON VoteRecord(VotingId);
CREATE INDEX IX_VoteRecord_UserId ON VoteRecord(UserId);

ALTER TABLE Voting
    ADD RelatedExpenseId BIGINT NULL,
        EstimatedAmount DECIMAL(12,2) NULL;

ALTER TABLE Voting
    ADD CONSTRAINT FK_Voting_Expense
        FOREIGN KEY (RelatedExpenseId) REFERENCES Expense(ExpenseId);


-- =============================================
-- INDEXES
-- =============================================

-- Users
CREATE INDEX IX_Users_Email ON Users (Email);
CREATE INDEX IX_Users_RoleId ON Users (RoleId);

-- OwnershipShare
CREATE INDEX IX_OwnershipShare_GroupId ON OwnershipShare (GroupId);

-- Vehicle
CREATE INDEX IX_Vehicle_GroupId ON Vehicle (GroupId);
CREATE INDEX IX_Vehicle_LicensePlate ON Vehicle (LicensePlate);

-- VehicleImages
CREATE INDEX IX_VehicleImages_VehicleId ON VehicleImages (VehicleId);
CREATE INDEX IX_VehicleImages_ImageType ON VehicleImages (ImageType);

-- Contract
CREATE INDEX IX_Contract_ApprovalStatus ON Contract (ApprovalStatus);
CREATE INDEX IX_Contract_ApprovedBy ON Contract (ApprovedBy);

-- UsageBooking
CREATE INDEX IX_UsageBooking_UserId ON UsageBooking (UserId);
CREATE INDEX IX_UsageBooking_VehicleId ON UsageBooking (VehicleId);
CREATE INDEX IX_UsageBooking_StartDateTime ON UsageBooking (StartDateTime);
CREATE INDEX IX_UsageBooking_Status ON UsageBooking (Status);

-- Maintenance
CREATE INDEX IX_Maintenance_VehicleId ON Maintenance (VehicleId);
CREATE INDEX IX_Maintenance_RequestedBy ON Maintenance (RequestedBy);
CREATE INDEX IX_Maintenance_Status ON Maintenance (MaintenanceStatus);

-- Notification
CREATE INDEX IX_Notification_UserId ON Notification (UserId);
CREATE INDEX IX_Notification_IsRead ON Notification (IsRead);

-- Incident
CREATE INDEX IX_Incident_BookingId ON Incident (BookingId);
CREATE INDEX IX_Incident_ResolvedBy ON Incident (ResolvedBy);
CREATE INDEX IX_Incident_Status ON Incident (Status);

-- VehicleCheck
CREATE INDEX IX_VehicleCheck_BookingId ON VehicleCheck (BookingId);
CREATE INDEX IX_VehicleCheck_Status ON VehicleCheck (Status);

-- UserDocument
CREATE INDEX IX_UserDocument_UserId ON UserDocument (UserId);
CREATE INDEX IX_UserDocument_Status ON UserDocument (Status);

-- Voting
CREATE INDEX IX_Voting_GroupId ON Voting (GroupId);
CREATE INDEX IX_Voting_Status ON Voting (Status);


-- FinancialReport
CREATE INDEX IX_FinancialReport_FundId ON FinancialReport (FundId);
CREATE INDEX IX_FinancialReport_GeneratedBy ON FinancialReport (GeneratedBy);
CREATE INDEX IX_FinancialReport_ReportYearMonth ON FinancialReport (ReportYear, ReportMonth);
GO

-- =============================================
-- SEED DATA (English-only)
-- =============================================

-- Roles
INSERT INTO Roles(RoleName)
VALUES ('CO_OWNER'),
       ('STAFF'),
       ('ADMIN'),
       ('TECHNICIAN');

-- Users
INSERT INTO Users(FullName, Email, PasswordHash, PhoneNumber, RoleId, Status)
VALUES (N'Alice Co-owner', 'alice@example.com', '$2a$12$0oCgkhJuoBdriN0dnc38wuA8Brpio3ixZXUHpnBtWwqT1tU4ikgzG',
        '0900000001', 1, 'ACTIVE'),
       (N'Bob Staff', 'bob@example.com', '$2a$12$3RHV1LDugSQS0IFAi1PF9OR7WWNg/fQ5R2GmIDJ5zaGtQjEh3g6ru', '0900000002',
        2, 'ACTIVE'),
       (N'Carol Admin', 'carol@example.com', '$2a$12$/UNH81MN3.ItR4MIkNIMd.Rh0j6nGPSH0p.wz/1pSBrG77tVRf7OW',
        '0900000003', 3, 'ACTIVE'),
       (N'Terry Technician', 'terry@example.com', '$2a$12$fK3gL5uSa5/XgWGoPf8VcusZp6KcddqjcmpLO0P8pXmj.0ErFJWXy',
        '0900000004', 4, 'ACTIVE');

-- Group (unique name)
INSERT INTO OwnershipGroup(GroupName, Status, Description)
VALUES (N'EV Group A', 'APPROVED', N'EV co-ownership group');
INSERT INTO OwnershipGroup(GroupName, Status, Description)
VALUES (N'EV Group B', 'PENDING', N'EV co-ownership group');

-- Shares (A:50%, add two more co-owners)
INSERT INTO OwnershipShare(UserId, GroupId, GroupRole, OwnershipPercentage)
VALUES (1, 1, 'ADMIN', 50.00);

INSERT INTO Users(FullName, Email, PasswordHash, PhoneNumber, RoleId, Status)
VALUES (N'David Co-owner', 'david@example.com', '$2a$12$mXvNgmVCwJBXvgB9J8SqzeL6Ls0wQLEKK1wjZ6xnwybIdq6xyoGay',
        '0900000005', 1, 'ACTIVE'),
       (N'Emma Co-owner', 'emma@example.com', '$2a$12$7KLojER8vQtzWqovOCbh3efIt55BOsfrL016SnmF9EnduKPGod86O',
        '0900000006', 1, 'ACTIVE');

INSERT INTO OwnershipShare(UserId, GroupId, GroupRole, OwnershipPercentage)
VALUES (5, 1, 'MEMBER', 30.00),
       (6, 1, 'MEMBER', 20.00);

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
VALUES (1, 1, DATEADD(HOUR, -1, SYSUTCDATETIME()), DATEADD(HOUR, 2, SYSUTCDATETIME()), 'PENDING');

-- Payments demo
INSERT INTO Payment(PayerUserId, FundId, Amount, PaymentMethod, Status, PaymentType, TransactionCode, PaymentCategory)
VALUES (1, 1, 1000000, 'BANK_TRANSFER', 'PENDING', 'CONTRIBUTION', 'TXN-P-001', 'GROUP'),
       (1, 1, 1500000, 'BANK_TRANSFER', 'COMPLETED', 'CONTRIBUTION', 'TXN-C-001', 'GROUP'),
       (1, 1, 200000, 'BANK_TRANSFER', 'FAILED', 'MAINTENANCE_FEE', 'TXN-F-001', 'GROUP'),
       (1, 1, 1500000, 'BANK_TRANSFER', 'REFUNDED', 'CONTRIBUTION', 'TXN-R-001', 'GROUP');

-- Expense demo
INSERT INTO Expense(FundId, SourceType, SourceId, Description, Amount)
VALUES (1, 'MAINTENANCE', NULL, N'Periodic maintenance', 300000);

-- Incident demo
INSERT INTO Incident(BookingId, IncidentType, Description, Status)
VALUES (1, 'DAMAGE', N'Light scratch', 'REPORTED');

-- VehicleCheck demo
INSERT INTO VehicleCheck(BookingId, CheckType, Odometer, BatteryLevel, Cleanliness, Status)
VALUES (1, 'POST_USE', 12000, 85.0, 'CLEAN', 'PASSED');

-- FinancialReport demo
INSERT INTO FinancialReport(FundId, ReportMonth, ReportYear, TotalIncome, TotalExpense, GeneratedBy)
VALUES (1, 12, 2024, 5000000, 300000, 3),
       (1, 11, 2024, 4500000, 250000, 3);

-- Contract Templates (HTML templates)
INSERT INTO ContractTemplate(TemplateName, Description, HtmlTemplate, IsActive)
VALUES (N'Standard EV Contract', N'Standard HTML template for EV co-ownership contracts',
        N'<!DOCTYPE html><html><head><title>EV Contract</title></head><body><h1>Standard EV Contract</h1><p>Contract content...</p></body></html>',
        1),

       (N'Premium EV Contract', N'Premium HTML template with enhanced styling',
        N'<!DOCTYPE html><html><head><title>Premium EV Contract</title><style>body{background:#f8f9fa}</style></head><body><h1>Premium EV Contract</h1><p>Enhanced contract content...</p></body></html>',
        1);

-- Contract sample (using template)
INSERT INTO Contract(GroupId, TemplateId, StartDate, EndDate, Terms, RequiredDepositAmount, IsActive, ApprovalStatus,
                     ApprovedBy, ApprovedAt)
VALUES (1, 1, '2024-01-01', '2025-01-01', N'Standard EV co-ownership contract terms...', 2000000, 1, 'APPROVED', 2,
        SYSUTCDATETIME());

-- =============================================
-- END


-- =============================================
-- 20) INVITATION (invite bằng link + OTP, không revoke)
-- =============================================
CREATE TABLE Invitation
(
    InvitationId        BIGINT IDENTITY (1,1) PRIMARY KEY,

    GroupId             BIGINT        NOT NULL,           -- FK -> OwnershipGroup
    InviterUserId       BIGINT        NOT NULL,           -- FK -> Users (người mời)
    InviteeEmail        NVARCHAR(100) NOT NULL,           -- email người được mời
    EmailNormalized     AS LOWER(InviteeEmail) PERSISTED, -- phục vụ unique filtered index

    Token               VARCHAR(128)  NOT NULL,           -- opaque token trong link
    OtpCode             VARCHAR(6)    NOT NULL,           -- OTP 6 số (nếu lưu hash: tăng size & bỏ CHECK)

    Status              NVARCHAR(20)  NOT NULL,           -- PENDING / ACCEPTED / EXPIRED
    SuggestedPercentage DECIMAL(5, 2) NULL,               -- gợi ý %, tuỳ chọn

    ExpiresAt           DATETIME2(7)  NOT NULL,           -- hạn dùng token/OTP
    ResendCount         INT           NOT NULL
        CONSTRAINT DF_Invitation_ResendCount DEFAULT (0),
    LastSentAt          DATETIME2(7)  NULL,

    CreatedAt           DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    AcceptedAt          DATETIME2(7)  NULL,
    AcceptedBy          BIGINT        NULL                -- FK -> Users (user đã accept)
);
GO

ALTER TABLE Invitation
    ADD CONSTRAINT FK_Invitation_Group
        FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
            ON DELETE CASCADE; -- xoá group xoá luôn lời mời
GO

ALTER TABLE Invitation
    ADD CONSTRAINT FK_Invitation_Inviter
        FOREIGN KEY (InviterUserId) REFERENCES Users (UserId);
GO

ALTER TABLE Invitation
    ADD CONSTRAINT FK_Invitation_AcceptedBy
        FOREIGN KEY (AcceptedBy) REFERENCES Users (UserId)
            ON DELETE SET NULL; -- giữ audit nếu user bị xoá
GO

-- Trạng thái hợp lệ
ALTER TABLE Invitation
    ADD CONSTRAINT CK_Invitation_Status
        CHECK (Status IN (N'PENDING', N'ACCEPTED', N'EXPIRED'));
GO

-- OTP 6 chữ số (bỏ nếu bạn lưu hash)
ALTER TABLE Invitation
    ADD CONSTRAINT CK_Invitation_Otp
        CHECK (OtpCode NOT LIKE '%[^0-9]%' AND LEN(OtpCode) = 6);
GO

-- SuggestedPercentage trong [0..100]
ALTER TABLE Invitation
    ADD CONSTRAINT CK_Invitation_SuggestedPct
        CHECK (SuggestedPercentage IS NULL OR (SuggestedPercentage >= 0.00 AND SuggestedPercentage <= 100.00));
GO

-- Token duy nhất
CREATE UNIQUE INDEX UQ_Invitation_Token ON Invitation (Token);
GO

-- Không cho trùng email đang PENDING trong cùng group (case-insensitive)
CREATE UNIQUE INDEX UQ_Invitation_Group_Email_Pending
    ON Invitation (GroupId, EmailNormalized)
    WHERE Status = N'PENDING';
GO

-- Hỗ trợ dọn dẹp & tra cứu
CREATE INDEX IX_Invitation_ExpiresAt ON Invitation (ExpiresAt);
CREATE INDEX IX_Invitation_Group_ExpiresAt ON Invitation (GroupId, ExpiresAt);
GO

-- Mỗi group chỉ có 1 vehicle (áp dụng khi GroupId NOT NULL)
CREATE UNIQUE INDEX UQ_Vehicle_GroupId ON Vehicle (GroupId) WHERE GroupId IS NOT NULL;
GO


-- =============================================
-- CLEANUP (Remove dispute-related tables)
-- =============================================
IF OBJECT_ID('Refund', 'U') IS NOT NULL
    DROP TABLE Refund;
GO


-- ==================================================================================================================
-- Drop theo thứ tự: Expense → Incident → Maintenance
IF OBJECT_ID('Expense', 'U') IS NOT NULL
    DROP TABLE Expense;
GO
IF OBJECT_ID('Incident', 'U') IS NOT NULL
    DROP TABLE Incident;
GO
IF OBJECT_ID('Maintenance', 'U') IS NOT NULL
    DROP TABLE Maintenance;
GO

CREATE TABLE Maintenance
(
    MaintenanceId BIGINT IDENTITY (1,1) PRIMARY KEY,
    VehicleId     BIGINT       NOT NULL,
    RequestedBy   BIGINT       NOT NULL,                   -- technician phát hiện
    ApprovedBy    BIGINT       NULL,                       -- staff duyệt
    Description   NVARCHAR(MAX),
    ActualCost    DECIMAL(12, 2),
    Status        NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
    CreatedAt     DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),

    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId),
    FOREIGN KEY (RequestedBy) REFERENCES Users (UserId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

CREATE TABLE Incident
(
    IncidentId  BIGINT IDENTITY (1,1) PRIMARY KEY,
    BookingId   BIGINT       NOT NULL,
    UserId      BIGINT       NOT NULL,                   -- người gặp sự cố
    Description NVARCHAR(MAX),
    ActualCost  DECIMAL(12, 2),
    ImageUrls   NVARCHAR(MAX),
    Status      NVARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING | APPROVED | REJECTED
    ApprovedBy  BIGINT       NULL,                       -- staff duyệt
    CreatedAt   DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),

    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

CREATE TABLE Expense
(
    ExpenseId       BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId          BIGINT         NOT NULL,
    SourceType      NVARCHAR(30)   NOT NULL CHECK (SourceType IN ('INCIDENT', 'MAINTENANCE')),
    SourceId        BIGINT         NOT NULL,                   -- FK logic đến Incident/Maintenance
    RecipientUserId BIGINT         NULL,                       -- người được hoàn tiền (nếu có)
    Description     NVARCHAR(MAX),
    Amount          DECIMAL(12, 2) NOT NULL,
    Status          NVARCHAR(20)   NOT NULL DEFAULT 'PENDING', -- PENDING | COMPLETED
    CreatedAt       DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    ApprovedBy      BIGINT         NULL,                       -- admin duyệt chi / hoàn

    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (RecipientUserId) REFERENCES Users (UserId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- Maintenance
INSERT INTO Maintenance (VehicleId, RequestedBy, ApprovedBy, Description, ActualCost, Status)
VALUES (1, 4, NULL, N'Battery health warning detected, awaiting review', 0, 'PENDING'),
       (1, 4, 2, N'Tire pressure adjustment and brake check completed', 500000, 'APPROVED'),
       (1, 4, 2, N'Body repaint request rejected due to non-essential cosmetic issue', 0, 'REJECTED');

-- Incident
INSERT INTO Incident (BookingId, UserId, Description, ActualCost, ImageUrls, Status, ApprovedBy)
VALUES (1, 1, N'Front headlight malfunction during night trip. User paid for replacement.', 350000,
        N'https://example.com/headlight_before.jpg;https://example.com/headlight_after.jpg', 'APPROVED', 2),
       (1, 1, N'Scratch on rear bumper reported by user. Considered user fault.', 150000,
        N'https://example.com/bumper_scratch.jpg', 'REJECTED', 2);

-- Expense
INSERT INTO Expense (FundId, SourceType, SourceId, RecipientUserId, Description, Amount, Status, ApprovedBy)
VALUES (1, 'INCIDENT', 1, 1, N'Reimburse user for headlight repair (incident #1)', 350000, 'COMPLETED', 3),
       (1, 'MAINTENANCE', 2, NULL, N'Brake and tire maintenance by technician', 500000, 'COMPLETED', 3);
GO

-- Thêm cột
ALTER TABLE Expense
    ADD ExpenseDate DATETIME2(7) NULL;
GO

-- Gán giá trị cho bản ghi cũ
UPDATE Expense
SET ExpenseDate = CreatedAt
WHERE ExpenseDate IS NULL;
GO

-- Tạo default cho bản ghi mới
ALTER TABLE Expense
    ADD CONSTRAINT DF_Expense_ExpenseDate DEFAULT SYSUTCDATETIME() FOR ExpenseDate;
GO

---------------------------- tạo maintenanceDate
ALTER TABLE Maintenance ADD MaintenanceDate DATE NULL;

UPDATE Maintenance
SET MaintenanceDate = DATEADD(DAY, 7, CAST(SYSUTCDATETIME() AS DATE))
WHERE MaintenanceDate IS NULL;


ALTER TABLE Maintenance
    ADD CONSTRAINT CK_Maintenance_FutureDate
        CHECK (MaintenanceDate > CAST(CreatedAt AS DATE));
GO

-- Thêm cột UpdatedAt
ALTER TABLE Maintenance
    ADD UpdatedAt DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME();
GO

-- Thêm cột FundBalanceAfter
ALTER TABLE Expense
    ADD FundBalanceAfter DECIMAL(15,2) NULL;
GO

ALTER TABLE Expense
    ADD UpdatedAt DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME();
GO

UPDATE Expense
SET UpdatedAt = CreatedAt
WHERE UpdatedAt IS NULL;
GO


--------------------------------------------------------
-- them QrCode vao Usage
ALTER TABLE UsageBooking
    ADD QrCode VARCHAR(255) NULL;
-- them RejectionCategory va RejectionReason vao Incident
ALTER TABLE Incident
    ADD
        RejectionCategory VARCHAR(50) NULL,
        RejectionReason NVARCHAR(MAX) NULL;

