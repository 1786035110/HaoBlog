ALTER TABLE comment ALTER COLUMN status SET DEFAULT 'APPROVED';

ALTER TABLE site_setting ADD COLUMN music_manifest_url varchar(2048);
