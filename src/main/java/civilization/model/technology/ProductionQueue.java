package civilization.model.technology;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Stores Town Hall production orders; the application service enforces one active slot. */
public final class ProductionQueue implements java.io.Serializable {
    private static final long serialVersionUID = 1L;
    private final Deque<ProductionOrder> orders = new ArrayDeque<>();

    public void enqueue(ProductionOrder order) {
        if (!orders.isEmpty()) {
            throw new IllegalStateException("Town Hall production slot is occupied");
        }
        orders.addLast(Objects.requireNonNull(order, "order"));
    }

    public Optional<ProductionOrder> currentOrder() {
        return Optional.ofNullable(orders.peekFirst());
    }

    public int size() {
        return orders.size();
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public List<ProductionOrder> orders() {
        return List.copyOf(orders);
    }

    /** Removes the first order only after an external queue service has completed its effect. */
    public ProductionOrder removeCompletedOrder() {
        ProductionOrder current = orders.peekFirst();
        if (current == null || !current.isComplete()) {
            throw new IllegalStateException("The current order is not complete");
        }
        return orders.removeFirst();
    }

    /** Cancels the active order. Its paid cost remains spent. */
    public ProductionOrder cancelCurrent() {
        ProductionOrder current = orders.pollFirst();
        if (current == null) throw new IllegalStateException("Town Hall production slot is empty");
        return current;
    }
}
