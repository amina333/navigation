package com.air.navigation.controllers;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.request.ChatTypingDto;
import com.air.navigation.services.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {

    private final ChatService chatService;

    /** Client → /app/chat.send */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessageRequestDto dto, Principal principal) {
        if (principal == null) throw new IllegalArgumentException("Session WebSocket non authentifiée");
        chatService.handleWebSocketMessage(dto, principal.getName());
    }

    /** Client → /app/chat.typing */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload ChatTypingDto dto, Principal principal) {
        if (principal == null || dto == null) return;
        chatService.broadcastTyping(dto.getRoomId(), principal.getName(), dto.isTyping());
    }

    /** Les erreurs (validation, accès refusé) sont renvoyées à l'émetteur sur /user/queue/errors */
    @MessageExceptionHandler
    @SendToUser("/queue/errors")
    public Map<String, String> handleError(Exception ex) {
        log.warn("Erreur WebSocket chat : {}", ex.getMessage());
        return Map.of("error", ex.getMessage() != null ? ex.getMessage() : "Erreur inconnue");
    }
}
