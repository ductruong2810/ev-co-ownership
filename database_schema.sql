-- =============================================
-- EV Co-ownership Database Schema (SQL Server)
-- Updated based on JPA Entities
-- =============================================

-- Recreate Database (run from master)
USE master;
GO
IF DB_ID(N'evshare') IS NOT NULL
    BEGIN
        ALTER DATABASE evshare SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
        DROP DATABASE evshare;
    END
GO
CREATE DATABASE evshare;
GO
USE evshare;
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
    Status       NVARCHAR(20),
    CreatedAt    DATETIME2(7),
    UpdatedAt    DATETIME2(7),
    FOREIGN KEY (RoleId) REFERENCES Roles (RoleId)
);
GO

-- =============================================
-- 3) OWNERSHIP GROUP (GroupName must be unique)
-- =============================================
CREATE TABLE OwnershipGroup
(
    GroupId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupName      NVARCHAR(100) NOT NULL,
    Status         NVARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    Description    NVARCHAR(MAX),
    MemberCapacity INT           NULL,
    RejectionReason NVARCHAR(MAX),
    FundId         BIGINT        NULL,
    CreatedAt      DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt      DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
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
-- 5) SHARED FUND
-- =============================================
CREATE TABLE SharedFund
(
    FundId       BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId      BIGINT         NOT NULL,
    Balance      DECIMAL(15, 2) DEFAULT 0,
    TargetAmount DECIMAL(15, 2) DEFAULT 0,
    FundType     NVARCHAR(20)   NOT NULL DEFAULT 'OPERATING',
    IsSpendable  BIT            NOT NULL DEFAULT 1,
    CreatedAt    DATETIME2(7)   DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(7)   DEFAULT SYSUTCDATETIME(),
    Version      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT UQ_SharedFund_Group UNIQUE (GroupId, FundType),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId)
);
GO

-- Add FK from OwnershipGroup to SharedFund (after SharedFund is created)
ALTER TABLE OwnershipGroup
    ADD CONSTRAINT FK_OwnershipGroup_FundId
        FOREIGN KEY (FundId) REFERENCES SharedFund (FundId);
GO

-- =============================================
-- 6) VEHICLE
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

-- Mỗi group chỉ có 1 vehicle (áp dụng khi GroupId NOT NULL)
CREATE UNIQUE INDEX UQ_Vehicle_GroupId ON Vehicle (GroupId) WHERE GroupId IS NOT NULL;
GO

-- =============================================
-- 7) VEHICLE IMAGES
-- =============================================
CREATE TABLE VehicleImages
(
    ImageId         BIGINT IDENTITY (1,1) PRIMARY KEY,
    VehicleId       BIGINT        NULL,
    ImageUrl        NVARCHAR(500),
    ImageType       NVARCHAR(20),
    ApprovalStatus  NVARCHAR(20)  DEFAULT 'PENDING',
    ApprovedBy      BIGINT        NULL,
    ApprovedAt      DATETIME2(7)  NULL,
    RejectionReason NVARCHAR(500) NULL,
    UploadedAt      DATETIME2(7)  DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 8) CONTRACT (TemplateId removed - handled in frontend)
-- =============================================
CREATE TABLE Contract
(
    ContractId            BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId               BIGINT        NOT NULL,
    StartDate             DATE,
    EndDate               DATE,
    Terms                 NVARCHAR(MAX),
    RequiredDepositAmount DECIMAL(15, 2),
    IsActive              BIT           DEFAULT 1,
    CreatedAt             DATETIME2(7)  DEFAULT SYSUTCDATETIME(),
    UpdatedAt             DATETIME2(7)  DEFAULT SYSUTCDATETIME(),
    ApprovalStatus        NVARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    ApprovedBy            BIGINT        NULL,
    ApprovedAt            DATETIME2(7)  NULL,
    RejectionReason       NVARCHAR(500) NULL,
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 9) CONTRACT FEEDBACK
-- =============================================
CREATE TABLE ContractFeedback
(
    FeedbackId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    ContractId        BIGINT        NOT NULL,
    UserId            BIGINT        NOT NULL,
    Status            NVARCHAR(20)  NOT NULL,
    ReactionType      NVARCHAR(20),
    Reason            NVARCHAR(1000),
    AdminNote         NVARCHAR(1000),
    LastAdminAction   NVARCHAR(50),
    LastAdminActionAt DATETIME2(7),
    SubmittedAt       DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(7),
    FOREIGN KEY (ContractId) REFERENCES Contract (ContractId),
    FOREIGN KEY (UserId) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 10) USAGE BOOKING
-- =============================================
CREATE TABLE UsageBooking
(
    BookingId      BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId         BIGINT       NOT NULL,
    VehicleId      BIGINT       NOT NULL,
    StartDateTime  DATETIME2(7),
    EndDateTime    DATETIME2(7),
    Status         NVARCHAR(20),
    TotalDuration  INT,
    Priority       INT,
    QrCodeCheckin  VARCHAR(255),
    QrCodeCheckout VARCHAR(255),
    CheckinStatus  BIT           DEFAULT 0,
    CheckoutStatus BIT           DEFAULT 0,
    CheckinTime    DATETIME2(7),
    CheckoutTime   DATETIME2(7),
    CreatedAt      DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    UpdatedAt      DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId)
);
GO

-- =============================================
-- 11) MAINTENANCE
-- =============================================
CREATE TABLE Maintenance
(
    MaintenanceId         BIGINT IDENTITY (1,1) PRIMARY KEY,
    VehicleId             BIGINT       NOT NULL,
    RequestedBy           BIGINT       NOT NULL,
    ApprovedBy            BIGINT       NULL,
    LiableUserId          BIGINT       NULL,
    Description           NVARCHAR(MAX),
    ActualCost            DECIMAL(12, 2) NOT NULL,
    Status                NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
    CoverageType          NVARCHAR(20) NOT NULL DEFAULT 'GROUP',
    RequestDate           DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    ApprovalDate          DATETIME2(7),
    NextDueDate           DATE,
    MaintenanceDate       DATE,
    EstimatedDurationDays INT,
    MaintenanceStartAt    DATETIME2(7),
    ExpectedFinishAt      DATETIME2(7),
    MaintenanceCompletedAt DATETIME2(7),
    FundedAt              DATETIME2(7),
    CreatedAt             DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt             DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId),
    FOREIGN KEY (RequestedBy) REFERENCES Users (UserId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId),
    FOREIGN KEY (LiableUserId) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 12) INCIDENT
-- =============================================
CREATE TABLE Incident
(
    IncidentId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    BookingId         BIGINT       NOT NULL,
    UserId            BIGINT       NOT NULL,
    Description       NVARCHAR(MAX),
    ActualCost        DECIMAL(12, 2),
    ImageUrls         NVARCHAR(MAX),
    Status            NVARCHAR(20) NOT NULL DEFAULT 'PENDING',
    ApprovedBy        BIGINT       NULL,
    RejectionCategory NVARCHAR(50),
    RejectionReason   NVARCHAR(MAX),
    CreatedAt         DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 13) EXPENSE
-- =============================================
CREATE TABLE Expense
(
    ExpenseId       BIGINT IDENTITY (1,1) PRIMARY KEY,
    FundId          BIGINT         NOT NULL,
    SourceType      NVARCHAR(30)   NOT NULL CHECK (SourceType IN ('INCIDENT', 'MAINTENANCE')),
    SourceId        BIGINT         NOT NULL,
    RecipientUserId BIGINT         NULL,
    Description     NVARCHAR(MAX),
    Amount          DECIMAL(12, 2) NOT NULL,
    Status          NVARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    ExpenseDate     DATETIME2(7)   DEFAULT SYSUTCDATETIME(),
    FundBalanceAfter DECIMAL(15, 2),
    CreatedAt       DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    UpdatedAt       DATETIME2(7)   NOT NULL DEFAULT SYSUTCDATETIME(),
    ApprovedBy      BIGINT         NULL,
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (RecipientUserId) REFERENCES Users (UserId),
    FOREIGN KEY (ApprovedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 14) PAYMENT
-- =============================================
CREATE TABLE Payment
(
    PaymentId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    PayerUserId      BIGINT         NOT NULL,
    FundId           BIGINT         NULL,
    Amount           DECIMAL(18, 2) NOT NULL,
    PaymentDate      DATETIME2(7)   DEFAULT SYSUTCDATETIME(),
    PaymentMethod    NVARCHAR(50),
    Status           NVARCHAR(20),
    TransactionCode  NVARCHAR(100),
    ProviderResponse NVARCHAR(MAX),
    PaymentType      NVARCHAR(20)  NOT NULL,
    PaymentCategory  NVARCHAR(20),
    ChargedUserId    BIGINT,
    PersonalReason   NVARCHAR(MAX),
    MaintenanceId    BIGINT         NULL,
    PaidAt           DATETIME2(7),
    Version          BIGINT         NOT NULL DEFAULT 0,
    FOREIGN KEY (PayerUserId) REFERENCES Users (UserId),
    FOREIGN KEY (ChargedUserId) REFERENCES Users (UserId),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (MaintenanceId) REFERENCES Maintenance (MaintenanceId),
    CONSTRAINT CK_Payment_Personal
        CHECK (PaymentCategory <> 'PERSONAL' OR ChargedUserId IS NOT NULL)
);
GO

-- =============================================
-- 15) NOTIFICATION
-- =============================================
CREATE TABLE Notification
(
    NotificationId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId           BIGINT,
    Title            NVARCHAR(255),
    [Message]        NVARCHAR(MAX),
    NotificationType NVARCHAR(50),
    IsRead           BIT          DEFAULT 0,
    IsDelivered      BIT          DEFAULT 0,
    CreatedAt        DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 16) VEHICLE CHECK
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
    CreatedAt    DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId)
);
GO

-- =============================================
-- 17) USER DOCUMENT
-- =============================================
CREATE TABLE UserDocument
(
    DocumentId    BIGINT IDENTITY (1,1) PRIMARY KEY,
    UserId         BIGINT        NOT NULL,
    DocumentNumber NVARCHAR(255) DEFAULT '',
    DateOfBirth    NVARCHAR(20) DEFAULT '',
    IssueDate      NVARCHAR(20) DEFAULT '',
    ExpiryDate     NVARCHAR(20) DEFAULT '',
    Address        NVARCHAR(MAX) DEFAULT '',
    DocumentType   NVARCHAR(20) DEFAULT '',
    Side           NVARCHAR(10) DEFAULT '',
    ImageUrl       NVARCHAR(500) NOT NULL DEFAULT '',
    FileHash       NVARCHAR(64) DEFAULT '',
    Status         NVARCHAR(20) DEFAULT '',
    ReviewNote     NVARCHAR(MAX) DEFAULT '',
    ReviewedBy     BIGINT,
    CreatedAt      DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    UpdatedAt      DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    FOREIGN KEY (ReviewedBy) REFERENCES Users (UserId)
);
GO

-- =============================================
-- 18) VOTING
-- =============================================
CREATE TABLE Voting
(
    VotingId         BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId          BIGINT,
    Title            NVARCHAR(255),
    Description      NVARCHAR(MAX),
    VotingType       NVARCHAR(50),
    Options          NVARCHAR(MAX),
    Results          NVARCHAR(MAX),
    Deadline         DATETIME2(7),
    Status           NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    CreatedBy        BIGINT,
    CreatedAt        DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    RelatedExpenseId BIGINT,
    EstimatedAmount  DECIMAL(12, 2),
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId),
    FOREIGN KEY (CreatedBy) REFERENCES Users (UserId),
    FOREIGN KEY (RelatedExpenseId) REFERENCES Expense (ExpenseId)
);
GO

-- =============================================
-- 19) VOTE RECORD
-- =============================================
CREATE TABLE VoteRecord
(
    VoteRecordId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    VotingId       BIGINT       NOT NULL,
    UserId         BIGINT       NOT NULL,
    SelectedOption NVARCHAR(50) NOT NULL,
    VotedAt        DATETIME2(7) NOT NULL DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (VotingId) REFERENCES Voting (VotingId),
    FOREIGN KEY (UserId) REFERENCES Users (UserId),
    CONSTRAINT UQ_VoteRecord UNIQUE (VotingId, UserId)
);
GO

-- =============================================
-- 20) INVITATION
-- =============================================
CREATE TABLE Invitation
(
    InvitationId        BIGINT IDENTITY (1,1) PRIMARY KEY,
    GroupId             BIGINT        NOT NULL,
    InviterUserId       BIGINT        NOT NULL,
    InviteeEmail        NVARCHAR(100) NOT NULL,
    EmailNormalized     AS LOWER(InviteeEmail) PERSISTED,
    Token               VARCHAR(128)  NOT NULL,
    OtpCode             VARCHAR(6)    NOT NULL,
    Status              NVARCHAR(20)  NOT NULL,
    SuggestedPercentage DECIMAL(5, 2) NULL,
    ExpiresAt           DATETIME2(7)  NOT NULL,
    ResendCount         INT           NOT NULL DEFAULT 0,
    LastSentAt          DATETIME2(7)  NULL,
    CreatedAt           DATETIME2(7)  NOT NULL DEFAULT SYSUTCDATETIME(),
    AcceptedAt          DATETIME2(7)  NULL,
    AcceptedBy          BIGINT        NULL,
    FOREIGN KEY (GroupId) REFERENCES OwnershipGroup (GroupId) ON DELETE CASCADE,
    FOREIGN KEY (InviterUserId) REFERENCES Users (UserId),
    FOREIGN KEY (AcceptedBy) REFERENCES Users (UserId) ON DELETE SET NULL,
    CONSTRAINT CK_Invitation_Status
        CHECK (Status IN (N'PENDING', N'ACCEPTED', N'EXPIRED')),
    CONSTRAINT CK_Invitation_Otp
        CHECK (OtpCode NOT LIKE '%[^0-9]%' AND LEN(OtpCode) = 6),
    CONSTRAINT CK_Invitation_SuggestedPct
        CHECK (SuggestedPercentage IS NULL OR (SuggestedPercentage >= 0.00 AND SuggestedPercentage <= 100.00))
);
GO

-- =============================================
-- 21) OTP TOKEN
-- =============================================
CREATE TABLE OtpToken
(
    TokenId   BIGINT IDENTITY (1,1) PRIMARY KEY,
    Email     NVARCHAR(100) NOT NULL,
    OtpCode   VARCHAR(6)    NOT NULL,
    ExpiresAt DATETIME2(7)  NOT NULL,
    IsUsed    BIT           DEFAULT 0,
    CreatedAt DATETIME2(7)  DEFAULT SYSUTCDATETIME()
);
GO

-- =============================================
-- 22) FINANCIAL REPORT
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
    CreatedAt    DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    UpdatedAt    DATETIME2(7) DEFAULT SYSUTCDATETIME(),
    FOREIGN KEY (FundId) REFERENCES SharedFund (FundId),
    FOREIGN KEY (GeneratedBy) REFERENCES Users (UserId)
);
GO

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
CREATE INDEX IX_Contract_GroupId ON Contract (GroupId);

-- ContractFeedback
CREATE INDEX IX_ContractFeedback_ContractId ON ContractFeedback (ContractId);
CREATE INDEX IX_ContractFeedback_UserId ON ContractFeedback (UserId);

-- UsageBooking
CREATE INDEX IX_UsageBooking_UserId ON UsageBooking (UserId);
CREATE INDEX IX_UsageBooking_VehicleId ON UsageBooking (VehicleId);
CREATE INDEX IX_UsageBooking_StartDateTime ON UsageBooking (StartDateTime);
CREATE INDEX IX_UsageBooking_Status ON UsageBooking (Status);

-- Maintenance
CREATE INDEX IX_Maintenance_VehicleId ON Maintenance (VehicleId);
CREATE INDEX IX_Maintenance_RequestedBy ON Maintenance (RequestedBy);
CREATE INDEX IX_Maintenance_Status ON Maintenance (Status);

-- Incident
CREATE INDEX IX_Incident_BookingId ON Incident (BookingId);
CREATE INDEX IX_Incident_UserId ON Incident (UserId);
CREATE INDEX IX_Incident_ApprovedBy ON Incident (ApprovedBy);
CREATE INDEX IX_Incident_Status ON Incident (Status);

-- Expense
CREATE INDEX IX_Expense_FundId ON Expense (FundId);
CREATE INDEX IX_Expense_SourceType_SourceId ON Expense (SourceType, SourceId);

-- Payment
CREATE INDEX IX_Payment_PayerUserId ON Payment (PayerUserId);
CREATE INDEX IX_Payment_FundId ON Payment (FundId);
CREATE INDEX IX_Payment_Status ON Payment (Status);
CREATE INDEX IX_Payment_MaintenanceId ON Payment (MaintenanceId);

-- Notification
CREATE INDEX IX_Notification_UserId ON Notification (UserId);
CREATE INDEX IX_Notification_IsRead ON Notification (IsRead);

-- VehicleCheck
CREATE INDEX IX_VehicleCheck_BookingId ON VehicleCheck (BookingId);
CREATE INDEX IX_VehicleCheck_Status ON VehicleCheck (Status);

-- UserDocument
CREATE INDEX IX_UserDocument_UserId ON UserDocument (UserId);
CREATE INDEX IX_UserDocument_Status ON UserDocument (Status);

-- Voting
CREATE INDEX IX_Voting_GroupId ON Voting (GroupId);
CREATE INDEX IX_Voting_Status ON Voting (Status);

-- VoteRecord
CREATE INDEX IX_VoteRecord_VotingId ON VoteRecord (VotingId);
CREATE INDEX IX_VoteRecord_UserId ON VoteRecord (UserId);

-- Invitation
CREATE UNIQUE INDEX UQ_Invitation_Token ON Invitation (Token);
CREATE UNIQUE INDEX UQ_Invitation_Group_Email_Pending
    ON Invitation (GroupId, EmailNormalized)
    WHERE Status = N'PENDING';
CREATE INDEX IX_Invitation_ExpiresAt ON Invitation (ExpiresAt);
CREATE INDEX IX_Invitation_Group_ExpiresAt ON Invitation (GroupId, ExpiresAt);

-- FinancialReport
CREATE INDEX IX_FinancialReport_FundId ON FinancialReport (FundId);
CREATE INDEX IX_FinancialReport_GeneratedBy ON FinancialReport (GeneratedBy);
CREATE INDEX IX_FinancialReport_ReportYearMonth ON FinancialReport (ReportYear, ReportMonth);

GO