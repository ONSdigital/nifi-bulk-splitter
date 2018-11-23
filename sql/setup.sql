
IF DB_ID (N'addressindex') IS NOT NULL
  DROP DATABASE addressindex;
GO

CREATE DATABASE addressindex;
GO

IF NOT EXISTS
(SELECT name
 FROM master.sys.server_principals
 WHERE name = 'ai-bulk')
  BEGIN
    CREATE LOGIN [ai-bulk] WITH PASSWORD = N'yourStrong123Password', DEFAULT_DATABASE = addressindex
  END

USE addressindex
GO

CREATE USER [ai-bulk] FOR LOGIN [ai-bulk]

-- ----------------------------
-- Table structure for jobresult
-- ----------------------------
IF EXISTS (SELECT * FROM sys.all_objects WHERE object_id = OBJECT_ID(N'[dbo].[jobresult]') AND type IN ('U'))
  DROP TABLE [dbo].[jobresult]
GO

CREATE TABLE [dbo].[jobresult] (
  [jobID] nvarchar(36) COLLATE SQL_Latin1_General_CP1_CI_AS  NOT NULL,
  [requestTime] datetime2(7)  NOT NULL,
  [completionTime] datetime2(7)  NULL,
  [results] ntext COLLATE SQL_Latin1_General_CP1_CI_AS  NULL
)
GO

ALTER TABLE [dbo].[jobresult] SET (LOCK_ESCALATION = TABLE)
GO

GRANT SELECT, INSERT, UPDATE, DELETE ON [dbo].[jobresult] TO [ai-bulk]

-- ----------------------------
-- Indexes structure for table jobresult
-- ----------------------------
CREATE UNIQUE NONCLUSTERED INDEX [jobid]
  ON [dbo].[jobresult] (
    [jobID] ASC
  )
GO
