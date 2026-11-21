package com.swiftdrop.delivery.web;

import static com.swiftdrop.test.security.AuthorizationMatrixRow.row;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.swiftdrop.delivery.application.DeliveryReportService;
import com.swiftdrop.delivery.application.DeliveryReportService.DailyCount;
import com.swiftdrop.delivery.application.DeliveryReportService.DailyDeliveries;
import com.swiftdrop.delivery.application.InvalidReportRangeException;
import com.swiftdrop.delivery.domain.DeliveryProofRepository;
import com.swiftdrop.delivery.domain.OutboxEventRepository;
import com.swiftdrop.delivery.domain.ParcelRepository;
import com.swiftdrop.delivery.domain.ParcelStatusHistoryRepository;
import com.swiftdrop.dispatch.domain.CourierProfileRepository;
import com.swiftdrop.identity.domain.ExternalIdentityRepository;
import com.swiftdrop.identity.domain.GoogleLoginCodeRepository;
import com.swiftdrop.identity.domain.UserRepository;
import com.swiftdrop.merchant.domain.MerchantRepository;
import com.swiftdrop.pricing.api.FeeCalculator;
import com.swiftdrop.pricing.domain.PricingRuleRepository;
import com.swiftdrop.pricing.domain.ZoneRepository;
import com.swiftdrop.test.security.AuthorizationMatrixExecutor;
import com.swiftdrop.test.security.AuthorizationMatrixRow;
import com.swiftdrop.test.security.TestPrincipal;

/** The report endpoints over HTTP: admin only, query parsing, response shape and safe 400s. */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "swiftdrop.security.jwt.secret=test-only-jwt-secret-not-used-outside-tests-0123456789",
        "swiftdrop.security.google.client-id="})
@AutoConfigureMockMvc
class ReportApiTest {

    private static final String DAILY = "/api/v1/reports/daily-deliveries";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DeliveryReportService reports;

    @MockitoBean
    private UserRepository users;
    @MockitoBean
    private ExternalIdentityRepository identities;
    @MockitoBean
    private GoogleLoginCodeRepository googleCodes;
    @MockitoBean
    private ParcelRepository parcels;
    @MockitoBean
    private ParcelStatusHistoryRepository parcelHistory;
    @MockitoBean
    private DeliveryProofRepository deliveryProofs;
    @MockitoBean
    private OutboxEventRepository outboxEvents;
    @MockitoBean
    private FeeCalculator feeCalculator;
    @MockitoBean
    private ZoneRepository zones;
    @MockitoBean
    private PricingRuleRepository pricingRules;
    @MockitoBean
    private CourierProfileRepository courierProfiles;
    @MockitoBean
    private MerchantRepository merchants;

    @Test
    void adminGetsEveryDayWithIsoDatesAndTheTotal() throws Exception {
        LocalDate from = LocalDate.parse("2026-11-01");
        LocalDate to = LocalDate.parse("2026-11-02");
        when(reports.dailyDeliveries(from, to)).thenReturn(new DailyDeliveries(from, to,
                ZoneId.of("Asia/Colombo"), 4, List.of(new DailyCount(from, 4), new DailyCount(to, 0))));

        mvc.perform(get(DAILY).param("from", "2026-11-01").param("to", "2026-11-02")
                        .with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-11-01"))
                .andExpect(jsonPath("$.to").value("2026-11-02"))
                .andExpect(jsonPath("$.timeZone").value("Asia/Colombo"))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.days.length()").value(2))
                .andExpect(jsonPath("$.days[0].date").value("2026-11-01"))
                .andExpect(jsonPath("$.days[0].count").value(4))
                .andExpect(jsonPath("$.days[1].count").value(0));
    }

    @Test
    void datesAreOptional() throws Exception {
        LocalDate day = LocalDate.parse("2026-11-21");
        when(reports.dailyDeliveries(null, null)).thenReturn(new DailyDeliveries(day, day,
                ZoneId.of("Asia/Colombo"), 0, List.of(new DailyCount(day, 0))));

        mvc.perform(get(DAILY).with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(1));
    }

    @Test
    void anInvalidRangeAnswers400WithTheRuleOnly() throws Exception {
        when(reports.dailyDeliveries(any(), any()))
                .thenThrow(new InvalidReportRangeException("'from' must not be after 'to'"));

        mvc.perform(get(DAILY).param("from", "2026-11-05").param("to", "2026-11-01")
                        .with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must not be after 'to'"));
    }

    @Test
    void aMalformedDateAnswers400NamingTheField() throws Exception {
        mvc.perform(get(DAILY).param("from", "01/11/2026").with(TestPrincipal.admin().postProcessor()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid request"))
                .andExpect(jsonPath("$.invalidFields[0]").value("from"));
        verify(reports, never()).dailyDeliveries(any(), any());
    }

    /** TST-2: only admins may read reports. */
    @TestFactory
    Stream<DynamicTest> reportAuthorizationMatrix() {
        LocalDate day = LocalDate.parse("2026-11-21");
        when(reports.dailyDeliveries(any(), any())).thenReturn(new DailyDeliveries(day, day,
                ZoneId.of("Asia/Colombo"), 0, List.of(new DailyCount(day, 0))));
        return AuthorizationMatrixExecutor.toDynamicTests(mvc, matrixRows(DAILY));
    }

    private static List<AuthorizationMatrixRow> matrixRows(String path) {
        return List.of(
                row("GET " + path + ": anonymous → 401", HttpMethod.GET, path,
                        TestPrincipal.anonymous(), HttpStatus.UNAUTHORIZED, null),
                row("GET " + path + ": CUSTOMER → 403", HttpMethod.GET, path,
                        TestPrincipal.customer(), HttpStatus.FORBIDDEN, null),
                row("GET " + path + ": MERCHANT → 403", HttpMethod.GET, path,
                        TestPrincipal.merchant(), HttpStatus.FORBIDDEN, null),
                row("GET " + path + ": COURIER → 403", HttpMethod.GET, path,
                        TestPrincipal.courier(), HttpStatus.FORBIDDEN, null),
                row("GET " + path + ": DISPATCHER → 403", HttpMethod.GET, path,
                        TestPrincipal.dispatcher(), HttpStatus.FORBIDDEN, null),
                row("GET " + path + ": ADMIN → 200", HttpMethod.GET, path,
                        TestPrincipal.admin(), HttpStatus.OK, null));
    }
}
