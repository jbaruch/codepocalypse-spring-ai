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
import java.util.regex.Pattern;

/**
 * Input guardrail: detects prompt injection attempts and short-circuits
 * with a blocked message before reaching the model.
 */
public class PromptInjectionGuardAdvisor implements CallAdvisor, StreamAdvisor {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("ignore\\s+your\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard\\s+(all\\s+)?(previous|prior|above|your)\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you\\s+are\\s+now\\s+(a|an)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend\\s+you\\s+are", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act\\s+as\\s+if\\s+you\\s+have\\s+no\\s+restrictions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(your|the)\\s+system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("show\\s+(me\\s+)?(your|the)\\s+system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("output\\s+(your|the)\\s+system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("delete\\s+(all|everything|the\\s+database)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("drop\\s+table", Pattern.CASE_INSENSITIVE),
            Pattern.compile("execute\\s+(this\\s+)?command", Pattern.CASE_INSENSITIVE)
    );

    private static final String BLOCKED_MESSAGE =
            "Nice try. I'm a snarky Java agent, not a gullible intern. " +
            "Prompt injection detected and blocked. You're welcome.";

    private final int order;

    public PromptInjectionGuardAdvisor(int order) {
        this.order = order;
    }

    @Override
    public String getName() {
        return "PromptInjectionGuardAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (isInjectionAttempt(request)) {
            return blockedResponse();
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (isInjectionAttempt(request)) {
            return Flux.just(blockedResponse());
        }
        return chain.nextStream(request);
    }

    private boolean isInjectionAttempt(ChatClientRequest request) {
        var userMessage = request.prompt().getUserMessage();
        if (userMessage == null) return false;
        String text = userMessage.getText();
        if (text == null) return false;
        return INJECTION_PATTERNS.stream().anyMatch(p -> p.matcher(text).find());
    }

    private ChatClientResponse blockedResponse() {
        var assistantMessage = new AssistantMessage(BLOCKED_MESSAGE);
        var generation = new Generation(assistantMessage);
        var chatResponse = new ChatResponse(List.of(generation));
        return ChatClientResponse.builder().chatResponse(chatResponse).build();
    }
}
