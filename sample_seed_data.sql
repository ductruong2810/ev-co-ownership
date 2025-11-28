-- =============================================
-- EV Co-ownership Sample Data (PostgreSQL)
-- Use AFTER running database_schema.sql on a clean database
-- Default plaintext used for seeded accounts: "password"
-- (BCrypt hash: $2a$12$EXRkfkdmXn2gzds2SSituuO2Z0fvPoTSwdDTIw4V8OnpG/Sf.lRe2)
-- =============================================

BEGIN;

-- 1. Roles
INSERT INTO "Roles" ("RoleName")
VALUES ('CO_OWNER'),
       ('STAFF'),
       ('ADMIN'),
       ('TECHNICIAN')
ON CONFLICT ("RoleName") DO NOTHING;

-- 2. Users (admin, staff, technician, co-owners)
WITH base_hash AS (SELECT '$2a$12$EXRkfkdmXn2gzds2SSituuO2Z0fvPoTSwdDTIw4V8OnpG/Sf.lRe2' AS hash)
INSERT
INTO "Users" ("FullName", "Email", "PasswordHash", "PhoneNumber", "AvatarUrl",
              "RoleId", "Status", "CreatedAt", "UpdatedAt")
VALUES ('Amelia Rivera', 'admin@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0188',
        'https://images.evshare.com/avatars/admin.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'ADMIN'), 'ACTIVE', NOW() - INTERVAL '90 days', NOW()),
       ('Lucas Bennett', 'staff.operations@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0190',
        'https://images.evshare.com/avatars/staff.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'STAFF'), 'ACTIVE', NOW() - INTERVAL '80 days', NOW()),
       ('Harper Nguyen', 'technician@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0192',
        'https://images.evshare.com/avatars/technician.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'TECHNICIAN'), 'ACTIVE', NOW() - INTERVAL '75 days', NOW()),
       ('Dylan Carter', 'dylan.carter@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0111',
        'https://images.evshare.com/avatars/dylan.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'CO_OWNER'), 'ACTIVE', NOW() - INTERVAL '60 days', NOW()),
       ('Jade Williams', 'jade.williams@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0112',
        'https://images.evshare.com/avatars/jade.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'CO_OWNER'), 'ACTIVE', NOW() - INTERVAL '50 days', NOW()),
       ('Noah Patel', 'noah.patel@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0113',
        'https://images.evshare.com/avatars/noah.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'CO_OWNER'), 'ACTIVE', NOW() - INTERVAL '45 days', NOW()),
       ('Sophia Tran', 'sophia.tran@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0114',
        'https://images.evshare.com/avatars/sophia.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'CO_OWNER'), 'ACTIVE', NOW() - INTERVAL '40 days', NOW()),
       ('Ethan Morales', 'ethan.morales@evshare.com', (SELECT hash FROM base_hash), '+1-415-555-0115',
        'https://images.evshare.com/avatars/ethan.png',
        (SELECT "RoleId" FROM "Roles" WHERE "RoleName" = 'CO_OWNER'), 'ACTIVE', NOW() - INTERVAL '38 days', NOW());

-- 3. Ownership groups
INSERT INTO "OwnershipGroup" ("GroupName", "Status", "Description", "MemberCapacity", "CreatedAt", "UpdatedAt")
VALUES ('Electric Pioneers Club', 'ACTIVE', 'Premium EV sharing group across downtown districts.', 6,
        NOW() - INTERVAL '50 days', NOW()),
       ('Harbor City Night Riders', 'PENDING', 'Late-night ride enthusiasts pooling two long-range SUVs.', 5,
        NOW() - INTERVAL '30 days', NOW()),
       ('Solar Trailblazers Collective', 'ACTIVE', 'Families sharing solar-charged crossovers for weekend getaways.', 8,
        NOW() - INTERVAL '20 days', NOW())
ON CONFLICT ("GroupName") DO NOTHING;

-- 4. Shared funds (OPERATING + DEPOSIT)
INSERT INTO "SharedFund" ("GroupId", "Balance", "TargetAmount", "FundType", "IsSpendable")
VALUES ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'), 18250.00, 25000.00,
        'OPERATING', TRUE),
       ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'), 6000.00, 6000.00,
        'DEPOSIT_RESERVE', FALSE),
       ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Harbor City Night Riders'), 5400.00, 9000.00,
        'OPERATING', TRUE),
       ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Solar Trailblazers Collective'), 9700.00, 15000.00,
        'OPERATING', TRUE)
ON CONFLICT DO NOTHING;

-- 5. Update group primary fund references (first OPERATING fund per group)
UPDATE "OwnershipGroup" g
SET "FundId" = sf."FundId"
FROM (SELECT DISTINCT ON ("GroupId") "FundId", "GroupId"
      FROM "SharedFund"
      WHERE "FundType" = 'OPERATING'
      ORDER BY "GroupId", "FundId") sf
WHERE g."GroupId" = sf."GroupId";

-- 6. Ownership shares
INSERT INTO "OwnershipShare" ("UserId", "GroupId", "GroupRole", "OwnershipPercentage", "DepositStatus")
VALUES ((SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'),
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'), 'ADMIN', 35.0, 'PAID'),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'jade.williams@evshare.com'),
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'), 'MEMBER', 25.0, 'PAID'),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'noah.patel@evshare.com'),
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'), 'MEMBER', 20.0, 'PAID'),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'sophia.tran@evshare.com'),
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Harbor City Night Riders'), 'ADMIN', 40.0,
        'PENDING'),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'ethan.morales@evshare.com'),
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Harbor City Night Riders'), 'MEMBER', 20.0,
        'PENDING')
ON CONFLICT DO NOTHING;

-- 7. Vehicles
INSERT INTO "Vehicle" ("Brand", "Model", "LicensePlate", "ChassisNumber", "VehicleValue", "GroupId")
VALUES ('Tesla', 'Model Y Performance', 'EV-88A-777', 'TY5K8L1A2234', 62000.00,
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club')),
       ('Hyundai', 'Ioniq 5 Limited', 'EV-55B-642', 'HN9G7F1Z9543', 52000.00,
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Harbor City Night Riders')),
       ('Kia', 'EV9 GT-Line', 'EV-90C-903', 'KIAEV92025GT', 78000.00,
        (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Solar Trailblazers Collective'))
ON CONFLICT DO NOTHING;

-- 8. Vehicle images
INSERT INTO "VehicleImages" ("VehicleId", "ImageUrl", "ImageType", "ApprovalStatus", "ApprovedBy")
VALUES ((SELECT "VehicleId" FROM "Vehicle" WHERE "LicensePlate" = 'EV-88A-777'),
        'https://images.evshare.com/vehicles/model-y-front.jpg', 'EXTERIOR', 'APPROVED',
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'staff.operations@evshare.com')),
       ((SELECT "VehicleId" FROM "Vehicle" WHERE "LicensePlate" = 'EV-88A-777'),
        'https://images.evshare.com/vehicles/model-y-cabin.jpg', 'INTERIOR', 'APPROVED',
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'staff.operations@evshare.com')),
       ((SELECT "VehicleId" FROM "Vehicle" WHERE "LicensePlate" = 'EV-55B-642'),
        'https://images.evshare.com/vehicles/ioniq5-night.jpg', 'EXTERIOR', 'PENDING', NULL)
ON CONFLICT DO NOTHING;

-- 9. Contract + feedback
INSERT INTO "Contract" ("GroupId", "StartDate", "EndDate", "Terms", "RequiredDepositAmount",
                        "IsActive", "ApprovalStatus", "ApprovedBy")
VALUES ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'),
        CURRENT_DATE - 60, CURRENT_DATE + 305,
        'Quarterly review clause with 1,200km monthly allocation per owner.', 6000.00,
        TRUE, 'APPROVED', (SELECT "UserId" FROM "Users" WHERE "Email" = 'admin@evshare.com')),
       ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Harbor City Night Riders'),
        CURRENT_DATE - 15, CURRENT_DATE + 175,
        'Shared SUV rotation with mandatory overnight charging at HQ.', 4500.00,
        TRUE, 'PENDING_MEMBER_APPROVAL', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO "ContractFeedback" ("ContractId", "UserId", "Status", "ReactionType", "Reason", "SubmittedAt")
VALUES ((SELECT "ContractId"
         FROM "Contract" c
                  JOIN "OwnershipGroup" g ON g."GroupId" = c."GroupId"
         WHERE g."GroupName" = 'Electric Pioneers Club'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'jade.williams@evshare.com'), 'AGREED', 'THUMBS_UP',
        'Looks great, thanks for adding the crossover detailing clause.', NOW() - INTERVAL '55 days'),
       ((SELECT "ContractId"
         FROM "Contract" c
                  JOIN "OwnershipGroup" g ON g."GroupId" = c."GroupId"
         WHERE g."GroupName" = 'Electric Pioneers Club'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'noah.patel@evshare.com'), 'REVISION_REQUESTED', 'QUESTION',
        'Requesting flexibility for airport drop-offs after 11 PM.', NOW() - INTERVAL '54 days')
ON CONFLICT DO NOTHING;

-- 10. Usage bookings
INSERT INTO "UsageBooking" ("UserId", "VehicleId", "StartDateTime", "EndDateTime", "Status", "TotalDuration",
                            "Priority",
                            "QrCodeCheckin", "QrCodeCheckout", "CheckinStatus", "CheckoutStatus")
VALUES ((SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'),
        (SELECT "VehicleId" FROM "Vehicle" WHERE "LicensePlate" = 'EV-88A-777'),
        NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days' + INTERVAL '6 hours', 'COMPLETED', 360, 1,
        'CHK-DYLAN-001', 'OUT-DYLAN-001', TRUE, TRUE),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'jade.williams@evshare.com'),
        (SELECT "VehicleId" FROM "Vehicle" WHERE "LicensePlate" = 'EV-88A-777'),
        NOW() + INTERVAL '1 day', NOW() + INTERVAL '1 day' + INTERVAL '8 hours', 'CONFIRMED', NULL, 2,
        'CHK-JADE-004', NULL, FALSE, FALSE)
ON CONFLICT DO NOTHING;

-- 11. Maintenance & incident
INSERT INTO "Maintenance" ("VehicleId", "RequestedBy", "ApprovedBy", "Description", "ActualCost", "Status",
                           "CoverageType",
                           "RequestDate", "MaintenanceDate")
VALUES ((SELECT "VehicleId" FROM "Vehicle" WHERE "LicensePlate" = 'EV-88A-777'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'staff.operations@evshare.com'),
        'Quarterly tire rotation and brake inspection.', 420.00, 'APPROVED', 'GROUP',
        NOW() - INTERVAL '12 days', NOW() - INTERVAL '10 days'),
       ((SELECT "VehicleId" FROM "Vehicle" WHERE "LicensePlate" = 'EV-55B-642'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'sophia.tran@evshare.com'),
        NULL,
        'Interior detailing after overnight airport shuttle.', 185.00, 'PENDING', 'PERSONAL',
        NOW() - INTERVAL '3 days', NULL)
ON CONFLICT DO NOTHING;

INSERT INTO "Incident" ("BookingId", "UserId", "Description", "ActualCost", "Status", "RejectionCategory")
VALUES ((SELECT "BookingId"
         FROM "UsageBooking" ub
                  JOIN "Users" u ON u."UserId" = ub."UserId"
         WHERE u."Email" = 'dylan.carter@evshare.com'
         LIMIT 1),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'),
        'Rear bumper scuff while reversing at Embarcadero charger.', 275.00, 'PENDING', 'MINOR_DAMAGE')
ON CONFLICT DO NOTHING;

-- 12. Dispute
INSERT INTO "Dispute" ("GroupId", "CreatedBy", "DisputeType", "Status", "Title", "Description")
VALUES ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'noah.patel@evshare.com'),
        'USAGE', 'OPEN', 'Request to extend Friday evening slot',
        'Looking to extend access until 9:30 PM on Fridays for mentoring program.')
ON CONFLICT DO NOTHING;

-- 13. Expense + payment
INSERT INTO "Expense" ("FundId", "SourceType", "SourceId", "RecipientUserId", "Description", "Amount", "Status")
VALUES ((SELECT "FundId"
         FROM "SharedFund"
         WHERE "GroupId" = (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club')
           AND "FundType" = 'OPERATING'),
        'MAINTENANCE',
        (SELECT "MaintenanceId"
         FROM "Maintenance" m
                  JOIN "Vehicle" v ON v."VehicleId" = m."VehicleId"
         WHERE v."LicensePlate" = 'EV-88A-777'
         LIMIT 1),
        NULL,
        'Tire rotation & brake inspection approved by operations staff.', 420.00, 'APPROVED')
ON CONFLICT DO NOTHING;

INSERT INTO "Payment" ("PayerUserId", "FundId", "Amount", "PaymentMethod", "Status", "TransactionCode",
                       "PaymentType", "PaymentCategory", "ChargedUserId", "PaidAt")
VALUES ((SELECT "UserId" FROM "Users" WHERE "Email" = 'jade.williams@evshare.com'),
        (SELECT "FundId"
         FROM "SharedFund"
         WHERE "GroupId" = (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club')
           AND "FundType" = 'OPERATING'),
        850.00, 'VNPAY', 'COMPLETED', 'VNP-2024-10-INV-8842', 'CONTRIBUTION', 'GROUP', NULL,
        NOW() - INTERVAL '14 days'),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'),
        (SELECT "FundId"
         FROM "SharedFund"
         WHERE "GroupId" = (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club')
           AND "FundType" = 'DEPOSIT_RESERVE'),
        2000.00, 'BANK_TRANSFER', 'COMPLETED', 'BANK-2024-09-DEPOSIT-221', 'DEPOSIT', 'PERSONAL',
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'), NOW() - INTERVAL '45 days')
ON CONFLICT DO NOTHING;

-- 14. Notifications
INSERT INTO "Notification" ("UserId", "Title", "Message", "NotificationType", "IsRead")
VALUES ((SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'),
        'Maintenance request approved', 'Your request for Model Y service has been approved and scheduled.',
        'MAINTENANCE_APPROVED', TRUE),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'sophia.tran@evshare.com'),
        'Reminder: submit deposit proof', 'Please upload the proof of deposit before Friday 5 PM.', 'DEPOSIT_REQUIRED',
        FALSE)
ON CONFLICT DO NOTHING;

-- 15. Vehicle check
INSERT INTO "VehicleCheck" ("BookingId", "CheckType", "Odometer", "BatteryLevel", "Cleanliness", "Status")
VALUES ((SELECT "BookingId"
         FROM "UsageBooking" ub
                  JOIN "Users" u ON u."UserId" = ub."UserId"
         WHERE u."Email" = 'dylan.carter@evshare.com'
         LIMIT 1),
        'CHECKIN', 18250, 0.87, 'CLEAN', 'COMPLETED')
ON CONFLICT DO NOTHING;

-- 16. User documents
INSERT INTO "UserDocument" ("UserId", "DocumentNumber", "IssueDate", "ExpiryDate", "Address", "DocumentType", "Side",
                            "ImageUrl", "Status")
VALUES ((SELECT "UserId" FROM "Users" WHERE "Email" = 'jade.williams@evshare.com'), 'A25789931', '2019-04-12',
        '2029-04-11', '220 Pine Street, San Francisco, CA', 'DRIVER_LICENSE', 'FRONT',
        'https://images.evshare.com/documents/jade-id-front.png', 'APPROVED'),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'noah.patel@evshare.com'), 'A25789931', '2019-04-12',
        '2029-04-11', '220 Pine Street, San Francisco, CA', 'DRIVER_LICENSE', 'BACK',
        'https://images.evshare.com/documents/jade-id-back.png', 'APPROVED'),
       ((SELECT "UserId" FROM "Users" WHERE "Email" = 'sophia.tran@evshare.com'), 'C88412903', '2021-01-08',
        '2031-01-07', '117 Valencia Street, San Francisco, CA', 'DRIVER_LICENSE', 'FRONT',
        'https://images.evshare.com/documents/sophia-id-front.png', 'PENDING')
ON CONFLICT DO NOTHING;

-- 17. Voting session
INSERT INTO "Voting" ("GroupId", "Title", "Description", "VotingType", "Options", "Status", "CreatedBy", "Deadline")
VALUES ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'),
        'Select color scheme for winter wrap',
        'Vote on matte midnight silver vs arctic white wrap for Q4 marketing push.', 'SINGLE_CHOICE',
        '["Matte Midnight Silver","Arctic White"]', 'ACTIVE',
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'), NOW() + INTERVAL '5 days')
ON CONFLICT DO NOTHING;

INSERT INTO "VoteRecord" ("VotingId", "UserId", "SelectedOption")
VALUES ((SELECT "VotingId" FROM "Voting" WHERE "Title" = 'Select color scheme for winter wrap'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'), 'Matte Midnight Silver'),
       ((SELECT "VotingId" FROM "Voting" WHERE "Title" = 'Select color scheme for winter wrap'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'jade.williams@evshare.com'), 'Arctic White')
ON CONFLICT DO NOTHING;

-- 18. Invitations & OTP
INSERT INTO "Invitation" ("GroupId", "InviterUserId", "InviteeEmail", "Token", "OtpCode", "Status",
                          "SuggestedPercentage", "ExpiresAt")
VALUES ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'dylan.carter@evshare.com'),
        'olivia.meadows@evshare.com', 'INV-EP-2024-102', '384912', 'PENDING', 15.0, NOW() + INTERVAL '3 days'),
       ((SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Harbor City Night Riders'),
        (SELECT "UserId" FROM "Users" WHERE "Email" = 'sophia.tran@evshare.com'),
        'kevin.cho@evshare.com', 'INV-HC-2024-077', '902144', 'ACCEPTED', 20.0, NOW() - INTERVAL '1 day')
ON CONFLICT DO NOTHING;

INSERT INTO "OtpToken" ("Email", "OtpCode", "ExpiresAt", "IsUsed")
VALUES ('olivia.meadows@evshare.com', '384912', NOW() + INTERVAL '3 days', FALSE),
       ('kevin.cho@evshare.com', '902144', NOW() - INTERVAL '1 day', TRUE)
ON CONFLICT DO NOTHING;

-- 19. Financial report
INSERT INTO "FinancialReport" ("FundId", "ReportMonth", "ReportYear", "TotalIncome", "TotalExpense", "GeneratedBy")
VALUES ((SELECT "FundId"
         FROM "SharedFund"
         WHERE "GroupId" = (SELECT "GroupId" FROM "OwnershipGroup" WHERE "GroupName" = 'Electric Pioneers Club')
           AND "FundType" = 'OPERATING'),
        EXTRACT(MONTH FROM CURRENT_DATE - INTERVAL '1 month'), EXTRACT(YEAR FROM CURRENT_DATE - INTERVAL '1 month'),
        8600.00, 3820.00, (SELECT "UserId" FROM "Users" WHERE "Email" = 'staff.operations@evshare.com'))
ON CONFLICT DO NOTHING;

COMMIT;
