package com.air.navigation.services.impl;

import com.air.navigation.dto.request.CreateUserRequestDto;
import com.air.navigation.dto.response.UserResponseDto;
import com.air.navigation.entities.Role;
import com.air.navigation.entities.User;
import com.air.navigation.enums.ERole;
import com.air.navigation.repository.RoleRepository;
import com.air.navigation.repository.UserRepository;
import com.air.navigation.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        return toResponseDto(findUserOrThrow(id));
    }

    @Override
    @Transactional
    public UserResponseDto createUser(CreateUserRequestDto dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Un compte existe déjà avec cet email : " + dto.getEmail());
        }
        Set<Role> roles = resolveRoles(dto.getRoles());
        User user = User.builder()
                .firstName(dto.getFirstName())
                .lastName(dto.getLastName())
                .email(dto.getEmail())
                .password(passwordEncoder.encode(dto.getPassword()))
                .phoneNumber(dto.getPhoneNumber())
                .badgeNumber(dto.getBadgeNumber())
                .active(true)
                .roles(roles)
                .build();
        User saved = userRepository.save(user);
        log.info("User created: {} ({})", saved.getFullName(), saved.getEmail());
        return toResponseDto(saved);
    }

    @Override
    @Transactional
    public UserResponseDto updateUser(Long id, CreateUserRequestDto dto) {
        User user = findUserOrThrow(id);
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhoneNumber(dto.getPhoneNumber());
        user.setBadgeNumber(dto.getBadgeNumber());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getRoles() != null && !dto.getRoles().isEmpty()) {
            user.setRoles(resolveRoles(dto.getRoles()));
        }
        return toResponseDto(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new RuntimeException("Utilisateur introuvable avec l'ID : " + id);
        }
        userRepository.deleteById(id);
        log.info("User deleted with ID: {}", id);
    }

    @Override
    @Transactional
    public UserResponseDto toggleUserStatus(Long id) {
        User user = findUserOrThrow(id);
        user.setActive(!user.isActive());
        return toResponseDto(userRepository.save(user));
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable avec l'ID : " + id));
    }

    private Set<Role> resolveRoles(Set<ERole> roleEnums) {
        Set<Role> roles = new HashSet<>();
        if (roleEnums == null || roleEnums.isEmpty()) {
            Role defaultRole = roleRepository.findByName(ERole.ATC)
                    .orElseGet(() -> roleRepository.save(new Role(ERole.ATC)));
            roles.add(defaultRole);
        } else {
            roleEnums.forEach(eRole -> {
                Role role = roleRepository.findByName(eRole)
                        .orElseGet(() -> roleRepository.save(new Role(eRole)));
                roles.add(role);
            });
        }
        return roles;
    }

    public UserResponseDto toResponseDto(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(r -> r.getName().name())
                .collect(Collectors.toList());
        return UserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .badgeNumber(user.getBadgeNumber())
                .active(user.isActive())
                .roles(roleNames)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
