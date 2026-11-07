package com.air.navigation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class JwtResponseDto {
    private String token;
    private String type = "Bearer";
    private Long id;
    private String email;
    private String fullName;
    private List<String> roles;

    /** true : mot de passe correct, un code de vérification a été envoyé par e-mail (2FA) */
    private boolean twoFactorRequired;

    public static JwtResponseDto twoFactorChallenge(String email) {
        JwtResponseDto d = new JwtResponseDto(null, null, email, null, null);
        d.twoFactorRequired = true;
        d.type = null;
        return d;
    }

    public JwtResponseDto(String token, Long id, String email, String fullName, List<String> roles) {
        this.token = token;
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.roles = roles;
    }
}
