package civilization.server.match;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

// PHASE 3 - Added in Phase 3
public final class SerializedServerState<S> implements AutoCloseable {
    // PHASE 3 - Added in Phase 3
    public static final int DEFAULT_PENDING_OPERATION_CAPACITY = 2_048;
    // PHASE 3 - Added in Phase 3
    private final S state;
    // PHASE 3 - Added in Phase 3
    private final ThreadPoolExecutor commandExecutor;
    // PHASE 3 - Added in Phase 3
    private final java.util.Set<CompletableFuture<?>> pendingOperations =
            ConcurrentHashMap.newKeySet();
    // PHASE 3 - Added in Phase 3
    private final AtomicBoolean closed = new AtomicBoolean();

    // PHASE 3 - Added in Phase 3
    public SerializedServerState(S state, String threadName) {
        this(state, threadName, DEFAULT_PENDING_OPERATION_CAPACITY);
    }

    // PHASE 3 - Added in Phase 3
    public SerializedServerState(S state, String threadName, int pendingOperationCapacity) {
        this.state = Objects.requireNonNull(state, "state");
        Objects.requireNonNull(threadName, "threadName");
        if (pendingOperationCapacity <= 0) {
            throw new IllegalArgumentException("Pending operation capacity must be positive");
        }
        // PHASE 3 - Added in Phase 3
        commandExecutor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(pendingOperationCapacity), runnable -> {
                    Thread thread = new Thread(runnable, threadName);
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    // PHASE 3 - Added in Phase 3
    public <R> CompletableFuture<R> submit(StateOperation<S, R> operation) {
        Objects.requireNonNull(operation, "operation");
        if (closed.get()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Authoritative state is closed"));
        }
        CompletableFuture<R> result = new CompletableFuture<>();
        pendingOperations.add(result);
        try {
            commandExecutor.execute(() -> {
                try {
                    result.complete(operation.apply(state));
                } catch (Throwable failure) {
                    result.completeExceptionally(failure);
                } finally {
                    // PHASE 3 - Added in Phase 3
                    pendingOperations.remove(result);
                }
            });
        } catch (RejectedExecutionException rejected) {
            pendingOperations.remove(result);
            result.completeExceptionally(rejected);
        }
        return result;
    }

    // PHASE 3 - Added in Phase 3
    public int pendingOperationCount() {
        return pendingOperations.size();
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        commandExecutor.shutdownNow();
        IllegalStateException failure = new IllegalStateException(
                "Authoritative state closed before the operation completed");
        // PHASE 3 - Added in Phase 3
        pendingOperations.forEach(future -> future.completeExceptionally(failure));
        pendingOperations.clear();
    }
}
