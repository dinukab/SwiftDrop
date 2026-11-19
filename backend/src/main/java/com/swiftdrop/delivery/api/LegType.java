package com.swiftdrop.delivery.api;

/** The part of a parcel's journey a shipment leg covers (ADR 0007). */
public enum LegType {
    /** From the sender to the origin hub; source and destination hub are the origin hub. */
    PICKUP,
    /** Between hubs: origin to Main Hub, or Main Hub to destination. */
    INTER_HUB,
    /** From the destination hub to the recipient; source and destination hub are the destination hub. */
    FINAL_MILE
}
