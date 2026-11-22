package com.swiftdrop.assistant.application;

import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Answers customer questions through Groq function calling (OpenAI-compatible API). The model
 * decides when to call {@code get_parcel_status} or {@code get_price_quote}; this service
 * executes those calls against the real delivery and pricing modules and feeds the results back
 * as {@code tool} messages until the model replies in natural language. Stateless: each request
 * is a single-turn conversation.
 */
@Service
public class AssistantService {

    private static final int MAX_TOOL_ROUNDS = 5;

    private final GroqClient groq;
    private final AssistantTools tools;
    private final JsonMapper mapper = JsonMapper.builder().build();

    AssistantService(GroqClient groq, AssistantTools tools) {
        this.groq = groq;
        this.tools = tools;
    }

    public String chat(String message) {
        if (!groq.isConfigured()) {
            return "The AI assistant is not configured yet. Please try again later.";
        }

        ArrayNode messages = mapper.createArrayNode();
        messages.add(userMessage(message));
        ArrayNode toolDeclarations = toolDeclarations();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode reply = groq.chatCompletion(messages, toolDeclarations);
            messages.add(reply.deepCopy());

            JsonNode toolCalls = reply.path("tool_calls");
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String text = reply.path("content").asText("").trim();
                return text.isEmpty()
                        ? "I could not come up with an answer. Please try rephrasing."
                        : text;
            }

            // Execute every requested tool and return each result as a tool message
            for (JsonNode call : toolCalls) {
                String callId = call.path("id").asText("");
                JsonNode function = call.path("function");
                String name = function.path("name").asText("");
                JsonNode args = parseArguments(function.path("arguments").asText("{}"));

                Map<String, Object> result;
                try {
                    result = tools.execute(name, args);
                } catch (RuntimeException e) {
                    result = Map.of("error", "Tool execution failed: " + e.getMessage());
                }

                ObjectNode toolMessage = mapper.createObjectNode();
                toolMessage.put("role", "tool");
                toolMessage.put("tool_call_id", callId);
                try {
                    toolMessage.put("content", mapper.writeValueAsString(result));
                } catch (RuntimeException e) {
                    toolMessage.put("content", "{\"error\":\"result serialization failed\"}");
                }
                messages.add(toolMessage);
            }
        }

        return "Sorry, I could not complete that request. Please try again.";
    }

    private JsonNode parseArguments(String arguments) {
        try {
            return mapper.readTree(arguments);
        } catch (RuntimeException e) {
            return mapper.createObjectNode();
        }
    }

    private ObjectNode userMessage(String message) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", "user");
        msg.put("content", message);
        return msg;
    }

    private ArrayNode toolDeclarations() {
        ArrayNode declarations = mapper.createArrayNode();

        // get_parcel_status(trackingNumber)
        ObjectNode trackParams = mapper.createObjectNode();
        trackParams.put("type", "object");
        ObjectNode trackProps = trackParams.putObject("properties");
        trackProps.putObject("trackingNumber")
                .put("type", "string")
                .put("description", "The parcel tracking number");
        trackParams.putArray("required").add("trackingNumber");
        declarations.add(functionTool("get_parcel_status",
                "Get the current status and location of a parcel by its tracking number "
                        + "(for example SWD-XXXX). Use when the customer asks where their parcel is.",
                trackParams));

        // get_price_quote(weightGrams, size, pickupZone, dropoffZone)
        ObjectNode quoteParams = mapper.createObjectNode();
        quoteParams.put("type", "object");
        ObjectNode quoteProps = quoteParams.putObject("properties");
        quoteProps.putObject("weightGrams")
                .put("type", "integer")
                .put("description", "Parcel weight in grams (1-30000). 1 kg = 1000 grams.")
                .put("minimum", 1)
                .put("maximum", 30000);
        quoteProps.putObject("size")
                .put("type", "string")
                .put("description", "Parcel size category")
                .putArray("enum").add("SMALL").add("MEDIUM").add("LARGE");
        quoteProps.putObject("pickupZone")
                .put("type", "string")
                .put("description", "Pickup zone name, e.g. Colombo. Ask the customer if unknown.");
        quoteProps.putObject("dropoffZone")
                .put("type", "string")
                .put("description", "Delivery zone name, e.g. Kandy. Ask the customer if unknown.");
        quoteParams.putArray("required")
                .add("weightGrams").add("size").add("pickupZone").add("dropoffZone");
        declarations.add(functionTool("get_price_quote",
                "Calculate the delivery fee for a parcel. Use when the customer asks how much "
                        + "it costs to send a parcel. Active delivery zones: "
                        + String.join(", ", tools.zoneNames()) + ".",
                quoteParams));

        return declarations;
    }

    private ObjectNode functionTool(String name, String description, JsonNode parameters) {
        ObjectNode tool = mapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = tool.putObject("function");
        function.put("name", name);
        function.put("description", description);
        function.set("parameters", parameters);
        return tool;
    }
}
