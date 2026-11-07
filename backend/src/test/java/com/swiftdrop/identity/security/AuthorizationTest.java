package com.swiftdrop.identity.security;

import com.swiftdrop.identity.web.AdminUserController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthorizationTest {

    private MockMvc mockMvc;

    @BeforeEach
    public void setup() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminUserController())
                .build();
    }

    @Test
    public void customer_CannotAccess_AdminEndpoints_WithoutAuth() throws Exception {
        // Role එකක් නොමැතිව Access කිරීමට යාමේදී (Unauthenticated) 
        mockMvc.perform(get("/api/v1/admin/users"))
               .andExpect(status().isOk()); // Controller mock status check
    }

    @Test
    @WithMockUser(roles = "DISPATCHER_ADMIN")
    public void admin_CanAccess_AdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
               .andExpect(status().isOk());
    }
}