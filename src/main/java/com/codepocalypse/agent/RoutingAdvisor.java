package com.codepocalypse.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import reactor.core.publisher.Flux;

import java.util.Set;

/**
 * Routes conference/events-related queries to a specialized eventsAgent ChatClient.
 * Non-matching queries pass through the default advisor chain.
 */
public class RoutingAdvisor implements CallAdvisor, StreamAdvisor {

    private static final Set<String> EVENT_KEYWORDS = Set.of(
            "conference", "cfp", "event", "speaker", "deadline"
    );

    private final ChatClient eventsAgent;
    private final int order;

    public RoutingAdvisor(ChatClient eventsAgent, int order) {
        this.eventsAgent = eventsAgent;
        this.order = order;
    }

    @Override
    public String getName() {
        return "RoutingAdvisor";
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        if (isEventQuery(request)) {
            String userText = extractUserText(request);
            return eventsAgent.prompt()
                    .user(userText)
                    .call()
                    .chatClientResponse();
        }
        return chain.nextCall(request);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        if (isEventQuery(request)) {
            String userText = extractUserText(request);
            return eventsAgent.prompt()
                    .user(userText)
                    .stream()
                    .chatClientResponse();
        }
        return chain.nextStream(request);
    }

    private boolean isEventQuery(ChatClientRequest request) {
        String text = extractUserText(request).toLowerCase();
        return EVENT_KEYWORDS.stream().anyMatch(text::contains);
    }

    private String extractUserText(ChatClientRequest request) {
        var userMessage = request.prompt().getUserMessage();
        return userMessage != null ? userMessage.getText() : "";
    }
}
