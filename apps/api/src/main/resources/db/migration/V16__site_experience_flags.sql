ALTER TABLE site_setting
    ADD COLUMN music_enabled boolean NOT NULL DEFAULT false,
    ADD COLUMN three_d_enabled boolean NOT NULL DEFAULT false;
