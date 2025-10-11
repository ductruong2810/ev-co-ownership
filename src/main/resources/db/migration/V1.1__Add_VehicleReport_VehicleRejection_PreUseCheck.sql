-- V1.1__Add_VehicleReport_VehicleRejection_PreUseCheck.sql
-- Migration to add new entities and update existing ones

-- Update VehicleReport table
ALTER TABLE VehicleReport
    ADD COLUMN VehicleId BIGINT,
        ADD COLUMN BookingId BIGINT,
        ADD COLUMN ReportedBy BIGINT,
        ADD COLUMN Odometer INT,
        ADD COLUMN BatteryLevel DECIMAL (5,2),
        ADD COLUMN RejectionReason TEXT,
        ADD COLUMN ResolutionNotes TEXT;

-- Create VehicleRejection table
CREATE TABLE VehicleRejection
(
    RejectionId     BIGINT PRIMARY KEY AUTO_INCREMENT,
    VehicleId       BIGINT,
    BookingId       BIGINT,
    RejectedBy      BIGINT,
    RejectionReason VARCHAR(20),
    DetailedReason  TEXT,
    Photos          TEXT,
    Status          VARCHAR(20),
    RejectedAt      DATETIME,
    ResolvedAt      DATETIME,
    FOREIGN KEY (VehicleId) REFERENCES Vehicle (VehicleId),
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId),
    FOREIGN KEY (RejectedBy) REFERENCES User (UserId)
);

-- Create PreUseCheck table
CREATE TABLE PreUseCheck
(
    PreUseCheckId  BIGINT PRIMARY KEY AUTO_INCREMENT,
    BookingId      BIGINT,
    UserId         BIGINT,
    ExteriorDamage BOOLEAN,
    InteriorClean  BOOLEAN,
    WarningLights  BOOLEAN,
    TireCondition  BOOLEAN,
    UserNotes      TEXT,
    CheckTime      DATETIME,
    FOREIGN KEY (BookingId) REFERENCES UsageBooking (BookingId),
    FOREIGN KEY (UserId) REFERENCES User (UserId)
);

-- Update Expense table
ALTER TABLE Expense
    ADD COLUMN SourceType VARCHAR(20),
        ADD COLUMN SourceId BIGINT;

-- Create indexes
CREATE INDEX idx_vehicle_report_vehicle ON VehicleReport (VehicleId);
CREATE INDEX idx_vehicle_report_booking ON VehicleReport (BookingId);
CREATE INDEX idx_vehicle_rejection_vehicle ON VehicleRejection (VehicleId);
CREATE INDEX idx_vehicle_rejection_status ON VehicleRejection (Status);
CREATE INDEX idx_pre_use_check_booking ON PreUseCheck (BookingId);
CREATE INDEX idx_expense_source ON Expense (SourceType, SourceId);
