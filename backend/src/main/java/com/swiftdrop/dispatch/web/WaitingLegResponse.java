package com.swiftdrop.dispatch.web;

import java.util.UUID;

import com.swiftdrop.dispatch.application.DispatchService.WaitingLegEntry;

/** A leg waiting for a courier at the dispatcher's hub. Hub codes are province codes such as LK-UP. */
record WaitingLegResponse(
        UUID legId,
        UUID parcelId,
        String trackingNumber,
        String legType,
        int sequence,
        String sourceHubCode,
        String destinationHubCode,
        String size,
        int weightGrams) {

    static WaitingLegResponse from(WaitingLegEntry entry) {
        return new WaitingLegResponse(entry.legId(), entry.parcelId(), entry.trackingNumber(), entry.legType(),
                entry.sequence(), entry.sourceHubCode(), entry.destinationHubCode(), entry.size(),
                entry.weightGrams());
    }
}
