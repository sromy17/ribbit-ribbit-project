package com.neueda.leap.trading.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.UserRepository;

@WebMvcTest(UserController.class)
@SuppressWarnings("null")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserRepository userRepository;

    @Test
    void createUser_returnsCreatedUser() throws Exception {
        User saved = new User();
        saved.setUserId(77);
        saved.setUsername("alice");
        saved.setSalt("contract-salt");
        saved.setUserHashedSaltedPassword("secret");

        when(userRepository.save(any(User.class))).thenReturn(saved);

        CreateUserRequest request = new CreateUserRequest("alice", "secret");

        mockMvc.perform(post("/users")
                .with(jwt().jwt(jwt -> jwt
                    .claim("sub", "user-123")
                    .claim("aud", java.util.List.of("authenticated"))
                    .issuer("https://YOUR-PROJECT-REF.supabase.co/auth/v1")
                ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(77))
            .andExpect(jsonPath("$.username").value("alice"));
    }
}
