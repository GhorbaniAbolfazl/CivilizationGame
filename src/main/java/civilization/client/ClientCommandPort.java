package civilization.client;

import civilization.protocol.RequestMessage;
import civilization.protocol.ResponseMessage;

import java.util.concurrent.CompletableFuture;

// PHASE 3 - Added in Phase 3
public interface ClientCommandPort {
    // PHASE 3 - Added in Phase 3
    CompletableFuture<ResponseMessage> send(RequestMessage request);
}
