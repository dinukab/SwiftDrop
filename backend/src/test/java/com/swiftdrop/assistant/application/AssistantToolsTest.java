package com.swiftdrop.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import com.swiftdrop.delivery.api.ParcelQueries;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.api.FeeQuote;
import com.swiftdrop.pricing.api.ParcelSize;
import com.swiftdrop.pricing.api.ZoneDirectory;
import com.swiftdrop.pricing.api.ZoneSummary;

class AssistantToolsTest {

    private final ZoneSummary western = new ZoneSummary(UUID.randomUUID(), "LK-WP", "Western Province");
    private final ZoneSummary southern = new ZoneSummary(UUID.randomUUID(), "LK-SP", "Southern Province");

    private FeeCalculator calculator;
    private ZoneDirectory zones;
    private AssistantTools tools;

    @BeforeEach
    void setUp() {
        calculator = mock(FeeCalculator.class);
        zones = mock(ZoneDirectory.class);
        when(zones.findActiveZones()).thenReturn(List.of(southern, western));
        tools = new AssistantTools(mock(ParcelQueries.class), calculator, zones);
    }

    private static ObjectNode quoteArgs(String pickup, String dropoff, String size, int weightGrams) {
        ObjectNode args = JsonNodeFactory.instance.objectNode();
        args.put("pickupZone", pickup);
        args.put("dropoffZone", dropoff);
        args.put("size", size);
        args.put("weightGrams", weightGrams);
        return args;
    }

    @Test
    void listsActiveZoneNamesSorted() {
        assertThat(tools.zoneNames()).containsExactly("Southern Province", "Western Province");
    }

    @Test
    void quotesAPriceResolvingZonesByExactNameAndBySubstring() {
        when(calculator.calculateFee(western.id(), southern.id(), ParcelSize.MEDIUM, 1500))
                .thenReturn(new FeeQuote(new BigDecimal("650.00"), "LKR", new BigDecimal("500.00"),
                        new BigDecimal("100.00"), new BigDecimal("50.00"), UUID.randomUUID()));

        Map<String, Object> result = tools.execute("get_price_quote",
                quoteArgs("western province", "southern", "medium", 1500));

        assertThat(result)
                .containsEntry("amount", "650.00")
                .containsEntry("currency", "LKR")
                .containsEntry("pickupZone", "Western Province")
                .containsEntry("dropoffZone", "Southern Province")
                .containsEntry("size", "MEDIUM");
    }

    @Test
    void anUnknownZoneListsTheAvailableOnesAndNeverQuotes() {
        Map<String, Object> result = tools.execute("get_price_quote",
                quoteArgs("Atlantis", "Western Province", "SMALL", 500));

        assertThat(result).containsKey("error");
        assertThat(result.get("availableZones")).isEqualTo(List.of("Southern Province", "Western Province"));
        verify(calculator, never()).calculateFee(any(), any(), any(), anyInt());
    }

    @Test
    void rejectsAWeightOutsideTheAllowedRange() {
        assertThat(tools.execute("get_price_quote", quoteArgs("Western", "Southern", "SMALL", 0)))
                .containsKey("error");
        assertThat(tools.execute("get_price_quote", quoteArgs("Western", "Southern", "SMALL", 30_001)))
                .containsKey("error");
        verify(calculator, never()).calculateFee(any(), any(), eq(ParcelSize.SMALL), anyInt());
    }

    @Test
    void rejectsAnUnknownSizeAndAnUnknownTool() {
        assertThat(tools.execute("get_price_quote", quoteArgs("Western", "Southern", "HUGE", 500)))
                .containsKey("error");
        JsonNode none = JsonNodeFactory.instance.objectNode();
        assertThat(tools.execute("delete_everything", none)).containsEntry("error", "Unknown tool: delete_everything");
    }
}
