package com.air.navigation.controllers;

import com.air.navigation.dto.request.ChatMessageRequestDto;
import com.air.navigation.dto.response.ChatMessageResponseDto;
import com.air.navigation.services.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class WebSocketChatController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessageRequestDto dto, Principal principal) {
        log.debug("WS message from {} → room {}", principal.getName(), dto.getRoomId());
        chatService.handleWebSocketMessage(dto, principal.getName());
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload ChatMessageRequestDto dto, Principal principal) {
        log.debug("User {} is typing in room {}", principal.getName(), dto.getRoomId());
    }
}
