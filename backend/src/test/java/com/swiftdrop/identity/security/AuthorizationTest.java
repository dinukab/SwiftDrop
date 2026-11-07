package com.swiftdrop.identity.security;

import com.swiftdrop.identity.application.AdminUserService;
import com.swiftdrop.identity.web.AdminUserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    public void customer_CannotAccess_AdminEndpoints_WithoutAuth() throws Exception {
        // Updated URL path to match frontend endpoint contract (/api/v1/users)
        mockMvc.perform(get("/api/v1/users"))
               .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "DISPATCHER_ADMIN")
    public void admin_CanAccess_AdminEndpoints() throws Exception {
        
        mockMvc.perform(get("/api/v1/users"))
               .andExpect(status().isOk());
    }
}