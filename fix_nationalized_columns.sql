-- Fix columns that were created as bytea due to @Nationalized annotation
-- Run this script in your PostgreSQL/Supabase database

-- Fix OwnershipGroup table
ALTER TABLE "OwnershipGroup"
    ALTER COLUMN "GroupName" TYPE VARCHAR(100) USING "GroupName"::TEXT;

ALTER TABLE "OwnershipGroup"
    ALTER COLUMN "Description" TYPE TEXT USING "Description"::TEXT;

ALTER TABLE "OwnershipGroup"
    ALTER COLUMN "RejectionReason" TYPE TEXT USING "RejectionReason"::TEXT;

-- Note: If you have other tables with @Nationalized fields, add similar ALTER statements here
-- Example for Users table (if needed):
-- ALTER TABLE "Users" 
--     ALTER COLUMN "FullName" TYPE VARCHAR(100) USING "FullName"::TEXT;

