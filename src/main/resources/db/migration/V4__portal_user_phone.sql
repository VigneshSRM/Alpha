-- Mobile number captured at sign-up. Stored in E.164 (+<dial><national>), validated
-- against the dialling-code table in in.alphagroup.portal.policy.Countries.
-- Nullable: accounts created before this migration have no number on file.
ALTER TABLE portal_user
    ADD COLUMN phone VARCHAR(20) NULL AFTER full_name;
