package com.codepocalypse.agent;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Output guardrail: redacts sensitive patterns (API keys, SSNs, credit cards)
 * from the model's response before returning to the user.
 */
public class OutputGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Map<Pattern, String> REDACTION_PATTERNS = Map.of(
            // API keys: sk-..., key-..., api_key=...
            Pattern.compile("(sk-[a-zA-Z0-9]{20,})"), "[REDACTED_API_KEY]",
            Pattern.compile("(key-[a-zA-Z0-9]{20,})"), "[REDACTED_API_KEY]",
            Pattern.compile("(?i)(api[_-]?key\\s*[=:]\\s*)[a-zA-Z0-9_\\-]{16,}"), "$1[REDACTED]",
            // SSN: 123-45-6789
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"), "[REDACTED_SSN]",
            // Credit card: 16-digit groups
            Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b"), "[REDACTED_CC]",
            // AWS keys
            Pattern.compile("(AKIA[A-Z0-9]{16})"), "[REDACTED_AWS_KEY]"
    );

    private final int order;

    public OutputGuardAdvisor(int order) {
        this.order = order;
    }

    @Override
    public String getName() {
        return "OutputGuardAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        return redactResponse(response);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return chain.nextStream(request).map(this::redactResponse);
    }

    private ChatClientResponse redactResponse(ChatClientResponse response) {
        var chatResponse = response.chatResponse();
        if (chatResponse == null || chatResponse.getResult() == null) {
            return response;
        }

        var output = chatResponse.getResult().getOutput();
        if (output == null || output.getText() == null) {
            return response;
        }

        String original = output.getText();
        String redacted = redact(original);

        if (original.equals(redacted)) {
            return response; // nothing to redact
        }

        var redactedMessage = new AssistantMessage(redacted);
        var redactedGeneration = new Generation(redactedMessage);
        var redactedChatResponse = new ChatResponse(List.of(redactedGeneration));
        return response.mutate().chatResponse(redactedChatResponse).build();
    }

    private String redact(String text) {
        String result = text;
        for (var entry : REDACTION_PATTERNS.entrySet()) {
            result = entry.getKey().matcher(result).replaceAll(entry.getValue());
        }
        return result;
    }
}
