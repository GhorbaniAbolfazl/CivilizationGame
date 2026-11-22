package civilization.protocol.json;

import civilization.protocol.MessageMetadata;
import civilization.protocol.MessageType;
import civilization.protocol.ProtocolMessage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

// PHASE 3 - Added in Phase 3
public final class JsonMessageSerializer implements MessageSerializer {
    // PHASE 3 - Added in Phase 3
    public static final int MAX_JSON_CHARACTERS = 4 * 1024 * 1024;
    // PHASE 3 - Added in Phase 3
    private static final Set<String> ENVELOPE_FIELDS = Set.of(
            "protocolVersion", "type", "messageId", "sentAt");
    // PHASE 3 - Added in Phase 3
    private final Gson gson;
    // PHASE 3 - Added in Phase 3
    private final MessageCodecRegistry registry;

    // PHASE 3 - Added in Phase 3
    public JsonMessageSerializer() {
        this(MessageCodecRegistry.standard());
    }

    // PHASE 3 - Added in Phase 3
    public JsonMessageSerializer(MessageCodecRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        gson = new GsonBuilder()
                .serializeNulls()
                .disableHtmlEscaping()
                .registerTypeAdapter(Instant.class, new InstantAdapter().nullSafe())
                .create();
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public String serialize(ProtocolMessage message) {
        Objects.requireNonNull(message, "message");
        @SuppressWarnings("unchecked")
        Class<? extends ProtocolMessage> messageClass =
                (Class<? extends ProtocolMessage>) message.getClass();
        MessageType registeredType = registry.messageType(messageClass);
        if (registeredType != message.type()) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "Message class/type mismatch: " + messageClass.getSimpleName());
        }
        // PHASE 3 - Added in Phase 3
        if (message.metadata().protocolVersion() != MessageMetadata.CURRENT_PROTOCOL_VERSION) {
            throw new ProtocolException(ProtocolException.Code.UNSUPPORTED_PROTOCOL_VERSION,
                    "Unsupported protocol version: " + message.metadata().protocolVersion());
        }
        JsonObject record = gson.toJsonTree(message, messageClass).getAsJsonObject();
        record.remove("metadata");
        JsonObject envelope = metadataObject(message.metadata());
        envelope.addProperty("type", registeredType.name());
        // PHASE 3 - Added in Phase 3
        for (var entry : record.entrySet()) envelope.add(entry.getKey(), entry.getValue());
        if (!envelope.keySet().equals(registry.allowedFields(registeredType))) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "Message fields do not match registry for " + registeredType);
        }
        return gson.toJson(envelope);
    }

    // PHASE 3 - Added in Phase 3
    @Override
    public ProtocolMessage deserialize(String json) {
        if (json == null || json.isBlank() || json.length() > MAX_JSON_CHARACTERS) {
            throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                    "JSON message is empty or exceeds the maximum size");
        }
        JsonObject envelope = parseEnvelope(json);
        MessageType type = readType(envelope);
        validateFields(envelope, type);
        validatePayloadShape(envelope, registry.messageClass(type));
        MessageMetadata metadata = readMetadata(envelope);
        JsonObject record = envelope.deepCopy();
        // PHASE 3 - Added in Phase 3
        for (String field : ENVELOPE_FIELDS) record.remove(field);
        record.add("metadata", gson.toJsonTree(metadata));
        try {
            ProtocolMessage decoded = gson.fromJson(record, registry.messageClass(type));
            if (decoded == null || decoded.type() != type || !decoded.metadata().equals(metadata)) {
                throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                        "Decoded message does not match its envelope");
            }
            return decoded;
        } catch (ProtocolException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "Invalid payload for " + type, failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    public MessageCodecRegistry registry() {
        return registry;
    }

    // PHASE 3 - Added in Phase 3
    private static JsonObject parseEnvelope(String json) {
        try {
            validateJsonSyntax(json);
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                        "Protocol message must be a JSON object");
            }
            return parsed.getAsJsonObject();
        } catch (ProtocolException failure) {
            throw failure;
        } catch (JsonParseException | IllegalStateException failure) {
            throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                    "Malformed JSON message", failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void validateJsonSyntax(String json) {
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            validateJsonValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                        "Unexpected trailing JSON content");
            }
        } catch (ProtocolException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                    "Malformed JSON message", failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void validateJsonValue(JsonReader reader, int depth) throws IOException {
        if (depth > 64) {
            throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                    "JSON nesting exceeds protocol limit");
        }
        JsonToken token = reader.peek();
        // PHASE 3 - Added in Phase 3
        switch (token) {
            case BEGIN_OBJECT -> {
                reader.beginObject();
                Set<String> names = new HashSet<>();
                // PHASE 3 - Added in Phase 3
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (!names.add(name)) {
                        throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                                "Duplicate JSON field: " + name);
                    }
                    validateJsonValue(reader, depth + 1);
                }
                reader.endObject();
            }
            case BEGIN_ARRAY -> {
                reader.beginArray();
                // PHASE 3 - Added in Phase 3
                while (reader.hasNext()) validateJsonValue(reader, depth + 1);
                reader.endArray();
            }
            case STRING, NUMBER -> reader.nextString();
            case BOOLEAN -> reader.nextBoolean();
            case NULL -> reader.nextNull();
            default -> throw new ProtocolException(ProtocolException.Code.MALFORMED_JSON,
                    "Unexpected JSON token: " + token);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static MessageType readType(JsonObject envelope) {
        String typeName = requiredString(envelope, "type");
        try {
            return MessageType.valueOf(typeName);
        } catch (IllegalArgumentException failure) {
            throw new ProtocolException(ProtocolException.Code.UNKNOWN_MESSAGE_TYPE,
                    "Unknown message type: " + typeName, failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    private void validateFields(JsonObject envelope, MessageType type) {
        Set<String> actual = new LinkedHashSet<>(envelope.keySet());
        Set<String> expected = registry.allowedFields(type);
        if (!actual.equals(expected)) {
            Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            Set<String> unknown = new LinkedHashSet<>(actual);
            unknown.removeAll(expected);
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "Message fields differ from schema; missing=" + missing
                            + ", unknown=" + unknown);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void validatePayloadShape(JsonObject envelope,
                                             Class<? extends ProtocolMessage> messageClass) {
        // PHASE 3 - Added in Phase 3
        for (RecordComponent component : messageClass.getRecordComponents()) {
            if (component.getName().equals("metadata")) continue;
            validateElement(envelope.get(component.getName()), component.getGenericType(),
                    component.getName());
        }
    }

    // PHASE 3 - Added in Phase 3
    private static void validateElement(JsonElement value, Type expectedType, String path) {
        if (value == null) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "Missing field: " + path);
        }
        if (value.isJsonNull()) {
            if (expectedType instanceof Class<?> type && type.isPrimitive()) {
                throw invalidShape(path);
            }
            return;
        }
        if (expectedType instanceof ParameterizedType parameterized) {
            Class<?> rawType = (Class<?>) parameterized.getRawType();
            // PHASE 3 - Added in Phase 3
            if (java.util.List.class.isAssignableFrom(rawType)) {
                if (!value.isJsonArray()) throw invalidShape(path);
                int index = 0;
                // PHASE 3 - Added in Phase 3
                for (JsonElement item : value.getAsJsonArray()) {
                    validateElement(item, parameterized.getActualTypeArguments()[0],
                            path + "[" + index++ + "]");
                }
                return;
            }
            if (java.util.Map.class.isAssignableFrom(rawType)) {
                if (!value.isJsonObject()) throw invalidShape(path);
                Type valueType = parameterized.getActualTypeArguments()[1];
                // PHASE 3 - Added in Phase 3
                for (var entry : value.getAsJsonObject().entrySet()) {
                    validateElement(entry.getValue(), valueType, path + "." + entry.getKey());
                }
                return;
            }
        }
        if (!(expectedType instanceof Class<?> expectedClass)) return;
        if (expectedClass == String.class || expectedClass == UUID.class
                || expectedClass == Instant.class || expectedClass.isEnum()) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw invalidShape(path);
            }
        } else if (expectedClass == boolean.class || expectedClass == Boolean.class) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
                throw invalidShape(path);
            }
        } else if (expectedClass.isPrimitive()
                || Number.class.isAssignableFrom(expectedClass)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw invalidShape(path);
            }
            // PHASE 3 - Added in Phase 3
            try {
                if (expectedClass == int.class || expectedClass == Integer.class) {
                    value.getAsBigDecimal().intValueExact();
                } else if (expectedClass == long.class || expectedClass == Long.class) {
                    value.getAsBigDecimal().longValueExact();
                }
            } catch (ArithmeticException | NumberFormatException failure) {
                throw invalidShape(path);
            }
        } else if (expectedClass.isRecord()) {
            if (!value.isJsonObject()) throw invalidShape(path);
            JsonObject nested = value.getAsJsonObject();
            Set<String> expectedNames = new LinkedHashSet<>();
            // PHASE 3 - Added in Phase 3
            for (RecordComponent component : expectedClass.getRecordComponents()) {
                expectedNames.add(component.getName());
                validateElement(nested.get(component.getName()), component.getGenericType(),
                        path + "." + component.getName());
            }
            if (!nested.keySet().equals(expectedNames)) throw invalidShape(path);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static ProtocolException invalidShape(String path) {
        return new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                "Invalid JSON value for field: " + path);
    }

    // PHASE 3 - Added in Phase 3
    private static MessageMetadata readMetadata(JsonObject envelope) {
        int version;
        try {
            JsonPrimitive value = requiredPrimitive(envelope, "protocolVersion");
            if (!value.isNumber()) throw new NumberFormatException("not a number");
            version = value.getAsBigDecimal().intValueExact();
        } catch (ArithmeticException | NumberFormatException failure) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "protocolVersion must be an integer", failure);
        }
        if (version != MessageMetadata.CURRENT_PROTOCOL_VERSION) {
            throw new ProtocolException(ProtocolException.Code.UNSUPPORTED_PROTOCOL_VERSION,
                    "Unsupported protocol version: " + version);
        }
        try {
            return new MessageMetadata(version,
                    UUID.fromString(requiredString(envelope, "messageId")),
                    Instant.parse(requiredString(envelope, "sentAt")));
        } catch (RuntimeException failure) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "Invalid message metadata", failure);
        }
    }

    // PHASE 3 - Added in Phase 3
    private static JsonObject metadataObject(MessageMetadata metadata) {
        Objects.requireNonNull(metadata, "metadata");
        JsonObject result = new JsonObject();
        result.addProperty("protocolVersion", metadata.protocolVersion());
        result.addProperty("messageId", metadata.messageId().toString());
        result.addProperty("sentAt", metadata.sentAt().toString());
        return result;
    }

    // PHASE 3 - Added in Phase 3
    private static String requiredString(JsonObject object, String name) {
        JsonPrimitive value = requiredPrimitive(object, name);
        if (!value.isString()) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    name + " must be a string");
        }
        return value.getAsString();
    }

    // PHASE 3 - Added in Phase 3
    private static JsonPrimitive requiredPrimitive(JsonObject object, String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive()) {
            throw new ProtocolException(ProtocolException.Code.INVALID_MESSAGE,
                    "Missing or invalid field: " + name);
        }
        return value.getAsJsonPrimitive();
    }

    // PHASE 3 - Added in Phase 3
    private static final class InstantAdapter extends TypeAdapter<Instant> {
        // PHASE 3 - Added in Phase 3
        @Override
        public void write(JsonWriter output, Instant value) throws IOException {
            output.value(value.toString());
        }

        // PHASE 3 - Added in Phase 3
        @Override
        public Instant read(JsonReader input) throws IOException {
            return Instant.parse(input.nextString());
        }
    }
}
