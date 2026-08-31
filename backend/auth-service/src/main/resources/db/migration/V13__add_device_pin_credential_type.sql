-- CredentialType.DEVICE_PIN backs the new per-device app-PIN login (see
-- PinController) -- widen the existing CHECK constraint rather than
-- dropping it, matching how every other credential type is already
-- enumerated here explicitly.

ALTER TABLE identity_credential DROP CONSTRAINT identity_credential_credential_type_check;
ALTER TABLE identity_credential ADD CONSTRAINT identity_credential_credential_type_check
    CHECK (credential_type IN ('PASSWORD', 'GOOGLE', 'APPLE', 'PASSKEY', 'OTP_PHONE', 'DEVICE_PIN'));
