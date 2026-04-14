package com.codepocalypse.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Conference CFP tools using the developers.events JSON feed.
 *
 * Feed structure: array of objects with:
 *   link: CFP submission URL
 *   until: deadline as text (e.g. "May-05-2026")
 *   untilDate: deadline as epoch millis
 *   conf.name: conference name
 *   conf.location: city + country
 *   conf.hyperlink: conference website
 *   conf.status: "open" or "closed"
 *   conf.date: [startEpochMillis, endEpochMillis]
 */
@Component
public class ConferenceTools {

    private static final String CFP_URL = "https://developers.events/all-cfps.json";
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = "Search for developer conferences with open CFPs by keyword in the conference name. Returns names, locations, deadlines, and links.")
    public String searchCfps(
            @ToolParam(description = "Search keyword like 'java', 'kubernetes', 'AI', 'devops'") String keyword,
            @ToolParam(description = "Max results (default 5)", required = false) Integer limit) {
        int max = (limit != null && limit > 0) ? limit : 5;
        try {
            String kw = keyword.toLowerCase();
            List<Map<String, Object>> matches = fetchOpenCfps().stream()
                    .filter(c -> confName(c).toLowerCase().contains(kw))
                    .limit(max)
                    .toList();
            return format("CFPs matching '" + keyword + "'", matches);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Find conferences with CFPs closing soon within a given number of days")
    public String closingCfps(
            @ToolParam(description = "Days to look ahead (default 14)", required = false) Integer days) {
        int daysAhead = (days != null && days > 0) ? days : 14;
        try {
            long now = System.currentTimeMillis();
            long cutoff = now + (long) daysAhead * 86400000L;
            List<Map<String, Object>> closing = fetchOpenCfps().stream()
                    .filter(c -> {
                        long deadline = deadlineMillis(c);
                        return deadline >= now && deadline <= cutoff;
                    })
                    .sorted((a, b) -> Long.compare(deadlineMillis(a), deadlineMillis(b)))
                    .limit(10)
                    .toList();
            return format("CFPs closing within " + daysAhead + " days", closing);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @Tool(description = "Search for developer conferences by location or country")
    public String searchByLocation(
            @ToolParam(description = "Location like 'Germany', 'Netherlands', 'USA', 'Online'") String location) {
        try {
            String loc = location.toLowerCase();
            List<Map<String, Object>> matches = fetchOpenCfps().stream()
                    .filter(c -> confLocation(c).toLowerCase().contains(loc))
                    .limit(10)
                    .toList();
            return format("CFPs in '" + location + "'", matches);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private List<Map<String, Object>> fetchOpenCfps() throws Exception {
        var req = HttpRequest.newBuilder().uri(URI.create(CFP_URL)).GET().build();
        var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        List<Map<String, Object>> all = mapper.readValue(resp.body(),
                new TypeReference<List<Map<String, Object>>>() {});
        // Filter to open CFPs with future deadlines
        long now = System.currentTimeMillis();
        return all.stream()
                .filter(c -> "open".equals(confStatus(c)) && deadlineMillis(c) > now)
                .toList();
    }

    private String format(String title, List<Map<String, Object>> cfps) {
        if (cfps.isEmpty()) return title + ": No results found.";
        var sb = new StringBuilder(title + " (" + cfps.size() + " results):\n\n");
        for (int i = 0; i < cfps.size(); i++) {
            var c = cfps.get(i);
            sb.append(i + 1).append(". ").append(confName(c)).append("\n");
            sb.append("   Location: ").append(confLocation(c)).append("\n");
            sb.append("   CFP Deadline: ").append(str(c, "until")).append("\n");
            sb.append("   CFP Link: ").append(str(c, "link")).append("\n");
            sb.append("   Website: ").append(confHyperlink(c)).append("\n");
            long start = confStartDate(c);
            if (start > 0) {
                sb.append("   Conference Date: ")
                        .append(Instant.ofEpochMilli(start).atOffset(ZoneOffset.UTC).toLocalDate())
                        .append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> conf(Map<String, Object> cfp) {
        Object c = cfp.get("conf");
        return c instanceof Map ? (Map<String, Object>) c : Map.of();
    }

    private String confName(Map<String, Object> cfp) { return str(conf(cfp), "name"); }
    private String confLocation(Map<String, Object> cfp) { return str(conf(cfp), "location"); }
    private String confStatus(Map<String, Object> cfp) { return str(conf(cfp), "status"); }
    private String confHyperlink(Map<String, Object> cfp) { return str(conf(cfp), "hyperlink"); }

    @SuppressWarnings("unchecked")
    private long confStartDate(Map<String, Object> cfp) {
        Object d = conf(cfp).get("date");
        if (d instanceof List<?> list && !list.isEmpty()) {
            return ((Number) list.get(0)).longValue();
        }
        return 0;
    }

    private long deadlineMillis(Map<String, Object> cfp) {
        Object ud = cfp.get("untilDate");
        return ud instanceof Number ? ((Number) ud).longValue() : 0;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : "";
    }
}
