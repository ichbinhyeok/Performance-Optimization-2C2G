package com.example.sns_project.service;

import com.example.sns_project.dto.*;
import com.example.sns_project.jwt.JwtUtil;
import com.example.sns_project.model.Role;
import com.example.sns_project.model.User;
import com.example.sns_project.repository.RoleRepository;
import com.example.sns_project.repository.UserRepository;
import com.github.javafaker.Faker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class AuthService {
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = new BCryptPasswordEncoder(8);  // 강도를 n으로 설정
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse login(LoginRequest loginRequest) {
        long startTime = System.currentTimeMillis();

        // 1. 로그인 정보 조회
        LoginUserDTO userDTO = userRepository.findUserForLogin(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        long afterDbTime = System.currentTimeMillis();
        log.info("🔍 DB 조회 시간: {} ms", (afterDbTime - startTime));

        // 2. 비밀번호 검증
        if (passwordEncoder.matches(loginRequest.getPassword(), userDTO.getPassword())) {
            // 비밀번호 강도 업데이트가 필요한 경우
            if (needsUpgrade(userDTO.getPassword())) {
                log.info("🔄 사용자 {} 의 비밀번호 강도를 업데이트합니다.", userDTO.getUsername());
                String newHash = passwordEncoder.encode(loginRequest.getPassword());

                // 네이티브 쿼리로 직접 업데이트
                userRepository.updatePassword(userDTO.getId(), newHash);
            }

            long afterPasswordTime = System.currentTimeMillis();
            log.info("🔍 비밀번호 검증 시간: {} ms", (afterPasswordTime - afterDbTime));

            // 3. 토큰 생성
            String token = jwtUtil.generateToken(userDTO.getId());

            long afterTokenTime = System.currentTimeMillis();
            log.info("🔍 JWT 생성 시간: {} ms", (afterTokenTime - afterPasswordTime));

            return new AuthResponse(token, "로그인 성공");
        }

        throw new RuntimeException("Invalid data");
    }

    public UserDTO register(UserRegistrationDTO userRegistrationDTO) {
        User user = new User();
        user.setUsername(userRegistrationDTO.getUsername());
        user.setEmail(userRegistrationDTO.getEmail());

        // 비밀번호를 강도 n으로 암호화하여 저장
        user.setPassword(passwordEncoder.encode(userRegistrationDTO.getPassword()));

        // 기본 역할 설정 (예: ROLE_USER)
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("기본 역할이 존재하지 않습니다."));

        // List<Role>으로 설정
        user.setRoles(new ArrayList<>(List.of(userRole)));

        // 사용자 저장
        userRepository.save(user);

        // UserDTO에 ID 추가하여 반환
        return new UserDTO(user.getId(), user.getUsername(), user.getEmail());
    }

    public List<String> generateAndRegisterUsers(int count) {
        Faker faker = new Faker();
        List<UserRegistrationDTO> userRegistrationDTOs = new ArrayList<>();
        List<String> registeredUsernames = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            UserRegistrationDTO userRegistrationDTO = new UserRegistrationDTO();
            userRegistrationDTO.setUsername(faker.name().username());
            userRegistrationDTO.setEmail(faker.internet().emailAddress());
            String password = "123";
            userRegistrationDTO.setPassword(password);
            userRegistrationDTOs.add(userRegistrationDTO);
        }

        for (UserRegistrationDTO userRegistrationDTO : userRegistrationDTOs) {
            UserDTO registeredUser = register(userRegistrationDTO);
            registeredUsernames.add(registeredUser.getUsername());
        }

        return registeredUsernames;
    }

    @Transactional
    public int checkPasswordStrengths() {
        List<User> users = userRepository.findAll();
        int needsUpgradeCount = 0;

        for (User user : users) {
            if (needsUpgrade(user.getPassword())) {
                needsUpgradeCount++;
                log.info("사용자 {} 의 비밀번호 강도 업그레이드 필요", user.getUsername());
            }
        }

        return needsUpgradeCount;
    }

    // 비밀번호 해시가 업그레이드가 필요한지 확인하는 메서드
    private boolean needsUpgrade(String hashedPassword) {
        if (hashedPassword == null || !hashedPassword.startsWith("$2a$")) {
            return true;
        }
        // BCrypt 해시에서 강도 추출 (형식: $2a$10$...)
        String workFactor = hashedPassword.split("\\$")[2];
        return Integer.parseInt(workFactor) > 8;
    }
}