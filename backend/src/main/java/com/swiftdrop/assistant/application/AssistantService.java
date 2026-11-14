package com.swiftdrop.assistant.application;

import java.util.Map;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Answers customer questions through Gemini function calling. The model decides when to call
 * {@code get_parcel_status} or {@code get_price_quote}; this service executes those calls against
 * the real delivery and pricing modules and feeds the results back until the model replies in
 * natural language. Stateless: each request is a single-turn conversation.
 */
@Service
public class AssistantService {

    private static final int MAX_TOOL_ROUNDS = 5;

    private final GeminiClient gemini;
    private final AssistantTools tools;
    private final JsonMapper mapper = JsonMapper.builder().build();

    AssistantService(GeminiClient gemini, AssistantTools tools) {
        this.gemini = gemini;
        this.tools = tools;
    }

    public String chat(String message) {
        if (!gemini.isConfigured()) {
            return "The AI assistant is not configured yet. Please try again later.";
        }

        ArrayNode contents = mapper.createArrayNode();
        contents.add(userContent(message));
        ArrayNode toolDeclarations = toolDeclarations();

        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            JsonNode content = gemini.generateContent(contents, toolDeclarations);
            contents.add(content.deepCopy());

            JsonNode parts = content.path("parts");
            if (!parts.isArray()) {
                break;
            }

            StringBuilder text = new StringBuilder();
            JsonNode functionCall = null;
            for (JsonNode part : parts) {
                if (part.has("text")) {
                    text.append(part.path("text").asText());
                }
                if (part.has("functionCall") && functionCall == null) {
                    functionCall = part.path("functionCall");
                }
            }

            if (functionCall == null) {
                String reply = text.toString().trim();
                return reply.isEmpty() ? "I could not come up with an answer. Please try rephrasing." : reply;
            }

            // Execute the requested tool and send the result back as a functionResponse turn
            String name = functionCall.path("name").asText("");
            JsonNode args = functionCall.path("args");
            Map<String, Object> result;
            try {
                result = tools.execute(name, args);
            } catch (RuntimeException e) {
                result = Map.of("error", "Tool execution failed: " + e.getMessage());
            }

            ObjectNode functionTurn = mapper.createObjectNode();
            functionTurn.put("role", "user");
            ArrayNode responseParts = functionTurn.putArray("parts");
            ObjectNode functionResponse = responseParts.addObject().putObject("functionResponse");
            functionResponse.put("name", name);
            functionResponse.set("response", mapper.valueToTree(result));
            contents.add(functionTurn);
        }

        return "Sorry, I could not complete that request. Please try again.";
    }

    private ObjectNode userContent(String message) {
        ObjectNode content = mapper.createObjectNode();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", message);
        return content;
    }

    private ArrayNode toolDeclarations() {
        ArrayNode declarationsWrapper = mapper.createArrayNode();
        ObjectNode tool = declarationsWrapper.addObject();
        ArrayNode declarations = tool.putArray("functionDeclarations");

        ObjectNode track = declarations.addObject();
        track.put("name", "get_parcel_status");
        track.put("description",
                "Get the current status and location of a parcel by its tracking number "
                        + "(for example SWD-XXXX). Use when the customer asks where their parcel is.");
        ObjectNode trackParams = track.putObject("parameters");
        trackParams.put("type", "OBJECT");
        ObjectNode trackProps = trackParams.putObject("properties");
        trackProps.putObject("trackingNumber")
                .put("type", "STRING")
                .put("description", "The parcel tracking number");
        trackParams.putArray("required").add("trackingNumber");

        ObjectNode quote = declarations.addObject();
        quote.put("name", "get_price_quote");
        quote.put("description",
                "Calculate the delivery fee for a parcel. Use when the customer asks how much "
                        + "it costs to send a parcel. Active delivery zones: "
                        + String.join(", ", tools.zoneNames()) + ".");
        ObjectNode quoteParams = quote.putObject("parameters");
        quoteParams.put("type", "OBJECT");
        ObjectNode quoteProps = quoteParams.putObject("properties");
        quoteProps.putObject("weightGrams")
                .put("type", "INTEGER")
                .put("description", "Parcel weight in grams (1-30000). 1 kg = 1000 grams.");
        quoteProps.putObject("size")
                .put("type", "STRING")
                .put("description", "Parcel size category: SMALL, MEDIUM or LARGE");
        quoteProps.putObject("pickupZone")
                .put("type", "STRING")
                .put("description", "Pickup zone name, e.g. Colombo. Ask the customer if unknown.");
        quoteProps.putObject("dropoffZone")
                .put("type", "STRING")
                .put("description", "Delivery zone name, e.g. Kandy. Ask the customer if unknown.");
        quoteParams.putArray("required")
                .add("weightGrams").add("size").add("pickupZone").add("dropoffZone");

        return declarationsWrapper;
    }
}
