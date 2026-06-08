package com.air.navigation.enums;

public enum RoomType {
    /** Salon ouvert à tous */
    PUBLIC,
    /** Conversation privée entre deux utilisateurs (DM) */
    PRIVATE_DM,
    /** Salon officiel rattaché à un service (ATC, PISTE, AIS…) */
    SERVICE_CHANNEL,
    /** Groupe créé manuellement */
    GROUP
}
