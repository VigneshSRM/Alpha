-- Restaurant businesses may keep their FSSAI licence on the profile. Both the
-- number and its expiry are optional, so both columns allow NULL.
ALTER TABLE business_profile
    ADD COLUMN fssai        VARCHAR(14) NULL AFTER gstin,
    ADD COLUMN fssai_expiry DATE        NULL AFTER fssai;
