package com.example.order.service.impl;

import com.example.order.config.JwtTokenProvider;
import com.example.order.dto.request.LoginRequest;
import com.example.order.dto.request.RegisterRequest;
import com.example.order.dto.response.AuthResponse;
import com.example.order.dto.response.UserResponse;
import com.example.order.model.Role;
import com.example.order.model.User;
import com.example.order.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuthServiceImpl}.
 * Covers user registration, login, duplicate username/email handling,
 * invalid roles, and authentication failures.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthServiceImpl authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encoded_password")
                .role(Role.ROLE_USER)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ========================
    // Registration Tests
    // ========================

    @Nested
    @DisplayName("User Registration")
    class RegistrationTests {

        @Test
        @DisplayName("Should register new user successfully with default USER role")
        void shouldRegisterUserSuccessfully() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("password123")
                    .build();

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encoded_password123");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(2L);
                user.setCreatedAt(LocalDateTime.now());
                return user;
            });

            // Act
            UserResponse response = authService.register(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(2L);
            assertThat(response.getUsername()).isEqualTo("newuser");
            assertThat(response.getEmail()).isEqualTo("new@example.com");
            assertThat(response.getRole()).isEqualTo("ROLE_USER");

            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Should register user with ADMIN role when specified")
        void shouldRegisterUserWithAdminRole() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .username("adminuser")
                    .email("admin@example.com")
                    .password("password123")
                    .role("ADMIN")
                    .build();

            when(userRepository.existsByUsername("adminuser")).thenReturn(false);
            when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
            when(passwordEncoder.encode("password123")).thenReturn("encoded_password123");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(3L);
                user.setCreatedAt(LocalDateTime.now());
                return user;
            });

            // Act
            UserResponse response = authService.register(request);

            // Assert
            assertThat(response.getRole()).isEqualTo("ROLE_ADMIN");
        }

        @Test
        @DisplayName("Should throw exception for duplicate username")
        void shouldThrowExceptionForDuplicateUsername() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .username("testuser")
                    .email("unique@example.com")
                    .password("password123")
                    .build();

            when(userRepository.existsByUsername("testuser")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Username is already taken");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception for duplicate email")
        void shouldThrowExceptionForDuplicateEmail() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .username("uniqueuser")
                    .email("test@example.com")
                    .password("password123")
                    .build();

            when(userRepository.existsByUsername("uniqueuser")).thenReturn(false);
            when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Email is already in use");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception for invalid role")
        void shouldThrowExceptionForInvalidRole() {
            // Arrange
            RegisterRequest request = RegisterRequest.builder()
                    .username("newuser")
                    .email("new@example.com")
                    .password("password123")
                    .role("SUPER_ADMIN")
                    .build();

            when(userRepository.existsByUsername("newuser")).thenReturn(false);
            when(userRepository.existsByEmail("new@example.com")).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid role");
        }
    }

    // ========================
    // Login Tests
    // ========================

    @Nested
    @DisplayName("User Login")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully and return JWT token")
        void shouldLoginSuccessfully() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .username("testuser")
                    .password("password123")
                    .build();

            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(testUser);
            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenReturn(authentication);
            when(tokenProvider.generateToken(authentication)).thenReturn("jwt-token-123");

            // Act
            AuthResponse response = authService.login(request);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo("jwt-token-123");
            assertThat(response.getUsername()).isEqualTo("testuser");
            assertThat(response.getRole()).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("Should throw BadCredentialsException for wrong credentials")
        void shouldThrowBadCredentialsForWrongPassword() {
            // Arrange
            LoginRequest request = LoginRequest.builder()
                    .username("testuser")
                    .password("wrong_password")
                    .build();

            when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            // Act & Assert
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }
}
