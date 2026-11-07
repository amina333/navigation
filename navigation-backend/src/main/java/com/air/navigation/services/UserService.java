package com.air.navigation.services;

import com.air.navigation.dto.request.CreateUserRequestDto;
import com.air.navigation.dto.response.UserResponseDto;

import java.util.List;

public interface UserService {
    List<UserResponseDto> getAllUsers();
    UserResponseDto getUserById(Long id);
    UserResponseDto createUser(CreateUserRequestDto dto);
    UserResponseDto updateUser(Long id, com.air.navigation.dto.request.UpdateUserRequestDto dto, boolean asAdmin);
    void deleteUser(Long id);
    java.util.Map<String, Object> resendCredentials(Long id);
    UserResponseDto toggleUserStatus(Long id);
}
