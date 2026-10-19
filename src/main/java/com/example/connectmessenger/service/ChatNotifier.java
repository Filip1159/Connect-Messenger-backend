package com.example.connectmessenger.service;

import com.example.connectmessenger.model.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/** Tells the members of a chat that the chat itself changed (created, renamed, members changed). */
@Component
public class ChatNotifier {
    /** Sent to a user who is no longer in the chat. */
    public record Removed(Long chatId) {
    }

    private final ChatService chatService;
    private final SimpMessagingTemplate template;
    private final SimpUserRegistry userRegistry;
    private final MessageChannel clientInboundChannel;

    public ChatNotifier(ChatService chatService, SimpMessagingTemplate template, SimpUserRegistry userRegistry,
                        @Lazy @Qualifier("clientInboundChannel") MessageChannel clientInboundChannel) {
        this.chatService = chatService;
        this.template = template;
        this.userRegistry = userRegistry;
        this.clientInboundChannel = clientInboundChannel;
    }

    /** Every current member receives the chat with their own unread count and settings. */
    public void chatChanged(Long chatId) {
        chatService.getChatForEachMember(chatId)
                .forEach((userId, chat) -> template.convertAndSend("/topic/new-chats/" + userId, chat));
    }

    /**
     * The user is no longer in the chat: tell them, and stop what they already follow. A subscription is only
     * checked when it is made, so without this the user would keep receiving the chat's messages.
     */
    public void removed(Long chatId, User user) {
        template.convertAndSend("/topic/removed-chats/" + user.getId(), new Removed(chatId));
        SimpUser connected = userRegistry.getUser(user.getUsername());
        if (connected == null) {
            return;
        }
        for (SimpSession session : connected.getSessions()) {
            for (SimpSubscription subscription : session.getSubscriptions()) {
                if (isChatTopic(subscription.getDestination(), chatId)) {
                    var headers = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
                    headers.setSessionId(session.getId());
                    headers.setSubscriptionId(subscription.getId());
                    clientInboundChannel.send(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
                }
            }
        }
    }

    private static boolean isChatTopic(String destination, Long chatId) {
        return destination != null && (destination.equals("/topic/messages/" + chatId)
                || destination.equals("/topic/status/" + chatId) || destination.equals("/topic/typing/" + chatId));
    }
}
