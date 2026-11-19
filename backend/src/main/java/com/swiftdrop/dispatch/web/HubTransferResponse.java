package com.swiftdrop.dispatch.web;

import java.time.Instant;
import java.util.UUID;

import com.swiftdrop.dispatch.application.DispatchService.TransferEntry;

/**
 * A parcel ready to be sent from the dispatcher's hub, or on its way to it. Hub codes are province codes such as
 * LK-UP. {@code transferredAt} is null while the parcel waits to be sent.
 */
record HubTransferResponse(
        UUID legId,
        UUID parcelId,
        String trackingNumber,
        int sequence,
        String sourceHubCode,
        String destinationHubCode,
        String size,
        int weightGrams,
        Instant transferredAt) {

    static HubTransferResponse from(TransferEntry entry) {
        return new HubTransferResponse(entry.legId(), entry.parcelId(), entry.trackingNumber(), entry.sequence(),
                entry.sourceHubCode(), entry.destinationHubCode(), entry.size(), entry.weightGrams(),
                entry.transferredAt());
    }
}
