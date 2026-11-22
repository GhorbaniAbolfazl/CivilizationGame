package civilization.server.dispatch;

import civilization.protocol.ErrorResponse;
import civilization.protocol.MessageMetadata;
import civilization.protocol.MessageType;
import civilization.protocol.RequestMessage;
import civilization.protocol.ResponseMessage;
import civilization.server.session.ClientSession;
import civilization.server.session.SessionId;
import civilization.server.session.SessionRegistry;
import civilization.server.security.ServerRequestGuard;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// PHASE 3 - Added in Phase 3
public final class RequestDispatcher {
    // PHASE 3 - Added in Phase 3
    private final SessionRegistry sessions;
    // PHASE 3 - Added in Phase 3
    private final ServerRequestGuard requestGuard;
    // PHASE 3 - Added in Phase 3
    private final Map<MessageType, ServerRequestHandler> handlers = new ConcurrentHashMap<>();

    // PHASE 3 - Added in Phase 3
    public RequestDispatcher(SessionRegistry sessions) {
        this(sessions, new ServerRequestGuard());
    }

    // PHASE 3 - Added in Phase 3
    public RequestDispatcher(SessionRegistry sessions, ServerRequestGuard requestGuard) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.requestGuard = Objects.requireNonNull(requestGuard, "requestGuard");
    }

    // PHASE 3 - Added in Phase 3
    public void register(MessageType type, ServerRequestHandler handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        if (handlers.putIfAbsent(type, handler) != null) {
            throw new IllegalStateException("Handler is already registered for " + type);
        }
    }

    // PHASE 3 - Added in Phase 3
    public CompletableFuture<? extends ResponseMessage> dispatch(
            SessionId sessionId, RequestMessage request) {
        Objects.requireNonNull(request, "request");
        ClientSession session;
        try {
            session = sessions.requireConnected(sessionId);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(error(
                    request, "INVALID_SESSION", failure.getMessage(), false));
        }
        // PHASE 3 - Added in Phase 3
        ServerRequestGuard.Decision securityDecision = requestGuard.inspect(
                sessionId, request.metadata().messageId());
        if (securityDecision == ServerRequestGuard.Decision.DUPLICATE) {
            return CompletableFuture.completedFuture(error(request, "DUPLICATE_REQUEST",
                    "This request message ID has already been processed", false));
        }
        if (securityDecision == ServerRequestGuard.Decision.RATE_LIMITED) {
            return CompletableFuture.completedFuture(error(request, "RATE_LIMITED",
                    "Too many requests; retry after the rate window", true));
        }
        ServerRequestHandler handler = handlers.get(request.type());
        if (handler == null) {
            return CompletableFuture.completedFuture(error(
                    request, "UNSUPPORTED_REQUEST", "Unsupported request: " + request.type(),
                    false));
        }
        try {
            return handler.handle(session, request);
        } catch (RuntimeException failure) {
            return CompletableFuture.completedFuture(error(
                    request, "REQUEST_REJECTED", failure.getMessage(), false));
        }
    }

    // PHASE 3 - Added in Phase 3
    public void forget(SessionId sessionId) {
        requestGuard.forget(sessionId);
    }

    // PHASE 3 - Added in Phase 3
    public void clearSecurityState() {
        requestGuard.clear();
    }

    // PHASE 3 - Added in Phase 3
    public int purgeSecurityStateForMissingSessions() {
        return requestGuard.removeSessionsMatching(sessionId -> sessions.find(sessionId).isEmpty());
    }

    // PHASE 3 - Added in Phase 3
    private static ErrorResponse error(
            RequestMessage request, String code, String message, boolean retryable) {
        return new ErrorResponse(MessageMetadata.createNow(), request.metadata().messageId(),
                code, message == null ? code : message, retryable);
    }
}
