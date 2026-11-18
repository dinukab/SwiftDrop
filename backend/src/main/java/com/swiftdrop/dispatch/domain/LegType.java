package com.swiftdrop.dispatch.domain;

/**
 * The part of a parcel's journey an assignment covers. Today a parcel has one courier, assigned before
 * pickup, so every assignment is {@link #PICKUP}; the other legs are for the hub workflow.
 */
public enum LegType {
    /** From the sender to the origin hub. */
    PICKUP,
    /** Between hubs (origin to main hub, main hub to destination hub). */
    INTER_HUB,
    /** From the destination hub to the recipient. */
    FINAL_MILE
}
