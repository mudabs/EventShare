-- V1 created media.sha256 as CHAR(64) (bpchar). The JPA mapping is a plain String
-- (varchar), so Hibernate schema-validation rejects the type mismatch. VARCHAR(64)
-- stores the 64-character hex digest without CHAR blank-padding semantics.
ALTER TABLE media ALTER COLUMN sha256 TYPE varchar(64);
