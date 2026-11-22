package com.swiftdrop.dispatch.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.swiftdrop.delivery.api.LegPlan;
import com.swiftdrop.delivery.api.LegType;

class RoutePlannerTest {

    private static final UUID COLOMBO = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID UVA = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID SOUTHERN = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    @Test
    void sameProvinceIsPickupThenFinalMileAtTheSameHub() {
        List<LegPlan> plan = RoutePlanner.plan(UVA, UVA, COLOMBO);

        assertThat(plan).containsExactly(
                new LegPlan(LegType.PICKUP, UVA, UVA),
                new LegPlan(LegType.FINAL_MILE, UVA, UVA));
    }

    @Test
    void sameProvinceInColomboNeedsNoHubTransport() {
        assertThat(RoutePlanner.plan(COLOMBO, COLOMBO, COLOMBO)).extracting(LegPlan::legType)
                .containsExactly(LegType.PICKUP, LegType.FINAL_MILE);
    }

    @Test
    void crossProvinceGoesThroughTheMainHub() {
        List<LegPlan> plan = RoutePlanner.plan(UVA, SOUTHERN, COLOMBO);

        assertThat(plan).containsExactly(
                new LegPlan(LegType.PICKUP, UVA, UVA),
                new LegPlan(LegType.INTER_HUB, UVA, COLOMBO),
                new LegPlan(LegType.INTER_HUB, COLOMBO, SOUTHERN),
                new LegPlan(LegType.FINAL_MILE, SOUTHERN, SOUTHERN));
    }

    @Test
    void fromTheMainHubOnlyOneHubToHubLegIsNeeded() {
        assertThat(RoutePlanner.plan(COLOMBO, SOUTHERN, COLOMBO)).containsExactly(
                new LegPlan(LegType.PICKUP, COLOMBO, COLOMBO),
                new LegPlan(LegType.INTER_HUB, COLOMBO, SOUTHERN),
                new LegPlan(LegType.FINAL_MILE, SOUTHERN, SOUTHERN));
    }

    @Test
    void toTheMainHubOnlyOneHubToHubLegIsNeeded() {
        assertThat(RoutePlanner.plan(UVA, COLOMBO, COLOMBO)).containsExactly(
                new LegPlan(LegType.PICKUP, UVA, UVA),
                new LegPlan(LegType.INTER_HUB, UVA, COLOMBO),
                new LegPlan(LegType.FINAL_MILE, COLOMBO, COLOMBO));
    }

    @Test
    void everyRouteStartsWithPickupAndEndsWithFinalMileAndLegsChainFromHubToHub() {
        for (UUID origin : List.of(COLOMBO, UVA, SOUTHERN)) {
            for (UUID destination : List.of(COLOMBO, UVA, SOUTHERN)) {
                List<LegPlan> plan = RoutePlanner.plan(origin, destination, COLOMBO);

                assertThat(plan.get(0).legType()).isEqualTo(LegType.PICKUP);
                assertThat(plan.get(plan.size() - 1).legType()).isEqualTo(LegType.FINAL_MILE);
                assertThat(plan.get(0).destinationHubId()).isEqualTo(origin);
                assertThat(plan.get(plan.size() - 1).sourceHubId()).isEqualTo(destination);
                for (int i = 1; i < plan.size(); i++) {
                    assertThat(plan.get(i).sourceHubId()).as("leg %d starts where leg %d ended", i + 1, i)
                            .isEqualTo(plan.get(i - 1).destinationHubId());
                }
            }
        }
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> RoutePlanner.plan(null, UVA, COLOMBO)).isInstanceOf(NullPointerException.class);
    }
}
