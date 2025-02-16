package com.jithin.ai_content_platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jithin.ai_content_platform.model.TrendData;
import com.jithin.ai_content_platform.model.TrendPattern;
import org.springframework.cache.annotation.Cacheable;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WebScrapingService {

    @Autowired
    private ObjectMapper objectMapper;

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int TIMEOUT = 10000; // 10 seconds
    private static final ExecutorService executorService = Executors.newFixedThreadPool(4);

    @Cacheable(value = "recentTrends", key = "#limit")
    public List<TrendData> scrapeRecentSources(int limit) {
        List<TrendData> allTrends = scrapeAllSources();
        return allTrends.stream()
                .sorted(Comparator.comparing(TrendData::getAnalysisTimestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "allTrends", key = "'all'")
    public List<TrendData> scrapeAllSources() {
        List<Future<List<TrendData>>> futures = new ArrayList<>();
        
        // Submit all scraping tasks
        futures.add(executorService.submit(this::scrapeHackerNews));
        futures.add(executorService.submit(this::scrapeGitHubTrends));
        futures.add(executorService.submit(this::scrapeStackOverflow));
        futures.add(executorService.submit(this::scrapeTwitterTrends));
        futures.add(executorService.submit(this::scrapeGoogleTrends));
        
        List<TrendData> allTrends = new ArrayList<>();
        
        // Collect results
        for (Future<List<TrendData>> future : futures) {
            try {
                List<TrendData> trends = future.get(30, TimeUnit.SECONDS);
                // Ensure proper deserialization of each TrendData object
                for (TrendData trend : trends) {
                    if (trend != null) {
                        try {
                            // Populate transient fields if they're stored as JSON strings
                            if (trend.getTrendingTopics() != null) {
                                trend.setTrendingTopicsMap(objectMapper.readValue(
                                    trend.getTrendingTopics(),
                                    new TypeReference<Map<String, Map<String, Object>>>() {}
                                ));
                            }
                            if (trend.getSeasonalityData() != null) {
                                trend.setSeasonalityMap(objectMapper.readValue(
                                    trend.getSeasonalityData(),
                                    new TypeReference<Map<String, Object>>() {}
                                ));
                            }
                            if (trend.getHistoricalValues() != null) {
                                trend.setHistoricalValuesList(objectMapper.readValue(
                                    trend.getHistoricalValues(),
                                    new TypeReference<List<Double>>() {}
                                ));
                            }
                            if (trend.getHistoricalDates() != null) {
                                trend.setHistoricalDatesList(objectMapper.readValue(
                                    trend.getHistoricalDates(),
                                    new TypeReference<List<String>>() {}
                                ));
                            }
                        } catch (JsonProcessingException e) {
                            log.error("Error deserializing JSON data for trend: {}", e.getMessage());
                        }
                    }
                }
                allTrends.addAll(trends);
            } catch (Exception e) {
                log.error("Error collecting trends from source: {}", e.getMessage());
            }
        }
        
        return allTrends;
    }

    public List<TrendData> scrapeHackerNews() {
        List<TrendData> trends = new ArrayList<>();
        try {
            // Scrape current front page
            Document frontPage = getDocumentWithRetry("https://news.ycombinator.com");
            Map<String, List<Integer>> historicalData = new HashMap<>();
            
            // Scrape historical data from past few pages
            for (int page = 1; page <= 3; page++) {
                Document doc = page == 1 ? frontPage : 
                    getDocumentWithRetry("https://news.ycombinator.com/news?p=" + page);
                Elements stories = doc.select(".athing");
                
                for (Element story : stories) {
                    try {
                        String title = story.select(".titleline a").text();
                        String points = story.nextElementSibling().select(".score").text();
                        String comments = story.nextElementSibling().select("a:contains(comments)").text();
                        String timeAgo = story.nextElementSibling().select(".age").text();
                        
                        // Get the story URL to track duplicates/updates
                        String storyUrl = story.select(".titleline a").attr("href");
                        
                        // Track points over time for each unique story URL
                        historicalData.computeIfAbsent(storyUrl, k -> new ArrayList<>())
                            .add(extractNumber(points));
                        
                        if (page == 1) { // Only add current front page stories to trends
                            TrendData trend = new TrendData();
                            trend.setAnalysisTimestamp(LocalDateTime.now());
                            trend.setTopic(title);
                            trend.setCategory("Technology");
                            trend.setTrendScore(calculateScore(points, comments));
                            trend.setConfidenceScore(0.8);
                            
                            // Calculate trend pattern based on points history
                            List<Double> pointsHistory = historicalData.get(storyUrl).stream()
                                .map(p -> (double) p)
                                .collect(Collectors.toList());
                            trend.setTrendPattern(detectTrendPattern(pointsHistory));
                            
                            // Set metadata
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("source", "HackerNews");
                            metadata.put("points", extractNumber(points));
                            metadata.put("comments", extractNumber(comments));
                            metadata.put("timeAgo", timeAgo);
                            metadata.put("url", storyUrl);
                            metadata.put("historicalPoints", pointsHistory);
                            trend.setMetadata(metadata);
                            
                            trends.add(trend);
                        }
                    } catch (Exception e) {
                        log.warn("Error processing HackerNews story: {}", e.getMessage());
                    }
                }
                
                // Small delay between page requests
                if (page < 3) Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.error("Error scraping HackerNews: {}", e.getMessage());
        }
        return trends;
    }

    public List<TrendData> scrapeGitHubTrends() {
        List<TrendData> trends = new ArrayList<>();
        try {
            // Scrape trending repositories for different time ranges
            Map<String, Map<String, Object>> repoStats = new HashMap<>();
            
            for (String timeRange : Arrays.asList("daily", "weekly", "monthly")) {
                Document doc = getDocumentWithRetry("https://github.com/trending?since=" + timeRange);
                Elements repositories = doc.select("article.Box-row");
                
                for (Element repo : repositories) {
                    try {
                        String name = repo.select("h2 a").attr("href").substring(1);
                        String description = repo.select("p").text();
                        String stars = repo.select(".octicon-star").parents().first().text().trim();
                        String language = repo.select("[itemprop=programmingLanguage]").text();
                        String starsToday = repo.select(".d-inline-block.float-sm-right").text();
                        
                        // Track stars over time
                        repoStats.computeIfAbsent(name, k -> new HashMap<>())
                            .put(timeRange + "Stars", extractNumber(stars));
                        
                        if (timeRange.equals("daily")) { // Only add daily trending repos to results
                            TrendData trend = new TrendData();
                            trend.setAnalysisTimestamp(LocalDateTime.now());
                            trend.setTopic(name);
                            trend.setCategory("Development");
                            
                            // Calculate trend score based on stars and recent growth
                            double baseScore = calculateGitHubScore(stars);
                            double growthBonus = extractNumber(starsToday) / 1000.0;
                            trend.setTrendScore(Math.min(1.0, baseScore + growthBonus));
                            
                            trend.setConfidenceScore(0.9);
                            
                            // Calculate trend pattern
                            List<Double> starHistory = new ArrayList<>();
                            starHistory.add((double) extractNumber(stars));
                            if (repoStats.get(name).containsKey("weeklyStars")) {
                                starHistory.add((double) repoStats.get(name).get("weeklyStars"));
                            }
                            if (repoStats.get(name).containsKey("monthlyStars")) {
                                starHistory.add((double) repoStats.get(name).get("monthlyStars"));
                            }
                            trend.setTrendPattern(detectTrendPattern(starHistory));
                            
                            // Set metadata
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("source", "GitHub");
                            metadata.put("description", description);
                            metadata.put("language", language);
                            metadata.put("currentStars", extractNumber(stars));
                            metadata.put("starsToday", extractNumber(starsToday));
                            metadata.put("starHistory", starHistory);
                            trend.setMetadata(metadata);
                            
                            trends.add(trend);
                        }
                    } catch (Exception e) {
                        log.warn("Error processing GitHub repository: {}", e.getMessage());
                    }
                }
                
                // Small delay between requests
                if (!timeRange.equals("monthly")) Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.error("Error scraping GitHub: {}", e.getMessage());
        }
        return trends;
    }

    public List<TrendData> scrapeStackOverflow() {
        List<TrendData> trends = new ArrayList<>();
        try {
            // Scrape questions from different tabs to get trend data
            Map<String, Map<String, Object>> questionStats = new HashMap<>();
            
            for (String tab : Arrays.asList("hot", "week", "month")) {
                Document doc = getDocumentWithRetry("https://stackoverflow.com/questions?tab=" + tab);
                Elements questions = doc.select(".question-summary");
                
                for (Element question : questions) {
                    try {
                        String title = question.select(".question-hyperlink").text();
                        String votes = question.select(".vote-count-post").text();
                        String views = question.select(".views").attr("title");
                        String tags = question.select(".post-tag").text();
                        String timeAgo = question.select(".relativetime").text();
                        String questionId = question.select(".question-hyperlink").attr("href");
                        
                        // Track stats over time
                        questionStats.computeIfAbsent(questionId, k -> new HashMap<>())
                            .put(tab + "Stats", Map.of(
                                "votes", extractNumber(votes),
                                "views", extractNumber(views)
                            ));
                        
                        if (tab.equals("hot")) { // Only add hot questions to trends
                            TrendData trend = new TrendData();
                            trend.setAnalysisTimestamp(LocalDateTime.now());
                            trend.setTopic(title);
                            trend.setCategory("Programming");
                            
                            // Calculate trend score based on votes and views
                            trend.setTrendScore(calculateStackOverflowScore(votes, views));
                            trend.setConfidenceScore(0.85);
                            
                            // Calculate trend pattern
                            List<Double> activityHistory = new ArrayList<>();
                            activityHistory.add(calculateStackOverflowScore(votes, views));
                            
                            // Add historical data if available
                            if (questionStats.get(questionId).containsKey("weekStats")) {
                                Map<String, Integer> weekStats = (Map<String, Integer>) questionStats.get(questionId).get("weekStats");
                                activityHistory.add(calculateStackOverflowScore(
                                    String.valueOf(weekStats.get("votes")),
                                    String.valueOf(weekStats.get("views"))
                                ));
                            }
                            if (questionStats.get(questionId).containsKey("monthStats")) {
                                Map<String, Integer> monthStats = (Map<String, Integer>) questionStats.get(questionId).get("monthStats");
                                activityHistory.add(calculateStackOverflowScore(
                                    String.valueOf(monthStats.get("votes")),
                                    String.valueOf(monthStats.get("views"))
                                ));
                            }
                            
                            trend.setTrendPattern(detectTrendPattern(activityHistory));
                            
                            // Set metadata
                            Map<String, Object> metadata = new HashMap<>();
                            metadata.put("source", "StackOverflow");
                            metadata.put("votes", extractNumber(votes));
                            metadata.put("views", extractNumber(views));
                            metadata.put("tags", tags);
                            metadata.put("timeAgo", timeAgo);
                            metadata.put("questionId", questionId);
                            metadata.put("activityHistory", activityHistory);
                            trend.setMetadata(metadata);
                            
                            trends.add(trend);
                        }
                    } catch (Exception e) {
                        log.warn("Error processing StackOverflow question: {}", e.getMessage());
                    }
                }
                
                // Small delay between requests
                if (!tab.equals("month")) Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.error("Error scraping StackOverflow: {}", e.getMessage());
        }
        return trends;
    }

    private Document getDocumentWithRetry(String url) throws IOException {
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                return Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT)
                    .get();
            } catch (IOException e) {
                retryCount++;
                if (retryCount == maxRetries) {
                    throw e;
                }
                try {
                    Thread.sleep(1000 * retryCount); // Exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry", e);
                }
            }
        }
        throw new IOException("Failed after " + maxRetries + " retries");
    }

    public List<TrendData> scrapeTwitterTrends() {
        List<TrendData> trends = new ArrayList<>();
        Map<String, Map<String, Object>> trendHistory = new HashMap<>();
        
        try {
            // Scrape trends from different locations to get a broader view
            String[] locations = {"worldwide", "united-states", "united-kingdom", "canada", "australia"};
            
            for (String location : locations) {
                Document doc = getDocumentWithRetry("https://twitter.com/i/trends?f=worldwide&lang=en&location=" + location);
                Elements trendItems = doc.select("[data-testid='trend']").select(".css-1dbjc4n");
                
                for (Element trend : trendItems) {
                    try {
                        String topic = trend.select("[data-testid='trendName']").text();
                        String tweetCount = trend.select("[data-testid='trendMetadata']").text();
                        String category = trend.select(".r-1qd0xha").text();
                        
                        if (!topic.isEmpty()) {
                            // Track trend metrics across locations
                            trendHistory.computeIfAbsent(topic, k -> new HashMap<>())
                                .put(location, Map.of(
                                    "tweetCount", extractNumber(tweetCount),
                                    "category", category,
                                    "timestamp", LocalDateTime.now()
                                ));
                            
                            // Only add trends that appear in multiple locations or have significant tweet count
                            if (location.equals("worldwide") || trendHistory.get(topic).size() > 1) {
                                TrendData trendData = new TrendData();
                                trendData.setAnalysisTimestamp(LocalDateTime.now());
                                trendData.setTopic(topic);
                                trendData.setCategory(category.isEmpty() ? "Social Media" : category);
                                
                                // Calculate trend score based on tweet count and location presence
                                double baseScore = calculateTwitterScore(tweetCount);
                                double locationBonus = trendHistory.get(topic).size() * 0.1; // Bonus for appearing in multiple locations
                                trendData.setTrendScore(Math.min(1.0, baseScore + locationBonus));
                                
                                // Higher confidence for trends appearing in multiple locations
                                trendData.setConfidenceScore(0.75 + (trendHistory.get(topic).size() * 0.05));
                                
                                // Get related tweets for context
                                List<Map<String, String>> relatedTweets = scrapeRelatedTweets(topic);
                                
                                // Calculate trend pattern
                                List<Double> tweetHistory = new ArrayList<>();
                                tweetHistory.add((double) extractNumber(tweetCount));
                                
                                // Add historical data from other locations
                                trendHistory.get(topic).values().stream()
                                    .map(data -> (double) ((Integer) ((Map<String, Object>) data).get("tweetCount")))
                                    .forEach(tweetHistory::add);
                                
                                trendData.setTrendPattern(detectTrendPattern(tweetHistory));
                                
                                // Set rich metadata
                                Map<String, Object> metadata = new HashMap<>();
                                metadata.put("source", "Twitter");
                                metadata.put("tweetCount", extractNumber(tweetCount));
                                metadata.put("category", category);
                                metadata.put("locations", new ArrayList<>(trendHistory.get(topic).keySet()));
                                metadata.put("tweetHistory", tweetHistory);
                                metadata.put("relatedTweets", relatedTweets);
                                trendData.setMetadata(metadata);
                                
                                trends.add(trendData);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Error processing Twitter trend: {}", e.getMessage());
                    }
                }
                
                // Small delay between location requests
                if (!location.equals("australia")) Thread.sleep(1000);
            }
        } catch (Exception e) {
            log.error("Error scraping Twitter: {}", e.getMessage());
        }
        return trends;
    }
    
    private List<Map<String, String>> scrapeRelatedTweets(String topic) {
        List<Map<String, String>> tweets = new ArrayList<>();
        try {
            String encodedTopic = URLEncoder.encode(topic, StandardCharsets.UTF_8.toString());
            Document doc = getDocumentWithRetry("https://twitter.com/search?q=" + encodedTopic + "&src=trend_click&vertical=trends");
            
            Elements tweetItems = doc.select("[data-testid='tweet']");
            for (Element tweet : tweetItems.stream().limit(5).collect(Collectors.toList())) {
                try {
                    Map<String, String> tweetData = new HashMap<>();
                    tweetData.put("text", tweet.select("[data-testid='tweetText']").text());
                    tweetData.put("author", tweet.select("[data-testid='User-Name']").text());
                    tweetData.put("engagement", tweet.select(".r-1q142lx").text()); // Likes, retweets, etc.
                    tweets.add(tweetData);
                } catch (Exception e) {
                    log.warn("Error processing related tweet: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error fetching related tweets: {}", e.getMessage());
        }
        return tweets;
    }

    public List<TrendData> scrapeGoogleTrends() {
        List<TrendData> trends = new ArrayList<>();
        try {
            // Scrape daily trends
            trends.addAll(scrapeDailyTrends());
            
            // Scrape realtime trends
            trends.addAll(scrapeRealtimeTrends());
            
            // Add historical data where possible
            for (TrendData trend : trends) {
                try {
                    addHistoricalData(trend);
                } catch (Exception e) {
                    log.warn("Error adding historical data for trend {}: {}", trend.getTopic(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error scraping Google Trends: {}", e.getMessage());
        }
        return trends;
    }

    private List<TrendData> scrapeDailyTrends() {
        List<TrendData> trends = new ArrayList<>();
        try {
            // Google Trends Daily Search Trends
            Document doc = getDocumentWithRetry("https://trends.google.com/trends/trendingsearches/daily?geo=US");
            Elements trendItems = doc.select(".feed-item-header");
            
            for (Element trend : trendItems) {
                try {
                    String title = trend.select(".title").text();
                    String searchCount = trend.select(".search-count-title").text();
                    String category = trend.select(".source-and-time").text();
                    
                    // Get related articles for better context
                    Elements articles = trend.parent().select(".article");
                    List<Map<String, String>> relatedArticles = new ArrayList<>();
                    for (Element article : articles) {
                        Map<String, String> articleData = new HashMap<>();
                        articleData.put("title", article.select(".article-title").text());
                        articleData.put("source", article.select(".source-and-time").text());
                        articleData.put("snippet", article.select(".snippet").text());
                        relatedArticles.add(articleData);
                    }
                    
                    TrendData trendData = new TrendData();
                    trendData.setAnalysisTimestamp(LocalDateTime.now());
                    trendData.setTopic(title);
                    trendData.setCategory("Search Trends");
                    trendData.setTrendScore(calculateGoogleTrendScore(searchCount));
                    trendData.setConfidenceScore(0.9); // Web data is usually reliable
                    
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", "Google Trends Daily");
                    metadata.put("searchVolume", extractNumber(searchCount));
                    metadata.put("category", category);
                    metadata.put("relatedArticles", relatedArticles);
                    trendData.setMetadata(metadata);
                    
                    trends.add(trendData);
                } catch (Exception e) {
                    log.warn("Error processing Google daily trend: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error scraping Google daily trends: {}", e.getMessage());
        }
        return trends;
    }

    private List<TrendData> scrapeRealtimeTrends() {
        List<TrendData> trends = new ArrayList<>();
        try {
            // Google Trends Realtime Trends
            Document doc = getDocumentWithRetry("https://trends.google.com/trends/trendingsearches/realtime?geo=US&category=all");
            Elements trendItems = doc.select(".trending-item");
            
            for (Element trend : trendItems) {
                try {
                    String title = trend.select(".title").text();
                    String traffic = trend.select(".traffic").text();
                    String category = trend.select(".category").text();
                    
                    TrendData trendData = new TrendData();
                    trendData.setAnalysisTimestamp(LocalDateTime.now());
                    trendData.setTopic(title);
                    trendData.setCategory(category.isEmpty() ? "Realtime Trends" : category);
                    trendData.setTrendScore(calculateRealtimeTrendScore(traffic));
                    trendData.setConfidenceScore(0.95); // Realtime data is very reliable
                    trendData.setTrendPattern(TrendPattern.VOLATILE_RISE); // Realtime trends are usually rising sharply
                    
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("source", "Google Trends Realtime");
                    metadata.put("traffic", traffic);
                    metadata.put("category", category);
                    trendData.setMetadata(metadata);
                    
                    trends.add(trendData);
                } catch (Exception e) {
                    log.warn("Error processing Google realtime trend: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error scraping Google realtime trends: {}", e.getMessage());
        }
        return trends;
    }

    private void addHistoricalData(TrendData trend) {
        try {
            // Try to get historical data from the interest over time graph
            String encodedTopic = URLEncoder.encode(trend.getTopic(), StandardCharsets.UTF_8.toString());
            Document doc = getDocumentWithRetry(
                "https://trends.google.com/trends/explore?date=now%207-d&geo=US&q=" + encodedTopic);
            
            // Extract graph data
            Elements graphData = doc.select(".trends-graph");
            if (!graphData.isEmpty()) {
                List<Double> historicalValues = new ArrayList<>();
                // Parse the graph data points
                Elements dataPoints = graphData.select(".point");
                for (Element point : dataPoints) {
                    try {
                        double value = Double.parseDouble(point.attr("data-value"));
                        historicalValues.add(value);
                    } catch (NumberFormatException e) {
                        // Skip invalid data points
                    }
                }
                
                if (!historicalValues.isEmpty()) {
                    trend.getMetadata().put("historicalData", historicalValues);
                    trend.setTrendPattern(detectTrendPattern(historicalValues));
                }
            }
        } catch (Exception e) {
            log.warn("Error adding historical data: {}", e.getMessage());
        }
    }

    private TrendPattern detectTrendPattern(List<Double> values) {
        if (values == null || values.size() < 2) {
            return TrendPattern.STEADY_RISE;
        }
        
        double firstValue = values.get(0);
        double lastValue = values.get(values.size() - 1);
        double maxValue = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double minValue = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        
        if (lastValue > firstValue * 1.5) {
            return TrendPattern.VOLATILE_RISE;
        } else if (lastValue < firstValue * 0.5) {
            return TrendPattern.VOLATILE_DECLINE;
        } else if (maxValue > lastValue * 1.5 && maxValue > firstValue * 1.5) {
            return TrendPattern.VOLATILE_RISE;
        } else if (lastValue > firstValue) {
            return TrendPattern.STEADY_RISE;
        } else if (lastValue < firstValue) {
            return TrendPattern.STEADY_DECLINE;
        } else {
            return TrendPattern.CONSOLIDATION;
        }
    }

    private double calculateTwitterScore(String tweetCount) {
        int count = extractNumber(tweetCount);
        return Math.min(count / 100000.0, 1.0); // Normalize to 0-1 range
    }

    private double calculateGoogleTrendScore(String searchCount) {
        int searches = extractNumber(searchCount);
        return Math.min(searches / 1000000.0, 1.0); // Normalize to 0-1 range
    }

    private double calculateScore(String points, String comments) {
        int pointsNum = extractNumber(points);
        int commentsNum = extractNumber(comments);
        return (pointsNum * 0.7 + commentsNum * 0.3) / 100.0; // Normalize to 0-1 range
    }

    private double calculateGitHubScore(String stars) {
        return Math.min(1.0, extractNumber(stars) / 1000.0); // Normalize to 0-1 range
    }

    private double calculateStackOverflowScore(String votes, String views) {
        int votesNum = extractNumber(votes);
        int viewsNum = extractNumber(views);
        return (votesNum * 0.6 + viewsNum * 0.4) / 1000.0; // Normalize to 0-1 range
    }

    private double calculateRealtimeTrendScore(String traffic) {
        int trafficNum = extractNumber(traffic);
        return Math.min(1.0, trafficNum / 10000.0); // Normalize to 0-1 range
    }

    private int extractNumber(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(str.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
