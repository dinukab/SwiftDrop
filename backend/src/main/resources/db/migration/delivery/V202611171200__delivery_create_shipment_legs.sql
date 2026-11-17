-- Shipment legs for hub-based multi-leg delivery workflow. Each leg represents one segment of a
-- parcel's journey: pickup to hub, inter-hub transport, or final delivery. Legs are created when a
-- parcel is booked and couriers are assigned to legs one at a time by the dispatcher.

CREATE TABLE shipment_legs (
    id                     UUID         PRIMARY KEY,
    parcel_id              UUID         NOT NULL,
    sequence               INT          NOT NULL,
    leg_type               VARCHAR(20)  NOT NULL,
    source_hub_id          UUID         NOT NULL,
    destination_hub_id     UUID         NOT NULL,
    assigned_courier_id    UUID,
    created_at             TIMESTAMPTZ  NOT NULL,
    assigned_at            TIMESTAMPTZ,

    CONSTRAINT fk_legs_parcel FOREIGN KEY (parcel_id) REFERENCES parcels (id),
    CONSTRAINT fk_legs_source_hub FOREIGN KEY (source_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_legs_dest_hub FOREIGN KEY (destination_hub_id) REFERENCES hubs (id),
    CONSTRAINT fk_legs_courier FOREIGN KEY (assigned_courier_id) REFERENCES couriers (id)
);

-- Index for queries finding legs by parcel
CREATE INDEX ix_legs_parcel ON shipment_legs (parcel_id, sequence);

-- Index for queries finding legs assigned to a courier
CREATE INDEX ix_legs_courier ON shipment_legs (assigned_courier_id);
