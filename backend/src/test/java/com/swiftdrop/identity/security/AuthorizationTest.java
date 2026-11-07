package com.swiftdrop.identity.security;

import com.swiftdrop.identity.api.MerchantApprovalRequest;
import com.swiftdrop.identity.application.AdminUserService;
import com.swiftdrop.identity.web.AdminUserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthorizationTest {

    private MockMvc mockMvc;
    private AdminUserService adminUserService;

    @BeforeEach
    public void setup() {
        adminUserService = Mockito.mock(AdminUserService.class);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminUserController(adminUserService))
                .build();
    }

    @Test
    @WithMockUser(roles = "DISPATCHER_ADMIN")
    public void admin_CanAccess_MerchantApprovalEndpoint() throws Exception {
        UUID merchantId = UUID.randomUUID();
        String jsonPayload = "{\"approved\": true}";

        mockMvc.perform(patch("/api/v1/admin/merchants/" + merchantId + "/approve")
               .contentType(MediaType.APPLICATION_JSON)
               .content(jsonPayload))
               .andExpect(status().isNoContent());
    }
}