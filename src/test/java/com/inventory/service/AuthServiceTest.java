package com.inventory.service;

import com.inventory.dto.AuthResponse;
import com.inventory.dto.RegisterRequest;
import com.inventory.entity.User;
import com.inventory.exception.UsernameAlreadyExistsException;
import com.inventory.repository.UserRepository;
import com.inventory.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock AuthenticationManager authenticationManager;
    @Mock UserDetailsService userDetailsService;
    @InjectMocks AuthService authService;

    @Test
    void register_newUser_returnsTokenAndUsername() {
        RegisterRequest request = new RegisterRequest("alice", "password123");
        UserDetails userDetails = mock(UserDetails.class);

        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(jwtService.generateToken(userDetails)).thenReturn("generated-jwt");

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("generated-jwt");
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
    }

    @Test
    void register_existingUsername_throwsUsernameAlreadyExistsException() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("alice", "password123")))
                .isInstanceOf(UsernameAlreadyExistsException.class)
                .hasMessageContaining("alice");

        verify(userRepository, never()).save(any());
    }
}
