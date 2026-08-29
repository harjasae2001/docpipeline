package com.docpipeline.auth;

import com.docpipeline.auth.dto.AuthResponse;
import com.docpipeline.auth.dto.RegisterRequest;
import com.docpipeline.user.User;
import com.docpipeline.user.UserRepository;
import com.docpipeline.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenProvider jwtTokenProvider;
    @InjectMocks AuthService authService;

    @Test
    void registerHashesPasswordAssignsUserRoleAndReturnsToken() {
        RegisterRequest request = new RegisterRequest("demo@example.com", "Password123!", "Demo User");
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokenProvider.generateToken(any(User.class))).thenReturn("signed-token");

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(savedUser.getValue().getRole()).isEqualTo(UserRole.USER);
        assertThat(response.token()).isEqualTo("signed-token");
        assertThat(response.email()).isEqualTo(request.email());
    }
}
