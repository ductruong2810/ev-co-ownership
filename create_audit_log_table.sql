-- Create AuditLog table for tracking system activities and user actions
-- Run this script in your PostgreSQL database

CREATE TABLE IF NOT EXISTS "AuditLog" (
    "AuditLogId" BIGSERIAL PRIMARY KEY,
    "UserId" BIGINT,
    "ActionType" VARCHAR(50) NOT NULL,
    "EntityType" VARCHAR(50),
    "EntityId" VARCHAR(100),
    "Message" TEXT NOT NULL,
    "Metadata" JSONB,
    "CreatedAt" TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT "FK_AuditLog_User" FOREIGN KEY ("UserId") REFERENCES "Users"("UserId") ON DELETE SET NULL
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS "IDX_AuditLog_UserId" ON "AuditLog"("UserId");
CREATE INDEX IF NOT EXISTS "IDX_AuditLog_ActionType" ON "AuditLog"("ActionType");
CREATE INDEX IF NOT EXISTS "IDX_AuditLog_EntityType" ON "AuditLog"("EntityType");
CREATE INDEX IF NOT EXISTS "IDX_AuditLog_CreatedAt" ON "AuditLog"("CreatedAt" DESC);
CREATE INDEX IF NOT EXISTS "IDX_AuditLog_Entity" ON "AuditLog"("EntityType", "EntityId");

-- Add comment
COMMENT ON TABLE "AuditLog" IS 'Stores audit logs for system activities and user actions for compliance and debugging';

