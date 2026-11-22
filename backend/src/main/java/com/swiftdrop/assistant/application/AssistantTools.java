package com.swiftdrop.assistant.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.delivery.api.PublicTrackingView;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.domain.Zone;
import com.swiftdrop.pricing.domain.ZoneRepository;

/**
 * Executes the function calls Gemini may request. Each tool returns a plain map that is sent
 * back to the model as the function response, so the model can phrase the answer naturally.
 * Tool results never include personal data beyond what the public tracking view already exposes.
 */
@Component
class AssistantTools {

    private final ParcelQueries parcels;
    private final FeeCalculator feeCalculator;
    private final ZoneRepository zones;

    AssistantTools(ParcelQueries parcels, FeeCalculator feeCalculator, ZoneRepository zones) {
        this.parcels = parcels;
        this.feeCalculator = feeCalculator;
        this.zones = zones;
    }

    /** Active zone names for embedding in the price-quote tool description. */
    List<String> zoneNames() {
        return zones.findByActiveTrue().stream().map(Zone::getName).sorted().toList();
    }

    Map<String, Object> execute(String name, JsonNode args) {
        return switch (name) {
            case "get_parcel_status" -> parcelStatus(args.path("trackingNumber").asText(""));
            case "get_price_quote" -> priceQuote(args);
            default -> Map.of("error", "Unknown tool: " + name);
        };
    }

    private Map<String, Object> parcelStatus(String trackingNumber) {
        String number = trackingNumber == null ? "" : trackingNumber.trim();
        if (number.isEmpty()) {
            return Map.of("error", "trackingNumber is required");
        }
        Optional<PublicTrackingView> view = parcels.findPublicTrackingView(number);
        if (view.isEmpty()) {
            return Map.of("error", "No parcel found for tracking number " + number);
        }
        PublicTrackingView parcel = view.get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trackingNumber", parcel.trackingNumber());
        result.put("status", parcel.status().name());
        result.put("pickupCity", parcel.pickupCity());
        result.put("dropoffCity", parcel.dropoffCity());
        if (!parcel.history().isEmpty()) {
            var last = parcel.history().getLast();
            result.put("lastUpdate", last.occurredAt().toString());
            result.put("lastStatus", last.status().name());
        }
        return result;
    }

    private Map<String, Object> priceQuote(JsonNode args) {
        int weightGrams;
        try {
            weightGrams = args.path("weightGrams").asInt(-1);
        } catch (RuntimeException e) {
            weightGrams = -1;
        }
        if (weightGrams < 1 || weightGrams > 30_000) {
            return Map.of("error", "weightGrams must be between 1 and 30000");
        }

        ParcelSize size;
        try {
            size = ParcelSize.valueOf(args.path("size").asText("").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Map.of("error", "size must be one of SMALL, MEDIUM, LARGE");
        }

        Optional<Zone> pickup = resolveZone(args.path("pickupZone").asText(""));
        Optional<Zone> dropoff = resolveZone(args.path("dropoffZone").asText(""));
        if (pickup.isEmpty() || dropoff.isEmpty()) {
            return Map.of(
                    "error", "Could not resolve one or both zones",
                    "availableZones", zoneNames());
        }

        var quote = feeCalculator.calculateFee(pickup.get().getId(), dropoff.get().getId(), size, weightGrams);
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("baseFee", quote.baseFee().toPlainString());
        breakdown.put("weightFee", quote.weightFee().toPlainString());
        breakdown.put("sizeSurcharge", quote.sizeFee().toPlainString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("amount", quote.totalFee().toPlainString());
        result.put("currency", quote.currency());
        result.put("weightGrams", weightGrams);
        result.put("size", size.name());
        result.put("pickupZone", pickup.get().getName());
        result.put("dropoffZone", dropoff.get().getName());
        result.put("breakdown", breakdown);
        return result;
    }

    /** Exact name match first, then case-insensitive substring match against active zones. */
    private Optional<Zone> resolveZone(String name) {
        String query = Objects.toString(name, "").trim();
        if (query.isEmpty()) {
            return Optional.empty();
        }
        List<Zone> active = zones.findByActiveTrue();
        return active.stream()
                .filter(z -> z.getName().equalsIgnoreCase(query))
                .findFirst()
                .or(() -> active.stream()
                        .filter(z -> z.getName().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                        .findFirst());
    }
}
