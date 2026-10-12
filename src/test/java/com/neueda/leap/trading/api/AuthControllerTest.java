package com.neueda.leap.trading.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.api.dto.CreateUserRequest;
import com.neueda.leap.trading.api.dto.LoginRequest;
import com.neueda.leap.trading.api.dto.LogoutRequest;
import com.neueda.leap.trading.service.contract.AuthResult;
import com.neueda.leap.trading.service.contract.AuthService;
import com.neueda.leap.trading.service.contract.RegisterResult;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @Test
    void register_createsUserWithoutToken() throws Exception {
        when(authService.register("alice", "secret"))
            .thenReturn(new RegisterResult(77, "alice"));

        CreateUserRequest request = new CreateUserRequest("alice", "secret");

        mockMvc.perform(post("/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value("77"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void login_returnsJwtPayload() throws Exception {
        Instant expiresAt = Instant.parse("2030-01-01T00:00:00Z");
        when(authService.login("alice", "secret"))
            .thenReturn(new AuthResult("jwt-token", expiresAt));

        LoginRequest request = new LoginRequest("alice", "secret");

        mockMvc.perform(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("jwt-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresAt").value("2030-01-01T00:00:00Z"));
    }

    @Test
    void logout_returnsNoContent() throws Exception {
        LogoutRequest request = new LogoutRequest("jwt-token");

        mockMvc.perform(post("/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isNoContent());

        verify(authService).logout("jwt-token");
    }
}
