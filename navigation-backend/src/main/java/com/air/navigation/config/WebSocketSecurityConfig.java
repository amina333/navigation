package com.air.navigation.config;

import com.air.navigation.security.jwt.JwtUtils;
import com.air.navigation.services.ChatService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class WebSocketSecurityConfig implements WebSocketMessageBrokerConfigurer {

    /** /topic/chat/{roomId} et ses sous-canaux (/typing, /read) */
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/(\\d+)(/.*)?$");

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserDetailsService userDetailsService;

    /** Récupéré à la demande pour éviter une dépendance circulaire avec le broker */
    @Autowired
    private ObjectProvider<ChatService> chatServiceProvider;

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader("Authorization");

                    if (StringUtils.hasText(token) && token.startsWith("Bearer ")) {
                        token = token.substring(7);

                        if (jwtUtils.validateJwtToken(token)) {
                            String username = jwtUtils.getUserNameFromJwtToken(token);
                            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                            accessor.setUser(authentication);
                        }
                    }
                }

                // Contrôle d'accès : un utilisateur ne peut écouter que les salons auxquels il a droit
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null) {
                        Matcher m = CHAT_TOPIC.matcher(destination);
                        if (m.matches()) {
                            Principal user = accessor.getUser();
                            Long roomId = Long.valueOf(m.group(1));
                            if (user == null || !chatServiceProvider.getObject().canAccessRoom(roomId, user.getName())) {
                                throw new AccessDeniedException("Accès refusé au salon " + roomId);
                            }
                        }
                    }
                }

                return message;
            }
        });
    }
}
