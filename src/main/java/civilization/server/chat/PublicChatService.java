package civilization.server.chat;

import civilization.protocol.ChatMessagePush;
import civilization.protocol.MessageMetadata;
import civilization.server.lobby.LobbyPushGateway;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class PublicChatService implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    public static final int MAX_MESSAGE_CHARACTERS = 1_000;
    // PHASE 3 - Added in Phase 3
    public static final int MAX_HISTORY_MESSAGES = 100;
    // PHASE 3 - Added in Phase 3
    private final LobbyPushGateway gateway;
    // PHASE 3 - Added in Phase 3
    private final Clock clock;
    // PHASE 3 - Added in Phase 3
    private final ConcurrentHashMap<String, ArrayDeque<ChatMessagePush>> histories =
            new ConcurrentHashMap<>();
    // PHASE 3 - Added in Phase 3
    private final ExecutorService delivery = Executors.newSingleThreadExecutor(runnable -> {
        // PHASE 3 - Added in Phase 3
        Thread thread = new Thread(runnable, "phase3-public-chat-delivery");
        thread.setDaemon(true);
        return thread;
    });
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean closed = new AtomicBoolean();

    // PHASE 3 - Added in Phase 3
    public PublicChatService(LobbyPushGateway gateway, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<ChatMessagePush> publish(ClientSession sender, String scopeId,
                                                       String content,
                                                       List<SessionId> recipients) {
        Objects.requireNonNull(sender, "sender");
        String trustedScope = validateScope(scopeId);
        String trustedContent = validateContent(content);
        List<SessionId> trustedRecipients = List.copyOf(new LinkedHashSet<>(
                Objects.requireNonNull(recipients, "recipients")));
        if (trustedRecipients.isEmpty() || trustedRecipients.stream().anyMatch(Objects::isNull)) {
            throw new ChatException("CHAT_SCOPE_EMPTY", "Chat scope has no connected recipients");
        }
        if (closed.get()) {
            return CompletableFuture.failedFuture(new ChatException(
                    "CHAT_UNAVAILABLE", "Chat service is shutting down"));
        }
        // PHASE 3 - Added in Phase 3
        return CompletableFuture.supplyAsync(() -> {
            ChatMessagePush message = new ChatMessagePush(
                    MessageMetadata.createNow(), UUID.randomUUID().toString(), trustedScope,
                    sender.playerId().toString(), sender.username(), trustedContent,
                    Instant.now(clock));
            appendHistory(message);
            // PHASE 3 - Added in Phase 3
            for (SessionId recipient : trustedRecipients) {
                try {
                    gateway.push(recipient, message);
                } catch (RuntimeException ignored) {
                    // Connection lifecycle owns failed-recipient cleanup; other recipients continue.
                }
            }
            return message;
        }, delivery);
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<List<ChatMessagePush>> history(String scopeId) {
        String trustedScope = validateScope(scopeId);
        if (closed.get()) return CompletableFuture.completedFuture(List.of());
        // PHASE 3 - Added in Phase 3
        return CompletableFuture.supplyAsync(() -> {
            ArrayDeque<ChatMessagePush> history = histories.get(trustedScope);
            return history == null ? List.of() : List.copyOf(new ArrayList<>(history));
        }, delivery);
    }

    // PHASE 3 - Added in Phase 3
    private void appendHistory(ChatMessagePush message) {
        ArrayDeque<ChatMessagePush> history = histories.computeIfAbsent(
                message.scopeId(), ignored -> new ArrayDeque<>());
        history.addLast(message);
        // PHASE 3 - Added in Phase 3
        while (history.size() > MAX_HISTORY_MESSAGES) history.removeFirst();
    }

    // PHASE 3 - Added in Phase 3
    private static String validateScope(String scopeId) {
        if (scopeId == null || scopeId.isBlank() || scopeId.length() > 128) {
            throw new ChatException("INVALID_CHAT_SCOPE", "Chat scope is invalid");
        }
        return scopeId;
    }

    // PHASE 3 - Added in Phase 3
    private static String validateContent(String content) {
        if (content == null || content.isBlank()
                || content.length() > MAX_MESSAGE_CHARACTERS) {
            throw new ChatException("INVALID_CHAT_MESSAGE",
                    "Chat message must contain 1-1000 characters");
        }
        return content;
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        delivery.shutdown();
        try {
            if (!delivery.awaitTermination(2, TimeUnit.SECONDS)) delivery.shutdownNow();
        } catch (InterruptedException failure) {
            delivery.shutdownNow();
            Thread.currentThread().interrupt();
        }
        histories.clear();
    }
}
