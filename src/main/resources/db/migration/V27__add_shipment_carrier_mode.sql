ALTER TABLE shipments ADD COLUMN carrier_mode VARCHAR(30) NOT NULL DEFAULT 'MANUAL';
UPDATE shipments SET carrier_mode = 'SIMULATED' WHERE LEFT(tracking_code, 8) = 'GHN_HAN_';
