package com.codepocalypse.agent;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class AgentTools {

    @Tool(description = "Get the current date and time")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    @Tool(description = "Get the current weather for a city (simulated)")
    public String getWeather(@ToolParam(description = "City name") String city) {
        return "Weather in " + city + ": 22°C, sunny with light clouds";
    }
}
