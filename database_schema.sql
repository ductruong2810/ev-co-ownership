-- =============================================
-- EV Co-ownership Database Schema (PostgreSQL)
-- Generated from current JPA entities
-- =============================================

BEGIN;

-- Clean existing objects (optional for local/dev refresh)
DROP TABLE IF EXISTS "VoteRecord" CASCADE;
DROP TABLE IF EXISTS "Voting" CASCADE;
DROP TABLE IF EXISTS "UserDocument" CASCADE;
DROP TABLE IF EXISTS "VehicleCheck" CASCADE;
DROP TABLE IF EXISTS "Notification" CASCADE;
DROP TABLE IF EXISTS "Payment" CASCADE;
DROP TABLE IF EXISTS "Expense" CASCADE;
DROP TABLE IF EXISTS "Dispute" CASCADE;
DROP TABLE IF EXISTS "Incident" CASCADE;
DROP TABLE IF EXISTS "Maintenance" CASCADE;
DROP TABLE IF EXISTS "UsageBooking" CASCADE;
DROP TABLE IF EXISTS "ContractFeedback" CASCADE;
DROP TABLE IF EXISTS "Contract" CASCADE;
DROP TABLE IF EXISTS "VehicleImages" CASCADE;
DROP TABLE IF EXISTS "Vehicle" CASCADE;
DROP TABLE IF EXISTS "SharedFund" CASCADE;
DROP TABLE IF EXISTS "Invitation" CASCADE;
DROP TABLE IF EXISTS "OtpToken" CASCADE;
DROP TABLE IF EXISTS "FinancialReport" CASCADE;
DROP TABLE IF EXISTS "OwnershipShare" CASCADE;
DROP TABLE IF EXISTS "OwnershipGroup" CASCADE;
DROP TABLE IF EXISTS "Users" CASCADE;
DROP TABLE IF EXISTS "Roles" CASCADE;

-- =============================================
-- 1) ROLES
-- =============================================
CREATE TABLE "Roles"
(
    "RoleId"   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "RoleName" VARCHAR(30) NOT NULL UNIQUE
);

-- =============================================
-- 2) USERS
-- =============================================
CREATE TABLE "Users"
(
    "UserId"       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "FullName"     VARCHAR(100) NOT NULL,
    "Email"        VARCHAR(100) NOT NULL UNIQUE,
    "PasswordHash" VARCHAR(255) NOT NULL,
    "PhoneNumber"  VARCHAR(20),
    "AvatarUrl"    VARCHAR(500),
    "RoleId"       BIGINT,
    "Status"       VARCHAR(20),
    "CreatedAt"    TIMESTAMPTZ,
    "UpdatedAt"    TIMESTAMPTZ,
    CONSTRAINT fk_users_roles FOREIGN KEY ("RoleId") REFERENCES "Roles" ("RoleId")
);

-- =============================================
-- 3) OWNERSHIP GROUP
-- =============================================
CREATE TABLE "OwnershipGroup"
(
    "GroupId"         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "GroupName"       VARCHAR(100) NOT NULL,
    "Status"          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    "Description"     TEXT,
    "MemberCapacity"  INT,
    "RejectionReason" TEXT,
    "FundId"          BIGINT,
    "CreatedAt"       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"       TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ownershipgroup_name UNIQUE ("GroupName")
);

-- =============================================
-- 4) OWNERSHIP SHARE
-- =============================================
CREATE TABLE "OwnershipShare"
(
    "UserId"              BIGINT        NOT NULL,
    "GroupId"             BIGINT        NOT NULL,
    "GroupRole"           VARCHAR(50)   NOT NULL DEFAULT 'MEMBER',
    "OwnershipPercentage" NUMERIC(5, 2) NOT NULL CHECK ("OwnershipPercentage" >= 0 AND "OwnershipPercentage" <= 100),
    "JoinDate"            TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "DepositStatus"       VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    "UpdatedAt"           TIMESTAMPTZ   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_ownershipshare PRIMARY KEY ("UserId", "GroupId"),
    CONSTRAINT fk_ownershipshare_user FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_ownershipshare_group FOREIGN KEY ("GroupId") REFERENCES "OwnershipGroup" ("GroupId")
);

-- =============================================
-- 5) SHARED FUND
-- =============================================
CREATE TABLE "SharedFund"
(
    "FundId"       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "GroupId"      BIGINT         NOT NULL,
    "Balance"      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    "TargetAmount" NUMERIC(15, 2) NOT NULL DEFAULT 0,
    "FundType"     VARCHAR(20)    NOT NULL DEFAULT 'OPERATING',
    "IsSpendable"  BOOLEAN        NOT NULL DEFAULT TRUE,
    "CreatedAt"    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "Version"      BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_sharedfund_group_type UNIQUE ("GroupId", "FundType"),
    CONSTRAINT fk_sharedfund_group FOREIGN KEY ("GroupId") REFERENCES "OwnershipGroup" ("GroupId")
);

ALTER TABLE "OwnershipGroup"
    ADD CONSTRAINT fk_ownershipgroup_fund
        FOREIGN KEY ("FundId") REFERENCES "SharedFund" ("FundId");

-- =============================================
-- 6) VEHICLE
-- =============================================
CREATE TABLE "Vehicle"
(
    "VehicleId"     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "Brand"         VARCHAR(100),
    "Model"         VARCHAR(100),
    "LicensePlate"  VARCHAR(20),
    "ChassisNumber" VARCHAR(30),
    "VehicleValue"  NUMERIC(15, 2),
    "GroupId"       BIGINT,
    "CreatedAt"     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicle_group FOREIGN KEY ("GroupId") REFERENCES "OwnershipGroup" ("GroupId")
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_vehicle_group_not_null
    ON "Vehicle" ("GroupId") WHERE "GroupId" IS NOT NULL;

-- =============================================
-- 7) VEHICLE IMAGES
-- =============================================
CREATE TABLE "VehicleImages"
(
    "ImageId"         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "VehicleId"       BIGINT,
    "ImageUrl"        VARCHAR(500),
    "ImageType"       VARCHAR(20),
    "ApprovalStatus"  VARCHAR(20) DEFAULT 'PENDING',
    "ApprovedBy"      BIGINT,
    "ApprovedAt"      TIMESTAMPTZ,
    "RejectionReason" VARCHAR(500),
    "UploadedAt"      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehicleimages_vehicle FOREIGN KEY ("VehicleId") REFERENCES "Vehicle" ("VehicleId"),
    CONSTRAINT fk_vehicleimages_user FOREIGN KEY ("ApprovedBy") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 8) CONTRACT
-- =============================================
CREATE TABLE "Contract"
(
    "ContractId"            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "GroupId"               BIGINT      NOT NULL,
    "StartDate"             DATE,
    "EndDate"               DATE,
    "Terms"                 TEXT,
    "RequiredDepositAmount" NUMERIC(15, 2),
    "IsActive"              BOOLEAN              DEFAULT TRUE,
    "CreatedAt"             TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"             TIMESTAMPTZ          DEFAULT CURRENT_TIMESTAMP,
    "ApprovalStatus"        VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    "ApprovedBy"            BIGINT,
    "ApprovedAt"            TIMESTAMPTZ,
    "RejectionReason"       VARCHAR(500),
    CONSTRAINT fk_contract_group FOREIGN KEY ("GroupId") REFERENCES "OwnershipGroup" ("GroupId"),
    CONSTRAINT fk_contract_user FOREIGN KEY ("ApprovedBy") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 9) CONTRACT FEEDBACK
-- =============================================
CREATE TABLE "ContractFeedback"
(
    "FeedbackId"        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "ContractId"        BIGINT      NOT NULL,
    "UserId"            BIGINT      NOT NULL,
    "Status"            VARCHAR(20) NOT NULL,
    "ReactionType"      VARCHAR(20),
    "Reason"            VARCHAR(1000),
    "AdminNote"         VARCHAR(1000),
    "LastAdminAction"   VARCHAR(50),
    "LastAdminActionAt" TIMESTAMPTZ,
    "SubmittedAt"       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"         TIMESTAMPTZ,
    CONSTRAINT fk_contractfeedback_contract FOREIGN KEY ("ContractId") REFERENCES "Contract" ("ContractId"),
    CONSTRAINT fk_contractfeedback_user FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 10) USAGE BOOKING
-- =============================================
CREATE TABLE "UsageBooking"
(
    "BookingId"         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "UserId"            BIGINT NOT NULL,
    "VehicleId"         BIGINT NOT NULL,
    "StartDateTime"     TIMESTAMPTZ,
    "EndDateTime"       TIMESTAMPTZ,
    "Status"            VARCHAR(20),
    "TotalDuration"     INT,
    "Priority"          INT,
    "QrCodeCheckin"     VARCHAR(255),
    "QrCodeCheckout"    VARCHAR(255),
    "CheckinStatus"     BOOLEAN     DEFAULT FALSE,
    "CheckoutStatus"    BOOLEAN     DEFAULT FALSE,
    "CheckinTime"       TIMESTAMPTZ,
    "CheckoutTime"      TIMESTAMPTZ,
    "CheckinSignature"  TEXT,
    "CheckoutSignature" TEXT,
    "CreatedAt"         TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"         TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_usagebooking_user FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_usagebooking_vehicle FOREIGN KEY ("VehicleId") REFERENCES "Vehicle" ("VehicleId")
);

-- =============================================
-- 11) MAINTENANCE
-- =============================================
CREATE TABLE "Maintenance"
(
    "MaintenanceId"          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "VehicleId"              BIGINT         NOT NULL,
    "RequestedBy"            BIGINT         NOT NULL,
    "ApprovedBy"             BIGINT,
    "LiableUserId"           BIGINT,
    "Description"            TEXT,
    "ActualCost"             NUMERIC(12, 2) NOT NULL,
    "Status"                 VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    "CoverageType"           VARCHAR(20)    NOT NULL DEFAULT 'GROUP',
    "RequestDate"            TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ApprovalDate"           TIMESTAMPTZ,
    "NextDueDate"            DATE,
    "MaintenanceDate"        DATE,
    "EstimatedDurationDays"  INT,
    "MaintenanceStartAt"     TIMESTAMPTZ,
    "ExpectedFinishAt"       TIMESTAMPTZ,
    "MaintenanceCompletedAt" TIMESTAMPTZ,
    "FundedAt"               TIMESTAMPTZ,
    "CreatedAt"              TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"              TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_maintenance_vehicle FOREIGN KEY ("VehicleId") REFERENCES "Vehicle" ("VehicleId"),
    CONSTRAINT fk_maintenance_requested FOREIGN KEY ("RequestedBy") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_maintenance_approved FOREIGN KEY ("ApprovedBy") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_maintenance_liable FOREIGN KEY ("LiableUserId") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 12) INCIDENT
-- =============================================
CREATE TABLE "Incident"
(
    "IncidentId"        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "BookingId"         BIGINT      NOT NULL,
    "UserId"            BIGINT      NOT NULL,
    "Description"       TEXT,
    "ActualCost"        NUMERIC(12, 2),
    "ImageUrls"         TEXT,
    "Status"            VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    "ApprovedBy"        BIGINT,
    "RejectionCategory" VARCHAR(50),
    "RejectionReason"   TEXT,
    "CreatedAt"         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_incident_booking FOREIGN KEY ("BookingId") REFERENCES "UsageBooking" ("BookingId"),
    CONSTRAINT fk_incident_user FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_incident_approver FOREIGN KEY ("ApprovedBy") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 13) DISPUTE
-- =============================================
CREATE TABLE "Dispute"
(
    "DisputeId"      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "GroupId"        BIGINT       NOT NULL,
    "CreatedBy"      BIGINT       NOT NULL,
    "DisputeType"    VARCHAR(20)  NOT NULL,
    "Status"         VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    "Title"          VARCHAR(255) NOT NULL,
    "Description"    TEXT,
    "ResolvedBy"     BIGINT,
    "ResolutionNote" TEXT,
    "ResolvedAt"     TIMESTAMPTZ,
    "CreatedAt"      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"      TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dispute_group FOREIGN KEY ("GroupId") REFERENCES "OwnershipGroup" ("GroupId"),
    CONSTRAINT fk_dispute_creator FOREIGN KEY ("CreatedBy") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_dispute_resolver FOREIGN KEY ("ResolvedBy") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 14) EXPENSE
-- =============================================
CREATE TABLE "Expense"
(
    "ExpenseId"        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "FundId"           BIGINT         NOT NULL,
    "SourceType"       VARCHAR(30)    NOT NULL CHECK ("SourceType" IN ('INCIDENT', 'MAINTENANCE')),
    "SourceId"         BIGINT         NOT NULL,
    "RecipientUserId"  BIGINT,
    "Description"      TEXT,
    "Amount"           NUMERIC(12, 2) NOT NULL,
    "Status"           VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    "ExpenseDate"      TIMESTAMPTZ             DEFAULT CURRENT_TIMESTAMP,
    "FundBalanceAfter" NUMERIC(15, 2),
    "CreatedAt"        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"        TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "ApprovedBy"       BIGINT,
    CONSTRAINT fk_expense_fund FOREIGN KEY ("FundId") REFERENCES "SharedFund" ("FundId"),
    CONSTRAINT fk_expense_recipient FOREIGN KEY ("RecipientUserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_expense_approver FOREIGN KEY ("ApprovedBy") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 15) PAYMENT
-- =============================================
CREATE TABLE "Payment"
(
    "PaymentId"        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "PayerUserId"      BIGINT         NOT NULL,
    "FundId"           BIGINT,
    "Amount"           NUMERIC(18, 2) NOT NULL,
    "PaymentDate"      TIMESTAMPTZ             DEFAULT CURRENT_TIMESTAMP,
    "PaymentMethod"    VARCHAR(50),
    "Status"           VARCHAR(20),
    "TransactionCode"  VARCHAR(100),
    "ProviderResponse" TEXT,
    "PaymentType"      VARCHAR(20)    NOT NULL,
    "PaymentCategory"  VARCHAR(20),
    "ChargedUserId"    BIGINT,
    "PersonalReason"   TEXT,
    "MaintenanceId"    BIGINT,
    "PaidAt"           TIMESTAMPTZ,
    "Version"          BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_payment_payer FOREIGN KEY ("PayerUserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_payment_charged FOREIGN KEY ("ChargedUserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_payment_fund FOREIGN KEY ("FundId") REFERENCES "SharedFund" ("FundId"),
    CONSTRAINT fk_payment_maintenance FOREIGN KEY ("MaintenanceId") REFERENCES "Maintenance" ("MaintenanceId"),
    CONSTRAINT ck_payment_personal CHECK ("PaymentCategory" IS DISTINCT FROM 'PERSONAL' OR "ChargedUserId" IS NOT NULL)
);

-- =============================================
-- 16) NOTIFICATION
-- =============================================
CREATE TABLE "Notification"
(
    "NotificationId"   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "UserId"           BIGINT,
    "Title"            VARCHAR(255),
    "Message"          TEXT,
    "NotificationType" VARCHAR(50),
    "IsRead"           BOOLEAN     DEFAULT FALSE,
    "IsDelivered"      BOOLEAN     DEFAULT FALSE,
    "CreatedAt"        TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 17) VEHICLE CHECK
-- =============================================
CREATE TABLE "VehicleCheck"
(
    "Id"           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "BookingId"    BIGINT,
    "CheckType"    VARCHAR(20),
    "Odometer"     INT,
    "BatteryLevel" NUMERIC(5, 2),
    "Cleanliness"  VARCHAR(20),
    "Notes"        TEXT,
    "Issues"       TEXT,
    "Status"       VARCHAR(20),
    "CreatedAt"    TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vehiclecheck_booking FOREIGN KEY ("BookingId") REFERENCES "UsageBooking" ("BookingId")
);

-- =============================================
-- 18) USER DOCUMENT
-- =============================================
CREATE TABLE "UserDocument"
(
    "DocumentId"     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "UserId"         BIGINT       NOT NULL,
    "DocumentNumber" VARCHAR(255)          DEFAULT '',
    "DateOfBirth"    VARCHAR(20)           DEFAULT '',
    "IssueDate"      VARCHAR(20)           DEFAULT '',
    "ExpiryDate"     VARCHAR(20)           DEFAULT '',
    "Address"        TEXT                  DEFAULT '',
    "DocumentType"   VARCHAR(20)           DEFAULT '',
    "Side"           VARCHAR(10)           DEFAULT '',
    "ImageUrl"       VARCHAR(500) NOT NULL DEFAULT '',
    "FileHash"       VARCHAR(64)           DEFAULT '',
    "Status"         VARCHAR(20)           DEFAULT '',
    "ReviewNote"     TEXT                  DEFAULT '',
    "ReviewedBy"     BIGINT,
    "CreatedAt"      TIMESTAMPTZ           DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"      TIMESTAMPTZ           DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_userdocument_user FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_userdocument_reviewer FOREIGN KEY ("ReviewedBy") REFERENCES "Users" ("UserId")
);

-- =============================================
-- 19) VOTING
-- =============================================
CREATE TABLE "Voting"
(
    "VotingId"         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "GroupId"          BIGINT,
    "Title"            VARCHAR(255),
    "Description"      TEXT,
    "VotingType"       VARCHAR(50),
    "Options"          TEXT,
    "Results"          TEXT,
    "Deadline"         TIMESTAMPTZ,
    "Status"           VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    "CreatedBy"        BIGINT,
    "CreatedAt"        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "RelatedExpenseId" BIGINT,
    "EstimatedAmount"  NUMERIC(12, 2),
    CONSTRAINT fk_voting_group FOREIGN KEY ("GroupId") REFERENCES "OwnershipGroup" ("GroupId"),
    CONSTRAINT fk_voting_creator FOREIGN KEY ("CreatedBy") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_voting_expense FOREIGN KEY ("RelatedExpenseId") REFERENCES "Expense" ("ExpenseId")
);

-- =============================================
-- 20) VOTE RECORD
-- =============================================
CREATE TABLE "VoteRecord"
(
    "VoteRecordId"   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "VotingId"       BIGINT      NOT NULL,
    "UserId"         BIGINT      NOT NULL,
    "SelectedOption" VARCHAR(50) NOT NULL,
    "VotedAt"        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_voterecord_voting FOREIGN KEY ("VotingId") REFERENCES "Voting" ("VotingId"),
    CONSTRAINT fk_voterecord_user FOREIGN KEY ("UserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT uq_voterecord UNIQUE ("VotingId", "UserId")
);

-- =============================================
-- 21) INVITATION
-- =============================================
CREATE TABLE "Invitation"
(
    "InvitationId"        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "GroupId"             BIGINT       NOT NULL,
    "InviterUserId"       BIGINT       NOT NULL,
    "InviteeEmail"        VARCHAR(100) NOT NULL,
    "EmailNormalized"     VARCHAR(100) GENERATED ALWAYS AS (LOWER("InviteeEmail")) STORED,
    "Token"               VARCHAR(128) NOT NULL,
    "OtpCode"             VARCHAR(6)   NOT NULL,
    "Status"              VARCHAR(20)  NOT NULL,
    "SuggestedPercentage" NUMERIC(5, 2),
    "ExpiresAt"           TIMESTAMPTZ  NOT NULL,
    "ResendCount"         INT          NOT NULL DEFAULT 0,
    "LastSentAt"          TIMESTAMPTZ,
    "CreatedAt"           TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "AcceptedAt"          TIMESTAMPTZ,
    "AcceptedBy"          BIGINT,
    CONSTRAINT fk_invitation_group FOREIGN KEY ("GroupId") REFERENCES "OwnershipGroup" ("GroupId") ON DELETE CASCADE,
    CONSTRAINT fk_invitation_inviter FOREIGN KEY ("InviterUserId") REFERENCES "Users" ("UserId"),
    CONSTRAINT fk_invitation_acceptor FOREIGN KEY ("AcceptedBy") REFERENCES "Users" ("UserId") ON DELETE SET NULL,
    CONSTRAINT ck_invitation_status CHECK ("Status" IN ('PENDING', 'ACCEPTED', 'EXPIRED')),
    CONSTRAINT ck_invitation_otp CHECK ("OtpCode" ~ '^[0-9]{6}$'),
    CONSTRAINT ck_invitation_pct CHECK ("SuggestedPercentage" IS NULL OR ("SuggestedPercentage" BETWEEN 0 AND 100))
);

-- =============================================
-- 22) OTP TOKEN
-- =============================================
CREATE TABLE "OtpToken"
(
    "TokenId"   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "Email"     VARCHAR(100) NOT NULL,
    "OtpCode"   VARCHAR(6)   NOT NULL CHECK ("OtpCode" ~ '^[0-9]{6}$'),
    "ExpiresAt" TIMESTAMPTZ  NOT NULL,
    "IsUsed"    BOOLEAN     DEFAULT FALSE,
    "CreatedAt" TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- =============================================
-- 23) FINANCIAL REPORT
-- =============================================
CREATE TABLE "FinancialReport"
(
    "ReportId"     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    "FundId"       BIGINT NOT NULL,
    "ReportMonth"  INT,
    "ReportYear"   INT,
    "TotalIncome"  NUMERIC(15, 2),
    "TotalExpense" NUMERIC(15, 2),
    "GeneratedBy"  BIGINT NOT NULL,
    "CreatedAt"    TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    "UpdatedAt"    TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_finreport_fund FOREIGN KEY ("FundId") REFERENCES "SharedFund" ("FundId"),
    CONSTRAINT fk_finreport_user FOREIGN KEY ("GeneratedBy") REFERENCES "Users" ("UserId")
);

-- =============================================
-- INDEXES
-- =============================================
CREATE INDEX IF NOT EXISTS ix_users_email ON "Users" ("Email");
CREATE INDEX IF NOT EXISTS ix_users_role ON "Users" ("RoleId");

CREATE INDEX IF NOT EXISTS ix_ownershipshare_group ON "OwnershipShare" ("GroupId");

CREATE INDEX IF NOT EXISTS ix_vehicle_group ON "Vehicle" ("GroupId");
CREATE INDEX IF NOT EXISTS ix_vehicle_license ON "Vehicle" ("LicensePlate");

CREATE INDEX IF NOT EXISTS ix_vehicleimages_vehicle ON "VehicleImages" ("VehicleId");
CREATE INDEX IF NOT EXISTS ix_vehicleimages_type ON "VehicleImages" ("ImageType");

CREATE INDEX IF NOT EXISTS ix_contract_status ON "Contract" ("ApprovalStatus");
CREATE INDEX IF NOT EXISTS ix_contract_approver ON "Contract" ("ApprovedBy");
CREATE INDEX IF NOT EXISTS ix_contract_group ON "Contract" ("GroupId");

CREATE INDEX IF NOT EXISTS ix_contractfeedback_contract ON "ContractFeedback" ("ContractId");
CREATE INDEX IF NOT EXISTS ix_contractfeedback_user ON "ContractFeedback" ("UserId");

CREATE INDEX IF NOT EXISTS ix_usagebooking_user ON "UsageBooking" ("UserId");
CREATE INDEX IF NOT EXISTS ix_usagebooking_vehicle ON "UsageBooking" ("VehicleId");
CREATE INDEX IF NOT EXISTS ix_usagebooking_start ON "UsageBooking" ("StartDateTime");
CREATE INDEX IF NOT EXISTS ix_usagebooking_status ON "UsageBooking" ("Status");

CREATE INDEX IF NOT EXISTS ix_maintenance_vehicle ON "Maintenance" ("VehicleId");
CREATE INDEX IF NOT EXISTS ix_maintenance_requested ON "Maintenance" ("RequestedBy");
CREATE INDEX IF NOT EXISTS ix_maintenance_status ON "Maintenance" ("Status");

CREATE INDEX IF NOT EXISTS ix_incident_booking ON "Incident" ("BookingId");
CREATE INDEX IF NOT EXISTS ix_incident_user ON "Incident" ("UserId");
CREATE INDEX IF NOT EXISTS ix_incident_approved ON "Incident" ("ApprovedBy");
CREATE INDEX IF NOT EXISTS ix_incident_status ON "Incident" ("Status");

CREATE INDEX IF NOT EXISTS ix_dispute_group ON "Dispute" ("GroupId");
CREATE INDEX IF NOT EXISTS ix_dispute_created ON "Dispute" ("CreatedBy");
CREATE INDEX IF NOT EXISTS ix_dispute_resolved ON "Dispute" ("ResolvedBy");
CREATE INDEX IF NOT EXISTS ix_dispute_status ON "Dispute" ("Status");
CREATE INDEX IF NOT EXISTS ix_dispute_type ON "Dispute" ("DisputeType");
CREATE INDEX IF NOT EXISTS ix_dispute_created_at ON "Dispute" ("CreatedAt");

CREATE INDEX IF NOT EXISTS ix_expense_fund ON "Expense" ("FundId");
CREATE INDEX IF NOT EXISTS ix_expense_source ON "Expense" ("SourceType", "SourceId");

CREATE INDEX IF NOT EXISTS ix_payment_payer ON "Payment" ("PayerUserId");
CREATE INDEX IF NOT EXISTS ix_payment_fund ON "Payment" ("FundId");
CREATE INDEX IF NOT EXISTS ix_payment_status ON "Payment" ("Status");
CREATE INDEX IF NOT EXISTS ix_payment_maintenance ON "Payment" ("MaintenanceId");

CREATE INDEX IF NOT EXISTS ix_notification_user ON "Notification" ("UserId");
CREATE INDEX IF NOT EXISTS ix_notification_read ON "Notification" ("IsRead");

CREATE INDEX IF NOT EXISTS ix_vehiclecheck_booking ON "VehicleCheck" ("BookingId");
CREATE INDEX IF NOT EXISTS ix_vehiclecheck_status ON "VehicleCheck" ("Status");

CREATE INDEX IF NOT EXISTS ix_userdocument_user ON "UserDocument" ("UserId");
CREATE INDEX IF NOT EXISTS ix_userdocument_status ON "UserDocument" ("Status");

CREATE INDEX IF NOT EXISTS ix_voting_group ON "Voting" ("GroupId");
CREATE INDEX IF NOT EXISTS ix_voting_status ON "Voting" ("Status");

CREATE INDEX IF NOT EXISTS ix_voterecord_voting ON "VoteRecord" ("VotingId");
CREATE INDEX IF NOT EXISTS ix_voterecord_user ON "VoteRecord" ("UserId");

CREATE UNIQUE INDEX IF NOT EXISTS uq_invitation_token ON "Invitation" ("Token");
CREATE UNIQUE INDEX IF NOT EXISTS uq_invitation_group_email_pending
    ON "Invitation" ("GroupId", "EmailNormalized")
    WHERE "Status" = 'PENDING';
CREATE INDEX IF NOT EXISTS ix_invitation_expires ON "Invitation" ("ExpiresAt");
CREATE INDEX IF NOT EXISTS ix_invitation_group_expires ON "Invitation" ("GroupId", "ExpiresAt");

CREATE INDEX IF NOT EXISTS ix_financialreport_fund ON "FinancialReport" ("FundId");
CREATE INDEX IF NOT EXISTS ix_financialreport_generated ON "FinancialReport" ("GeneratedBy");
CREATE INDEX IF NOT EXISTS ix_financialreport_period ON "FinancialReport" ("ReportYear", "ReportMonth");

COMMIT;
