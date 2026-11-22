package civilization.server.dispatch;

import civilization.protocol.RequestMessage;
import civilization.protocol.ResponseMessage;
import civilization.server.session.ClientSession;

import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
@FunctionalInterface
public interface ServerRequestHandler {
    // PHASE 3 - Added in Phase 3
    CompletableFuture<? extends ResponseMessage> handle(
            ClientSession session, RequestMessage request);
}
