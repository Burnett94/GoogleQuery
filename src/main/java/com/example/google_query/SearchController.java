package com.example.google_query;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 搜尋控制器
 * 提供 Google 自訂搜尋 API 的 REST 端點以及子網頁搜尋功能
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    @Value("${google.api.key}")
    private String apiKey;

    @Value("${google.cx}")
    private String cx;

    private final RestTemplate restTemplate;

    public SearchController() {
        this.restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().stream()
                .filter(converter -> converter instanceof StringHttpMessageConverter)
                .forEach(converter -> {
                    StringHttpMessageConverter stringConverter = (StringHttpMessageConverter) converter;
                    stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
                });
    }

    public ArrayList<SearchItem> merge(ArrayList<SearchItem> left, ArrayList<SearchItem> right) {
        ArrayList<SearchItem> result = new ArrayList<>();
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            if (left.get(i).getScore() > right.get(j).getScore()) {
                result.add(left.get(i));
                i++;
            } else {
                result.add(right.get(j));
                j++;
            }
        }

        while (i < left.size()) {
            result.add(left.get(i));
            i++;
        }
        while (j < right.size()) {
            result.add(right.get(j));
            j++;
        }
        return result;
    }

    public ArrayList<SearchItem> mergeSort(ArrayList<SearchItem> items) {
        if (items.size() <= 1) {
            return items;
        } else {
            int mid = items.size() / 2;
            ArrayList<SearchItem> left = mergeSort(new ArrayList<>(items.subList(0, mid)));
            ArrayList<SearchItem> right = mergeSort(new ArrayList<>(items.subList(mid, items.size())));
            return merge(left, right);
        }
    }

    private String enhanceQueryForRestaurants(String originalQuery) {
        boolean hasChinese = originalQuery.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
        if (!hasChinese) {
            return originalQuery;
        }
        
        String[] restaurantKeywords = {"餐廳", "美食", "小吃", "推薦", "必吃", "食記", "料理", "菜單"};
        boolean hasRestaurantKeyword = false;
        for (String keyword : restaurantKeywords) {
            if (originalQuery.contains(keyword)) {
                hasRestaurantKeyword = true;
                break;
            }
        }

        if (hasRestaurantKeyword) {
            return originalQuery;
        }
        return originalQuery + " 餐廳 推薦";
    }

    private Map<String, Float> initializeKeywordMap() {
        Map<String, Float> keywordMap = new HashMap<>();

        // --- 業配/廣告訊號 (負分) ---
        keywordMap.put("廣宣", -10f);
        keywordMap.put("邀約", -5f);
        keywordMap.put("體驗", -2f);
        keywordMap.put("折扣碼", -5f);
        keywordMap.put("完全沒有雷", -10f);
        keywordMap.put("每一道都讓人驚豔", -5f);
        keywordMap.put("收藏起來", -3f);
        keywordMap.put("下次聚餐就選這間", -3f);
        keywordMap.put("留言告訴我你最愛哪一道", -10f);
        keywordMap.put("留言告訴我你最愛哪一間", -10f);
        keywordMap.put("留言告訴我你最喜歡哪一間", -10f);
        keywordMap.put("每個人口味不同", -2f);
        keywordMap.put("每個人喜好不同", -2f);

        // --- 真實負評/平價訊號 (大幅加分) ---
        keywordMap.put("難吃", 1.5f);
        keywordMap.put("不推", 1.5f);
        keywordMap.put("抱怨", 2f);
        keywordMap.put("雷", 1f);
        keywordMap.put("盤", 1f);
        keywordMap.put("貴", 1.5f);
        keywordMap.put("CP值低", 2f);
        keywordMap.put("失望", 1.5f);
        keywordMap.put("不會再來", 2f);
        keywordMap.put("不會特別來", 2f);
        keywordMap.put("不會特地來", 2f);
        keywordMap.put("不用特別來", 2f);
        keywordMap.put("不用特地來", 2f);
        keywordMap.put("不用特別排", 2f);
        keywordMap.put("我家", 1.5f);
        keywordMap.put("家", 1f);
        keywordMap.put("至少", 1.5f);
        keywordMap.put("蚊子", 1.5f);
        keywordMap.put("XD", 1f);

        // --- 真實體驗訊號 (中度加分) ---
        keywordMap.put("普通", 2f);
        keywordMap.put("還行", 0.5f);
        keywordMap.put("排隊", 0.3f);
        keywordMap.put("排隊時間", 2f);
        keywordMap.put("等待時間", 2f);
        keywordMap.put("等很久", 1f);
        keywordMap.put("態度", 0.5f);
        keywordMap.put("回訪", 1f);

        // --- PTT 鄉民用語 (高真實度的情緒表達) ---
        keywordMap.put("大推", 0.6f);
        keywordMap.put("神", 1f);
        keywordMap.put("屌打", 0.9f);
        keywordMap.put("頂", 0.8f);

        // --- 一般正面體驗 (中度加分，需小心業配混用) ---
        keywordMap.put("驚艷", 0.5f);
        keywordMap.put("CP值高", 0.6f);
        keywordMap.put("吃得飽", 0.5f);
        keywordMap.put("不錯", 0.5f);
        keywordMap.put("喜歡", 0.3f);
        keywordMap.put("好喝", 0.3f);
        keywordMap.put("好吃", 0.3f);

        return keywordMap;
    }

    /**
     * 子網頁搜尋 API
     * 接收一個 parentUrl，抓取其底下的連結並進行評分排序
     */
    @GetMapping("/subsearch")
    public ArrayList<SearchItem> subSearch(@RequestParam(value = "url") String parentUrl) {
        logger.info("============== 開始子網頁搜尋 ==============");
        logger.info("母網頁: {}", parentUrl);
        
        ArrayList<SearchItem> subItems = new ArrayList<>();
        
        // 驗證 URL
        if(parentUrl == null || parentUrl.isEmpty()) {
            return subItems;
        }
        if(!parentUrl.startsWith("http")) {
            parentUrl = "https://" + parentUrl;
        }

        try {
            // 1. 抓取母網頁並分析連結
            WordCounter parentCounter = new WordCounter(parentUrl);
            Set<String> links = parentCounter.getHyperlinks();
            
            logger.info("在母網頁中找到 {} 個連結", links.size());

            // 2. 限制處理的子網頁數量 (避免請求太久導致 Timeout)
            int limit = 10; 
            int count = 0;

            Map<String, Float> keywordMap = initializeKeywordMap();
            
            for (String link : links) {
                if (count >= limit) break;
                // 排除連回母網頁自己的連結，或太短的連結
                if (link.equals(parentUrl) || link.length() < 10) continue;

                SearchItem item = new SearchItem();
                item.setLink(link);
                item.setTitle("子網頁-" + (count + 1)); 
                
                // 3. 計算該子網頁的分數
                try {
                    WordCounter childCounter = new WordCounter(link);
                    double totalScore = 0.0;
                    int matchedKeywordCount = 0;

                    for (Map.Entry<String, Float> entry : keywordMap.entrySet()) {
                        String keyword = entry.getKey();
                        float weight = entry.getValue();
                        
                        try {
                            int kCount = childCounter.countKeyword(keyword);
                            if (kCount > 0) {
                                totalScore += kCount * weight;
                                matchedKeywordCount++;
                            }
                        } catch (Exception e) {
                           // 忽略單一關鍵字錯誤
                        }
                    }
                    
                    // 計算平均分
                    double finalScore = matchedKeywordCount > 0 ? totalScore / matchedKeywordCount : 0.0;
                    item.setScore(finalScore);
                    
                    // --- 即時 Log 顯示進度 ---
                    logger.info("[{}/{}] 處理中: 分數={}\t網址={}", 
                        count + 1, limit, String.format("%.2f", finalScore), link);

                    subItems.add(item);
                    count++;

                } catch (Exception e) {
                    logger.warn("無法讀取子網頁: {} ({})", link, e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("子網頁搜尋發生錯誤: ", e);
        }

        // 4. 排序
        ArrayList<SearchItem> sortedList = mergeSort(subItems);

        // --- 在 Terminal 顯示最終排行榜 ---
        logger.info("\n\n");
        logger.info("============== 子網頁分數排行榜 ==============");
        if (sortedList.isEmpty()) {
            logger.info("沒有找到有效的子網頁或無法計算分數。");
        } else {
            for (int i = 0; i < sortedList.size(); i++) {
                SearchItem item = sortedList.get(i);
                // 格式：排名. [分數] 網址
                logger.info("No.{}\t[{}]\t{}", 
                    i + 1, 
                    String.format("%.2f", item.getScore()), 
                    item.getLink());
            }
        }
        logger.info("==========================================\n");

        return sortedList;
    }

    /**
     * 主搜尋端點
     */
    @GetMapping("/search")
    public ArrayList<SearchItem> search(@RequestParam(value = "q", required = false) String query) {
        if (query != null) {
            logger.info("收到搜尋查詢: {}", query);
        }

        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String finalQuery = enhanceQueryForRestaurants(query.trim());
        String url = "https://www.googleapis.com/customsearch/v1";

        String apiUrl = UriComponentsBuilder.fromUriString(url)
                .queryParam("key", apiKey)
                .queryParam("cx", cx)
                .queryParam("q", finalQuery)
                .queryParam("num", 10)
                .build()
                .toUriString();

        try {
            GoogleSearchResponse response = restTemplate.getForObject(apiUrl, GoogleSearchResponse.class);

            if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
                return new ArrayList<>();
            }

            ArrayList<SearchItem> items = new ArrayList<>(response.getItems());
            Map<String, Float> keywordMap = initializeKeywordMap();
            ArrayList<SearchItem> validItems = new ArrayList<>();

            for (SearchItem item : items) {
                try {
                    WordCounter counter = new WordCounter(item.getLink());
                    double totalScore = 0.0;
                    int matchedKeywordCount = 0;

                    for (Map.Entry<String, Float> entry : keywordMap.entrySet()) {
                        try {
                            int count = counter.countKeyword(entry.getKey());
                            if (count > 0) {
                                totalScore += count * entry.getValue();
                                matchedKeywordCount++;
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    }

                    double finalScore = matchedKeywordCount > 0 ? totalScore / matchedKeywordCount : 0.0;
                    item.setScore(finalScore);
                    validItems.add(item);

                } catch (Exception e) {
                    item.setScore(0.0);
                    validItems.add(item);
                }
            }

            ArrayList<SearchItem> sortedList = mergeSort(validItems);
            if (sortedList.size() > 10) {
                sortedList = new ArrayList<>(sortedList.subList(0, 10));
            }

            return sortedList;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}