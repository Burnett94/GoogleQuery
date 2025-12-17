package com.example.google_query;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

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
 * 提供 Google 自訂搜尋 API 的 REST 端點
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

    /**
     * 建構函數：配置 RestTemplate 以支援 UTF-8 編碼
     */
    public SearchController() {
        this.restTemplate = new RestTemplate();
        // 設置 StringHttpMessageConverter 使用 UTF-8 編碼
        restTemplate.getMessageConverters().stream()
                .filter(converter -> converter instanceof StringHttpMessageConverter)
                .forEach(converter -> {
                    StringHttpMessageConverter stringConverter = (StringHttpMessageConverter) converter;
                    stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
                });
    }

    /**
     * 合併兩個已排序的列表
     */
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

        // 把剩下的加進去
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

    /**
     * 合併排序演算法
     */
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

    /**
     * 增強查詢字串，自動添加餐廳相關關鍵字
     * 如果查詢中已經包含餐廳相關詞彙，則不重複添加
     * 注意：目前僅對中文查詢添加中文關鍵字，避免對其他語言查詢造成干擾
     */
    private String enhanceQueryForRestaurants(String originalQuery) {
        // 檢查是否包含中文字符
        boolean hasChinese = originalQuery.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
        
        // 如果查詢不是中文，直接返回原始查詢（避免添加不相關的中文關鍵字）
        if (!hasChinese) {
            return originalQuery;
        }
        
        String[] restaurantKeywords = {"餐廳", "美食", "小吃", "推薦", "必吃", "食記", "料理", "菜單"};

        // 檢查原始查詢是否已經包含餐廳相關關鍵字
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

    /**
     * 初始化關鍵字列表及其權重 (使用 Map 結構：關鍵字名稱 -> 權重)
     */
    private Map<String, Float> initializeKeywordMap() {
        Map<String, Float> keywordMap = new HashMap<>();

        // --- 業配/廣告訊號 (負分) ---
        keywordMap.put("廣宣", -100f);
        keywordMap.put("邀約", -50f);
        keywordMap.put("體驗", -20f);
        keywordMap.put("折扣碼", -50f);
        keywordMap.put("完全沒有雷", -100f);
        keywordMap.put("每一道都讓人驚豔", -50f);
        keywordMap.put("收藏起來", -30f);
        keywordMap.put("下次聚餐就選這間", -30f);
        keywordMap.put("留言告訴我你最愛哪一道", -100f);
        keywordMap.put("留言告訴我你最愛哪一間", -100f);
        keywordMap.put("留言告訴我你最喜歡哪一間", -100f);
        keywordMap.put("每個人口味不同", -20f);
        keywordMap.put("每個人喜好不同", -20f);

        // --- 真實負評/平價訊號 (大幅加分) ---
        keywordMap.put("難吃", 15f);
        keywordMap.put("不推", 15f);
        keywordMap.put("抱怨", 20f);
        keywordMap.put("雷", 10f);
        keywordMap.put("盤", 10f);
        keywordMap.put("貴", 15f);
        keywordMap.put("CP值低", 20f);
        keywordMap.put("失望", 15f);
        keywordMap.put("不會再來", 20f);
        keywordMap.put("不會特別來", 20f);
        keywordMap.put("不會特地來", 20f);
        keywordMap.put("不用特別來", 20f);
        keywordMap.put("不用特地來", 20f);
        keywordMap.put("不用特別排", 20f);
        keywordMap.put("我家", 15f);
        keywordMap.put("家", 10f);
        keywordMap.put("至少", 15f);
        keywordMap.put("蚊子", 15f);
        keywordMap.put("XD", 10f);

        // --- 真實體驗訊號 (中度加分) ---
        keywordMap.put("普通", 20f);
        keywordMap.put("還行", 5f);
        keywordMap.put("排隊", 3f);
        keywordMap.put("排隊時間", 20f);
        keywordMap.put("等待時間", 20f);
        keywordMap.put("等很久", 10f);
        keywordMap.put("態度", 5f);
        keywordMap.put("回訪", 10f);

        // --- PTT 鄉民用語 (高真實度的情緒表達) ---
        keywordMap.put("大推", 6f);
        keywordMap.put("神", 10f);
        keywordMap.put("屌打", 9f);
        keywordMap.put("頂", 8f);

        // --- 一般正面體驗 (中度加分，需小心業配混用) ---
        keywordMap.put("驚艷", 5f);
        keywordMap.put("CP值高", 6f);
        keywordMap.put("吃得飽", 5f);
        keywordMap.put("不錯", 5f);
        keywordMap.put("喜歡", 3f);
        keywordMap.put("好喝", 3f);
        keywordMap.put("好吃", 3f);

        return keywordMap;
    }

    /**
     * 搜尋端點
     * 接收查詢參數，呼叫 Google Custom Search API，並對結果進行評分和排序
     */
    @GetMapping("/search")
    public ArrayList<SearchItem> search(@RequestParam(value = "q", required = false) String query) {
        // 記錄接收到的查詢參數（用於除錯編碼）
        if (query != null) {
            logger.info("收到搜尋查詢: {}", query);
            logger.info("查詢參數長度: {} 字符", query.length());
            boolean hasChinese = query.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
            boolean hasNonASCII = query.chars().anyMatch(ch -> ch > 127);
            logger.info("包含中文字符: {}, 包含非ASCII字符: {}", hasChinese, hasNonASCII);
        }

        if (query == null || query.trim().isEmpty()) {
            logger.warn("查詢參數為空");
            return new ArrayList<>();
        }

        // 增強查詢：自動添加餐廳相關關鍵字
        String enhancedQuery = enhanceQueryForRestaurants(query.trim());
        String finalQuery = enhancedQuery;
        
        logger.info("原始查詢: {}, 增強後查詢: {}", query, finalQuery);

        // Google Custom Search API 的 URL
        String url = "https://www.googleapis.com/customsearch/v1";

        // 建立帶有查詢參數的 URL，確保正確編碼（支援中文）
        String apiUrl = UriComponentsBuilder.fromUriString(url)
                .queryParam("key", apiKey)
                .queryParam("cx", cx)
                .queryParam("q", finalQuery)
                .queryParam("num", 10)
                .build()
                .toUriString();

        logger.info("發送到 Google API 的 URL: {}", apiUrl.replace(apiKey, "API_KEY_HIDDEN"));

        try {
            logger.info("開始呼叫 Google Custom Search API...");
            // 呼叫 Google API 並指定回應應映射到我們的 POJO
            GoogleSearchResponse response = restTemplate.getForObject(
                    apiUrl,
                    GoogleSearchResponse.class);

            if (response == null) {
                logger.error("Google API 返回 null 回應");
                return new ArrayList<>();
            }
            
            if (response.getItems() == null) {
                logger.error("Google API 回應的 items 為 null");
                return new ArrayList<>();
            }
            
            if (response.getItems().isEmpty()) {
                logger.warn("Google API 返回空結果列表");
                return new ArrayList<>();
            }

            logger.info("收到 {} 筆原始搜尋結果", response.getItems().size());
            // 記錄前幾筆結果的 URL 以便除錯
            int debugCount = Math.min(3, response.getItems().size());
            for (int i = 0; i < debugCount; i++) {
                logger.info("  結果 {}: {} - {}", i + 1, response.getItems().get(i).getTitle(), response.getItems().get(i).getLink());
            }

            // 不再進行 PTT 過濾，保留所有搜尋結果
            ArrayList<SearchItem> filteredItems = new ArrayList<>(response.getItems());
            logger.info("保留所有 {} 筆搜尋結果（已取消 PTT 限制）", filteredItems.size());

            // 初始化關鍵字 Map (關鍵字名稱 -> 權重)
            Map<String, Float> keywordMap = initializeKeywordMap();

            // 計算分數（只對過濾後的結果計算）
            logger.info("開始處理 {} 筆搜尋結果，計算分數...", filteredItems.size());
            ArrayList<SearchItem> validItems = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;
            
            for (int idx = 0; idx < filteredItems.size(); idx++) {
                SearchItem item = filteredItems.get(idx);
                String webURL = item.getLink();
                String title = item.getTitle() != null ? item.getTitle() : "無標題";

                logger.info("[{}/{}] 處理: {}", idx + 1, filteredItems.size(), title);
                
                try {
                    WordCounter counter = new WordCounter(webURL);
                    double totalScore = 0.0;
                    int matchedKeywordCount = 0; // 記錄匹配到的關鍵字數量

                    StringBuilder sb = new StringBuilder();
                    Map<String, Integer> matchedKeywords = new HashMap<>(); // 記錄匹配到的關鍵字名稱和次數 (關鍵字名稱 -> 出現次數)
                    
                    // 遍歷關鍵字 Map
                    for (Map.Entry<String, Float> entry : keywordMap.entrySet()) {
                        String keywordName = entry.getKey();
                        float keywordWeight = entry.getValue();
                        
                        try {
                            int count = counter.countKeyword(keywordName);
                            double contribution = count * keywordWeight;

                            if (count > 0) {
                                matchedKeywordCount++; // 如果有匹配到，增加計數
                                matchedKeywords.put(keywordName, count); // 記錄關鍵字名稱和出現次數
                                String detail = String.format("%s:%d*%.1f=%.1f",
                                        keywordName, count, keywordWeight, contribution);
                                if (sb.length() > 0) {
                                    sb.append(", ");
                                }
                                sb.append(detail);
                            }

                            totalScore += contribution;
                        } catch (IOException e) {
                            logger.debug("計算關鍵字 '{}' 時發生錯誤: {}", keywordName, e.getMessage());
                        }
                    }

                    // 計算平均分數：總分除以匹配到的關鍵字數量
                    double finalScore = totalScore;
                    if (matchedKeywordCount > 0) {
                        finalScore = totalScore / matchedKeywordCount;
                        logger.info("  原始總分: {}, 匹配關鍵字數: {}, 匹配到的關鍵字: {}, 平均分數: {}", 
                                String.format("%.1f", totalScore), matchedKeywordCount, matchedKeywords.toString(), String.format("%.1f", finalScore));
                    } else {
                        logger.info("  原始總分: {}, 匹配關鍵字數: 0, 平均分數: {}", 
                                String.format("%.1f", totalScore), String.format("%.1f", finalScore));
                        logger.warn("  警告：此網頁沒有匹配到任何關鍵字！");
                    }

                    item.setScore(finalScore);
                    successCount++;
                    String scoreStr = String.format("%.1f", finalScore);
                    logger.info("  成功計算分數（平均分）: {}", scoreStr);
                    
                    // 只有成功處理的項目才加入列表
                    validItems.add(item);
                } catch (Exception e) {
                    failCount++;
                    logger.warn("  無法處理項目 (URL: {}): {}", webURL, e.getClass().getSimpleName() + ": " + e.getMessage());
                    // 即使無法訪問網頁，仍然保留該項目，但分數為 0（讓用戶至少能看到搜尋結果）
                    item.setScore(0.0);
                    validItems.add(item);
                }
            }
            
            logger.info("處理完成: 成功 {} 筆，失敗 {} 筆，總共 {} 筆", successCount, failCount, validItems.size());

            if (validItems.isEmpty()) {
                logger.warn("處理後沒有有效結果（共處理 {} 筆，全部失敗）", filteredItems.size());
                // 如果所有結果都無法處理，至少返回原始結果（不計算分數）
                for (SearchItem item : filteredItems) {
                    item.setScore(0.0);
                    validItems.add(item);
                }
                logger.info("返回原始搜尋結果（未計算分數）");
            }
            
            // 使用 MergeSort 排序
            ArrayList<SearchItem> sortedList = mergeSort(validItems);

            // 確保只返回最多 10 筆結果
            if (sortedList.size() > 10) {
                sortedList = new ArrayList<>(sortedList.subList(0, 10));
            }

            logger.info("排序完成，返回 {} 筆結果（限制為 10 筆）", sortedList.size());

            return sortedList;

        } catch (Exception e) {
            logger.error("搜尋時發生錯誤: ", e);
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
}
