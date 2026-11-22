package civilization.protocol;

import java.util.Map;

// PHASE 3 - Added in Phase 3
public record ResourceBundleDto(
        // PHASE 3 - Added in Phase 3
        Map<String, Long> quantities) {
    // PHASE 3 - Added in Phase 3
    public ResourceBundleDto {
        quantities = ProtocolValidation.map(quantities, "quantities");
        // PHASE 3 - Added in Phase 3
        for (var entry : quantities.entrySet()) {
            ProtocolValidation.text(entry.getKey(), "resource name", 64);
            ProtocolValidation.nonNegative(entry.getValue(), "resource quantity");
        }
    }
}
