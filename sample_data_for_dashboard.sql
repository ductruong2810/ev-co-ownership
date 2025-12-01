-- ============================================
-- Script để thêm dữ liệu mẫu cho Dashboard
-- ============================================
-- HƯỚNG DẪN SỬ DỤNG:
-- 1. Đảm bảo đã chạy sample_seed_data.sql trước
-- 2. Script này sẽ BỔ SUNG thêm dữ liệu (không duplicate)
-- 3. Chạy script này trong PostgreSQL (psql hoặc pgAdmin)
-- 4. Script sẽ tự động:
--    - Tạo thêm ~50 payments (30% COMPLETED để có revenue)
--    - Tạo thêm ~30 expenses
--    - Tạo thêm ~40 bookings
--    - Tạo thêm ~25 contracts
--    - Tạo thêm ~15 disputes
--    - Tạo thêm ~20 incidents
--    - Tạo thêm ~18 maintenances
--    - Cập nhật fund balance
-- ============================================

-- ============================================
-- 1. PAYMENTS (Để có Revenue)
-- ============================================
-- Lấy user ID đầu tiên làm payer
DO
$$
    DECLARE
        v_user_id      BIGINT;
        v_fund_id      BIGINT;
        v_payment_date TIMESTAMP;
    BEGIN
        -- Lấy user ID đầu tiên
        SELECT "UserId" INTO v_user_id FROM "Users" LIMIT 1;

        -- Lấy fund ID đầu tiên (nếu có)
        SELECT "FundId" INTO v_fund_id FROM "SharedFund" LIMIT 1;

        -- Nếu không có fund, tạo một fund mới
        IF v_fund_id IS NULL THEN
            INSERT INTO "SharedFund" ("GroupId", "Balance", "CreatedAt", "UpdatedAt")
            SELECT "GroupId", 0, NOW(), NOW()
            FROM "OwnershipGroup"
            LIMIT 1
            RETURNING "FundId" INTO v_fund_id;
        END IF;

        -- Tạo payments với các status khác nhau và dates khác nhau (30 ngày qua)
        -- Sử dụng transaction code unique để tránh duplicate
        FOR i IN 1..50
            LOOP
                v_payment_date := NOW() - (i || ' days')::INTERVAL + (RANDOM() * 24 || ' hours')::INTERVAL;

                -- Kiểm tra xem transaction code đã tồn tại chưa
                IF NOT EXISTS (SELECT 1
                               FROM "Payment"
                               WHERE "TransactionCode" = 'TXN-DASH-' || LPAD(i::TEXT, 6, '0')) THEN
                    INSERT INTO "Payment" ("PayerUserId",
                                           "FundId",
                                           "Amount",
                                           "PaymentDate",
                                           "PaymentMethod",
                                           "Status",
                                           "TransactionCode",
                                           "PaymentType",
                                           "PaymentCategory",
                                           "Version",
                                           "PaidAt")
                    VALUES (v_user_id,
                            v_fund_id,
                            (500000 + RANDOM() * 5000000)::NUMERIC(18, 2), -- 500K - 5.5M
                            v_payment_date,
                            CASE WHEN RANDOM() > 0.5 THEN 'VNPAY' ELSE 'BANK_TRANSFER' END,
                            CASE
                                WHEN RANDOM() > 0.7 THEN 'COMPLETED' -- 30% completed (revenue)
                                WHEN RANDOM() > 0.5 THEN 'PENDING' -- 20% pending
                                WHEN RANDOM() > 0.3 THEN 'FAILED' -- 20% failed
                                ELSE 'REFUNDED' -- 30% refunded
                                END,
                            'TXN-DASH-' || LPAD(i::TEXT, 6, '0'), -- Prefix DASH để phân biệt
                            'CONTRIBUTION',
                            'GROUP',
                            0,
                            CASE WHEN RANDOM() > 0.7 THEN v_payment_date END);
                END IF;
            END LOOP;
    END
$$;

-- ============================================
-- 2. EXPENSES (Để có Expense Amount)
-- ============================================
DO
$$
    DECLARE
        v_fund_id      BIGINT;
        v_user_id      BIGINT;
        v_expense_date TIMESTAMP;
    BEGIN
        -- Lấy fund ID
        SELECT "FundId" INTO v_fund_id FROM "SharedFund" LIMIT 1;

        -- Lấy user ID
        SELECT "UserId" INTO v_user_id FROM "Users" LIMIT 1;

        -- Create expenses with different dates
        FOR i IN 1..30
            LOOP
                v_expense_date := NOW() - (i || ' days')::INTERVAL + (RANDOM() * 24 || ' hours')::INTERVAL;

                -- Check if an expense with a similar description already exists
                IF NOT EXISTS (SELECT 1 FROM "Expense" WHERE "Description" = 'Sample dashboard expense ' || i) THEN
                    INSERT INTO "Expense" ("FundId",
                                           "SourceType",
                                           "SourceId",
                                           "RecipientUserId",
                                           "Description",
                                           "Amount",
                                           "Status",
                                           "CreatedAt",
                                           "UpdatedAt",
                                           "ExpenseDate",
                                           "ApprovedBy",
                                           "FundBalanceAfter")
                    VALUES (v_fund_id,
                            CASE WHEN RANDOM() > 0.5 THEN 'MAINTENANCE' ELSE 'INCIDENT' END,
                            9999 + i, -- Source ID (dùng số lớn để tránh conflict)
                            CASE WHEN RANDOM() > 0.3 THEN v_user_id END,
                            'Sample dashboard expense ' || i || ' - ' ||
                            CASE WHEN RANDOM() > 0.5 THEN 'Vehicle maintenance' ELSE 'Incident repair' END,
                            (200000 + RANDOM() * 3000000)::NUMERIC(12, 2), -- 200K - 3.2M
                            CASE WHEN RANDOM() > 0.4 THEN 'COMPLETED' ELSE 'PENDING' END,
                            v_expense_date,
                            v_expense_date,
                            v_expense_date,
                            CASE WHEN RANDOM() > 0.4 THEN v_user_id END,
                            (10000000 + RANDOM() * 50000000)::NUMERIC(15, 2) -- Fund balance after
                           );
                END IF;
            END LOOP;
    END
$$;

-- ============================================
-- 3. USAGE BOOKINGS (Để có Booking Statistics)
-- ============================================
DO
$$
    DECLARE
        v_user_id    BIGINT;
        v_vehicle_id BIGINT;
        v_start_date TIMESTAMP;
        v_end_date   TIMESTAMP;
    BEGIN
        -- Lấy user ID và vehicle ID
        SELECT "UserId" INTO v_user_id FROM "Users" LIMIT 1;
        SELECT "VehicleId" INTO v_vehicle_id FROM "Vehicle" LIMIT 1;

        -- Nếu không có vehicle, bỏ qua
        IF v_vehicle_id IS NULL THEN
            RAISE NOTICE 'No vehicle found, skipping bookings';
            RETURN;
        END IF;

        -- Tạo bookings với các status khác nhau
        FOR i IN 1..40
            LOOP
                v_start_date := NOW() - (i || ' days')::INTERVAL + (RANDOM() * 12 || ' hours')::INTERVAL;
                v_end_date := v_start_date + (1 + RANDOM() * 5 || ' hours')::INTERVAL;

                -- Kiểm tra xem booking tương tự đã tồn tại chưa (dựa vào start date gần)
                IF NOT EXISTS (SELECT 1
                               FROM "UsageBooking"
                               WHERE "UserId" = v_user_id
                                 AND "VehicleId" = v_vehicle_id
                                 AND ABS(EXTRACT(EPOCH FROM ("StartDateTime" - v_start_date))) < 3600 -- Trong vòng 1 giờ
                ) THEN
                    INSERT INTO "UsageBooking" ("UserId",
                                                "VehicleId",
                                                "StartDateTime",
                                                "EndDateTime",
                                                "Status",
                                                "TotalDuration",
                                                "Priority",
                                                "CreatedAt",
                                                "UpdatedAt",
                                                "CheckinStatus",
                                                "CheckoutStatus")
                    VALUES (v_user_id,
                            v_vehicle_id,
                            v_start_date,
                            v_end_date,
                            CASE
                                WHEN RANDOM() > 0.6 THEN 'CONFIRMED' -- 40% confirmed
                                WHEN RANDOM() > 0.3 THEN 'COMPLETED' -- 30% completed
                                ELSE 'CANCELLED' -- 30% cancelled
                                END,
                            EXTRACT(EPOCH FROM (v_end_date - v_start_date))::INTEGER / 3600, -- hours
                            CASE WHEN RANDOM() > 0.7 THEN 1 ELSE 2 END,
                            v_start_date - INTERVAL '1 day',
                            v_end_date,
                            CASE WHEN RANDOM() > 0.5 THEN TRUE ELSE FALSE END,
                            CASE WHEN RANDOM() > 0.5 THEN TRUE ELSE FALSE END);
                END IF;
            END LOOP;
    END
$$;

-- ============================================
-- 4. CONTRACTS (Để có Contract Statistics)
-- ============================================
DO
$$
    DECLARE
        v_group_id      BIGINT;
        v_user_id       BIGINT;
        v_contract_date TIMESTAMP;
    BEGIN
        -- Lấy group ID và user ID
        SELECT "GroupId" INTO v_group_id FROM "OwnershipGroup" LIMIT 1;
        SELECT "UserId" INTO v_user_id FROM "Users" LIMIT 1;

        IF v_group_id IS NULL THEN
            RAISE NOTICE 'No group found, skipping contracts';
            RETURN;
        END IF;

        -- Create contracts with different statuses
        FOR i IN 1..25
            LOOP
                v_contract_date := NOW() - (i || ' days')::INTERVAL;

                -- Check if a contract with similar terms already exists
                IF NOT EXISTS (SELECT 1 FROM "Contract" WHERE "Terms" LIKE '%Dashboard sample ' || i || '%') THEN
                    INSERT INTO "Contract" ("GroupId",
                                            "StartDate",
                                            "EndDate",
                                            "Terms",
                                            "RequiredDepositAmount",
                                            "IsActive",
                                            "ApprovalStatus",
                                            "CreatedAt",
                                            "UpdatedAt",
                                            "ApprovedBy",
                                            "ApprovedAt")
                    VALUES (v_group_id,
                            (v_contract_date)::DATE,
                            (v_contract_date + INTERVAL '365 days')::DATE,
                            'Dashboard sample contract terms number ' || i ||
                            '. Detailed content about the rights and obligations of the parties.',
                            (3000000 + RANDOM() * 5000000)::NUMERIC(15, 2), -- 3M - 8M
                            CASE WHEN RANDOM() > 0.3 THEN TRUE ELSE FALSE END,
                            CASE
                                WHEN RANDOM() > 0.5 THEN 'APPROVED' -- 50% approved
                                WHEN RANDOM() > 0.3 THEN 'PENDING' -- 20% pending
                                WHEN RANDOM() > 0.1 THEN 'SIGNED' -- 20% signed
                                ELSE 'REJECTED' -- 10% rejected
                                END,
                            v_contract_date,
                            v_contract_date + INTERVAL '1 day',
                            CASE WHEN RANDOM() > 0.5 THEN v_user_id END,
                            CASE WHEN RANDOM() > 0.5 THEN v_contract_date + INTERVAL '2 days' END);
                END IF;
            END LOOP;
    END
$$;

-- ============================================
-- 5. DISPUTES (Để có Dispute Statistics)
-- ============================================
DO
$$
    DECLARE
        v_user_id      BIGINT;
        v_group_id     BIGINT;
        v_dispute_date TIMESTAMP;
    BEGIN
        SELECT "UserId" INTO v_user_id FROM "Users" LIMIT 1;
        SELECT "GroupId" INTO v_group_id FROM "OwnershipGroup" LIMIT 1;

        IF v_group_id IS NULL THEN
            RAISE NOTICE 'No group found, skipping disputes';
            RETURN;
        END IF;

        -- Create disputes with different statuses
        FOR i IN 1..15
            LOOP
                v_dispute_date := NOW() - (i || ' days')::INTERVAL;

                -- Check if a dispute with a similar title already exists
                IF NOT EXISTS (SELECT 1 FROM "Dispute" WHERE "Title" = 'Dashboard sample dispute ' || i) THEN
                    INSERT INTO "Dispute" ("GroupId",
                                           "CreatedBy",
                                           "DisputeType",
                                           "Title",
                                           "Description",
                                           "Status",
                                           "CreatedAt",
                                           "UpdatedAt")
                    VALUES (v_group_id,
                            v_user_id,
                            CASE
                                WHEN RANDOM() > 0.5 THEN 'USAGE' -- 50% usage
                                WHEN RANDOM() > 0.3 THEN 'FINANCIAL' -- 20% financial
                                WHEN RANDOM() > 0.2 THEN 'CHARGING' -- 10% charging
                                WHEN RANDOM() > 0.1 THEN 'DECISION' -- 10% decision
                                ELSE 'OTHERS' -- 10% others
                                END,
                            'Dashboard sample dispute ' || i,
                            'Dashboard sample dispute description number ' || i,
                            CASE
                                WHEN RANDOM() > 0.5 THEN 'OPEN' -- 50% open
                                WHEN RANDOM() > 0.3 THEN 'RESOLVED' -- 20% resolved
                                ELSE 'REJECTED' -- 30% rejected
                                END,
                            v_dispute_date,
                            v_dispute_date + INTERVAL '1 day');
                END IF;
            END LOOP;
    END
$$;

-- ============================================
-- 6. INCIDENTS (Để có Incident Statistics)
-- ============================================
DO
$$
    DECLARE
        v_booking_id    BIGINT;
        v_user_id       BIGINT;
        v_incident_date TIMESTAMP;
    BEGIN
        SELECT "BookingId" INTO v_booking_id FROM "UsageBooking" LIMIT 1;
        SELECT "UserId" INTO v_user_id FROM "Users" LIMIT 1;

        IF v_booking_id IS NULL THEN
            RAISE NOTICE 'No booking found, skipping incidents';
            RETURN;
        END IF;

        -- Create incidents with different statuses
        FOR i IN 1..20
            LOOP
                v_incident_date := NOW() - (i || ' days')::INTERVAL;

                -- Check if an incident with a similar description already exists
                IF NOT EXISTS (SELECT 1 FROM "Incident" WHERE "Description" = 'Dashboard sample incident description number ' || i) THEN
                    INSERT INTO "Incident" ("BookingId",
                                            "UserId",
                                            "Description",
                                            "ActualCost",
                                            "Status",
                                            "CreatedAt",
                                            "UpdatedAt")
                    VALUES (v_booking_id,
                            v_user_id,
                            'Dashboard sample incident description number ' || i,
                            (300000 + RANDOM() * 1500000)::NUMERIC(12, 2), -- 300K - 1.8M
                            CASE
                                WHEN RANDOM() > 0.5 THEN 'APPROVED' -- 50% approved
                                WHEN RANDOM() > 0.3 THEN 'PENDING' -- 20% pending
                                ELSE 'REJECTED' -- 30% rejected
                                END,
                            v_incident_date,
                            v_incident_date + INTERVAL '1 day');
                END IF;
            END LOOP;
    END
$$;

-- ============================================
-- 7. MAINTENANCES (Để có Maintenance Statistics)
-- ============================================
DO
$$
    DECLARE
        v_vehicle_id       BIGINT;
        v_user_id          BIGINT;
        v_maintenance_date TIMESTAMP;
    BEGIN
        SELECT "VehicleId" INTO v_vehicle_id FROM "Vehicle" LIMIT 1;
        SELECT "UserId" INTO v_user_id FROM "Users" LIMIT 1;

        IF v_vehicle_id IS NULL THEN
            RAISE NOTICE 'No vehicle found, skipping maintenances';
            RETURN;
        END IF;

        -- Create maintenances with different statuses
        FOR i IN 1..18
            LOOP
                v_maintenance_date := NOW() - (i || ' days')::INTERVAL;

                -- Check if a maintenance with a similar description already exists
                IF NOT EXISTS (SELECT 1
                               FROM "Maintenance"
                               WHERE "Description" = 'Dashboard sample maintenance description number ' || i) THEN
                    INSERT INTO "Maintenance" ("VehicleId",
                                               "RequestedBy",
                                               "Description",
                                               "ActualCost",
                                               "Status",
                                               "CoverageType",
                                               "RequestDate",
                                               "CreatedAt",
                                               "UpdatedAt")
                    VALUES (v_vehicle_id,
                            v_user_id,
                            'Dashboard sample maintenance description number ' || i,
                            (500000 + RANDOM() * 2000000)::NUMERIC(12, 2), -- 500K - 2.5M
                            CASE
                                WHEN RANDOM() > 0.5 THEN 'COMPLETED' -- 50% completed
                                WHEN RANDOM() > 0.3 THEN 'IN_PROGRESS' -- 20% in progress
                                WHEN RANDOM() > 0.1 THEN 'APPROVED' -- 20% approved
                                ELSE 'PENDING' -- 10% pending
                                END,
                            CASE WHEN RANDOM() > 0.5 THEN 'GROUP' ELSE 'PERSONAL' END,
                            v_maintenance_date,
                            v_maintenance_date,
                            v_maintenance_date + INTERVAL '1 day');
                END IF;
            END LOOP;
    END
$$;

-- ============================================
-- 8. Cập nhật SharedFund balance (từ payments completed)
-- ============================================
DO
$$
    DECLARE
        v_fund_id       BIGINT;
        v_total_revenue NUMERIC;
    BEGIN
        SELECT "FundId" INTO v_fund_id FROM "SharedFund" LIMIT 1;

        IF v_fund_id IS NOT NULL THEN
            -- Tính tổng revenue từ payments completed
            SELECT COALESCE(SUM("Amount"), 0)
            INTO v_total_revenue
            FROM "Payment"
            WHERE "Status" = 'COMPLETED'
              AND "FundId" = v_fund_id;

            -- Cập nhật balance
            UPDATE "SharedFund"
            SET "Balance"   = v_total_revenue,
                "UpdatedAt" = NOW()
            WHERE "FundId" = v_fund_id;

            RAISE NOTICE 'Updated fund balance to %', v_total_revenue;
        END IF;
    END
$$;

-- ============================================
-- Xem kết quả
-- ============================================
SELECT 'Payments' as "Table", COUNT(*) as "Count"
FROM "Payment"
UNION ALL
SELECT 'Expenses', COUNT(*)
FROM "Expense"
UNION ALL
SELECT 'Bookings', COUNT(*)
FROM "UsageBooking"
UNION ALL
SELECT 'Contracts', COUNT(*)
FROM "Contract"
UNION ALL
SELECT 'Disputes', COUNT(*)
FROM "Dispute"
UNION ALL
SELECT 'Incidents', COUNT(*)
FROM "Incident"
UNION ALL
SELECT 'Maintenances', COUNT(*)
FROM "Maintenance";

-- Xem revenue và expense
SELECT 'Total Revenue (COMPLETED payments)' as "Metric",
       COALESCE(SUM("Amount"), 0)           as "Amount"
FROM "Payment"
WHERE "Status" = 'COMPLETED'
UNION ALL
SELECT 'Total Expenses',
       COALESCE(SUM("Amount"), 0)
FROM "Expense";

