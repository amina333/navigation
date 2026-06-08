package com.air.navigation.services;

import com.air.navigation.dto.request.CreateUserRequestDto;
import com.air.navigation.dto.response.UserResponseDto;

import java.util.List;

public interface UserService {
    List<UserResponseDto> getAllUsers();
    UserResponseDto getUserById(Long id);
    UserResponseDto createUser(CreateUserRequestDto dto);
    UserResponseDto updateUser(Long id, CreateUserRequestDto dto);
    void deleteUser(Long id);
    UserResponseDto toggleUserStatus(Long id);
}
