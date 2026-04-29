package com.flashmind.service;

import com.flashmind.dto.request.LoginRequest;
import com.flashmind.dto.request.RefreshTokenRequest;
import com.flashmind.dto.request.RegisterRequest;
import com.flashmind.dto.response.AuthResponse;
import com.flashmind.dto.response.UserResponse;
import com.flashmind.entity.User;
import com.flashmind.exception.BusinessException;
import com.flashmind.repository.UserRepository;
import com.flashmind.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("Email đã tồn tại");
        }

        User user = User.builder()
            .email(req.getEmail())
            .password(passwordEncoder.encode(req.getPassword()))
            .fullName(req.getFullName())
            .build();
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
            .orElseThrow(() -> new BusinessException("Email hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BusinessException("Email hoặc mật khẩu không đúng");
        }

        return buildAuthResponse(user);
    }

    public AuthResponse refresh(RefreshTokenRequest req) {
        String token = req.getRefreshToken();
        if (!jwtUtil.validateToken(token) || !"refresh".equals(jwtUtil.extractType(token))) {
            throw new BusinessException("Refresh token không hợp lệ");
        }

        Long userId = jwtUtil.extractUserId(token);
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("Người dùng không tồn tại"));

        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getEmail());
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .user(UserResponse.from(user))
            .build();
    }
}
