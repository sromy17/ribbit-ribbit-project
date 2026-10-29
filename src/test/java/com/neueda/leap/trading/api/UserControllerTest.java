package com.neueda.leap.trading.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import tools.jackson.databind.ObjectMapper;
import com.neueda.leap.trading.api.dto.CreateUserRequest;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.UserRepository;
import com.neueda.leap.trading.repository.jpa.AccessTokenRepository;
import com.neueda.leap.trading.security.AccountAccessService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AccessTokenRepository accessTokenRepository;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private AccountAccessService accountAccessService;

    @Test
    // Mockito matchers return null placeholders; the repository is mocked.
    @SuppressWarnings("null")
    void createUser_returnsCreatedUser() throws Exception {
        User saved = new User();
        saved.setUserId(77);
        saved.setUsername("alice");
        saved.setFirstName("Alice");
        saved.setLastName("Example");
        saved.setSalt("contract-salt");
        saved.setUserHashedSaltedPassword("secret");

        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");
        when(userRepository.save(any(User.class))).thenReturn(saved);

        CreateUserRequest request = new CreateUserRequest(
            "alice",
            "secret",
            "Alice",
            "Example"
        );

        mockMvc.perform(post("/users")
                .contentType("application/json")
                .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.userId").value(77))
            .andExpect(jsonPath("$.username").value("alice"));
    }
}
