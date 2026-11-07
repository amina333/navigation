package com.air.navigation.dto.request;

/**
 * Expressions régulières partagées par les DTO (alignées avec
 * navigation-frontend/src/app/shared/validation/app-validators.ts).
 */
public final class ValidationPatterns {

    private ValidationPatterns() {}

    /** Lettres (accents inclus), espaces, tiret, apostrophe */
    public static final String PERSON_NAME = "^[\\p{L}][\\p{L} '\\-]*$";

    public static final String EMAIL = "^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$";

    /** 8+ caractères, minuscule, majuscule, chiffre, caractère spécial, sans espace */
    public static final String STRONG_PASSWORD = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])\\S{8,100}$";

    public static final String PHONE_OR_EMPTY = "^$|^\\+?[0-9][0-9 ]{6,18}[0-9]$";

    public static final String CODE_OR_EMPTY = "^$|^[A-Za-z0-9][A-Za-z0-9\\-_/]*$";

    /** Texte libre sans balises HTML */
    public static final String SAFE_TEXT = "^[^<>]*$";

    /** Numéro de vol : TU712, BJ210A, AF 1234 */
    public static final String FLIGHT_NUMBER = "^[A-Z0-9]{2,3}\\s?\\d{1,4}[A-Z]?$";

    public static final String AIRPORT_CODE_OR_EMPTY = "^$|^[A-Z]{3,4}$";

    public static final String PLACE_OR_EMPTY = "^$|^[\\p{L}0-9][\\p{L}0-9 '().\\-/]*$";

    public static final String REGISTRATION_OR_EMPTY = "^$|^[A-Z0-9]{1,2}-?[A-Z0-9]{1,5}$";

    public static final String RUNWAY_OR_EMPTY = "^$|^(0[1-9]|[12][0-9]|3[0-6])[LCR]?(/(0[1-9]|[12][0-9]|3[0-6])[LCR]?)?$";

    public static final String GATE_OR_EMPTY = "^$|^[A-Z]{0,2}\\d{1,3}[A-Z]?$";
}
