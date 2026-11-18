-- Add hub fields to courier profiles for hub-based delivery workflow. Couriers are assigned to a
-- home hub where they operate from, and track their current hub location (for inter-hub transport).

ALTER TABLE couriers ADD COLUMN home_hub_id UUID;
ALTER TABLE couriers ADD COLUMN current_hub_id UUID;

-- Foreign keys to hubs table
ALTER TABLE couriers ADD CONSTRAINT fk_couriers_home_hub FOREIGN KEY (home_hub_id) REFERENCES hubs (id);
ALTER TABLE couriers ADD CONSTRAINT fk_couriers_current_hub FOREIGN KEY (current_hub_id) REFERENCES hubs (id);

-- Index for queries filtering by home hub
CREATE INDEX ix_couriers_home_hub ON couriers (home_hub_id);
