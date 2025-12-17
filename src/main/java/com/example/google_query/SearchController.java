package com.example.google_query;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SearchController {

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

    // --- Merge Sort 排序 ---
    public ArrayList<SearchItem> mergeSort(ArrayList<SearchItem> items) {
        if (items.size() <= 1) return items;
        int mid = items.size() / 2;
        ArrayList<SearchItem> left = mergeSort(new ArrayList<>(items.subList(0, mid)));
        ArrayList<SearchItem> right = mergeSort(new ArrayList<>(items.subList(mid, items.size())));
        
        ArrayList<SearchItem> result = new ArrayList<>();
        int i = 0, j = 0;
        while (i < left.size() && j < right.size()) {
            if (left.get(i).getScore() > right.get(j).getScore()) result.add(left.get(i++));
            else result.add(right.get(j++));
        }
        while (i < left.size()) result.add(left.get(i++));
        while (j < right.size()) result.add(right.get(j++));
        return result;
    }

    // --- 設定關鍵字權重 ---
    private Map<String, Float> initializeKeywordMap() {
        Map<String, Float> k = new HashMap<>();
        k.put("廣宣", -10f); k.put("邀約", -5f); k.put("折扣碼", -5f);
        k.put("好吃", 1f); k.put("大推", 2f); k.put("回訪", 2f); k.put("排隊", 1.5f);
        k.put("難吃", 3f); k.put("不推", 3f); k.put("普通", 1f);
        return k;
    }

    // --- 子網頁搜尋 API (單獨使用) ---
    @GetMapping("/subsearch")
    public ArrayList<SearchItem> subSearch(@RequestParam(value = "url") String parentUrl) {
        System.out.println("\n\n🔥🔥🔥 [SubSearch] 啟動: " + parentUrl);
        return performDeepDive(parentUrl, 10); // 單獨呼叫時，抓 10 個子網頁
    }

    // --- 主搜尋 API (現在包含深度挖掘功能) ---
    @GetMapping("/search")
    public ArrayList<SearchItem> search(@RequestParam(value = "q", required = false) String query) {
        System.out.println("\n🔥🔥🔥 [MainSearch] 查詢: " + query);
        if (query == null || query.trim().isEmpty()) return new ArrayList<>();

        String url = "https://www.googleapis.com/customsearch/v1";
        String apiUrl = UriComponentsBuilder.fromUriString(url)
                .queryParam("key", apiKey)
                .queryParam("cx", cx)
                .queryParam("q", query + " 評價")
                .queryParam("num", 5) // ⚠️ 為了速度，我們先抓前 5 筆就好 (不然會跑太久)
                .build().toUriString();

        try {
            GoogleSearchResponse response = restTemplate.getForObject(apiUrl, GoogleSearchResponse.class);
            if (response == null || response.getItems() == null) return new ArrayList<>();

            ArrayList<SearchItem> items = new ArrayList<>(response.getItems());
            Map<String, Float> keywordMap = initializeKeywordMap();

            System.out.println("🔎 找到 " + items.size() + " 筆搜尋結果，開始進行深度評分 (包含子網頁)...");

            for (SearchItem item : items) {
                try {
                    System.out.println("\n   ➤ [主網頁] 分析: " + item.getTitle());
                    
                    // 1. 先算主網頁分數
                    WordCounter mainCounter = new WordCounter(item.getLink());
                    double mainScore = calculateScore(mainCounter, keywordMap, "Main");
                    
                    // 2. 🔥【重點修改】自動往下挖！抓取該主網頁底下的連結
                    Set<String> subLinks = mainCounter.getHyperlinks();
                    System.out.println("      (發現 " + subLinks.size() + " 個子連結，隨機抽樣分析 3 個...)");

                    int subCount = 0;
                    double subTotalScore = 0;
                    
                    for (String subLink : subLinks) {
                        if (subCount >= 3) break; // 每個結果只挖 3 個子網頁，避免跑太久
                        if (subLink.equals(item.getLink())) continue;

                        try {
                            WordCounter subCounter = new WordCounter(subLink);
                            // 這裡會印出 └── [子網頁]
                            double sScore = calculateScore(subCounter, keywordMap, "Sub");
                            subTotalScore += sScore;
                            subCount++;
                        } catch (Exception e) {
                            // 忽略子網頁讀取錯誤
                        }
                    }

                    // 3. 整合分數 (主網頁 + 子網頁平均)
                    double finalScore = mainScore;
                    if (subCount > 0) {
                        finalScore = (mainScore + (subTotalScore / subCount)) / 2; // 取平均
                        System.out.println("      => 修正後總分 (含子網頁): " + String.format("%.2f", finalScore));
                    }
                    
                    item.setScore(finalScore);

                } catch (Exception e) {
                    System.out.println("   [略過] 無法讀取: " + item.getLink());
                    item.setScore(0.0);
                }
            }
            
            ArrayList<SearchItem> sorted = mergeSort(items);
            printRanking(sorted);
            return sorted;

        } catch (Exception e) {
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    // --- 輔助：執行深度挖掘邏輯 (給 SubSearch 用) ---
    private ArrayList<SearchItem> performDeepDive(String parentUrl, int limit) {
        ArrayList<SearchItem> subItems = new ArrayList<>();
        if (parentUrl == null || !parentUrl.startsWith("http")) return subItems;

        try {
            WordCounter parentCounter = new WordCounter(parentUrl);
            Set<String> links = parentCounter.getHyperlinks();
            Map<String, Float> keywordMap = initializeKeywordMap();

            int count = 0;
            for (String link : links) {
                if (count >= limit) break;
                if (link.equals(parentUrl)) continue;

                SearchItem item = new SearchItem();
                item.setLink(link);
                item.setTitle("SubPage-" + (count + 1));
                
                System.out.println("   [" + (count+1) + "] 爬取: " + link);
                try {
                    WordCounter childCounter = new WordCounter(link);
                    item.setScore(calculateScore(childCounter, keywordMap, "Sub"));
                    subItems.add(item);
                    count++;
                } catch (Exception e) {}
            }
        } catch (Exception e) { e.printStackTrace(); }
        
        ArrayList<SearchItem> sorted = mergeSort(subItems);
        printRanking(sorted);
        return sorted;
    }

    // --- 計算分數並印出詳細 Log (包含縮排) ---
    private double calculateScore(WordCounter counter, Map<String, Float> keywords, String type) throws IOException {
        double total = 0;
        int hits = 0;
        StringBuilder sb = new StringBuilder();
        
        // 根據類型決定縮排和前綴
        String prefix = "Sub".equals(type) ? "      └── [子網頁命中] " : "      [主網頁命中] ";

        for (Map.Entry<String, Float> entry : keywords.entrySet()) {
            try {
                int c = counter.countKeyword(entry.getKey());
                if (c > 0) {
                    total += c * entry.getValue();
                    hits++;
                    // 印出每一條命中的關鍵字
                    System.out.println(prefix + entry.getKey() + " x" + c + " (+" + (c*entry.getValue()) + ")");
                }
            } catch (Exception e) {}
        }
        
        double finalScore = (hits > 0) ? (total / hits) : 0.0;
        
        // 只有在真的有命中時，或是在主網頁分析時才印出總結，避免畫面太亂
        if (hits > 0) {
            String indent = "Sub".equals(type) ? "          " : "      ";
            System.out.println(indent + "=> 得分: " + String.format("%.2f", finalScore));
        }
        
        return finalScore;
    }

    private void printRanking(ArrayList<SearchItem> items) {
        System.out.println("\n🏆 最終排行榜:");
        System.out.println("===============================");
        for (int i = 0; i < items.size(); i++) {
            System.out.printf("No.%-2d [%6s] %s\n", (i+1), String.format("%.2f", items.get(i).getScore()), items.get(i).getTitle());
        }
        System.out.println("===============================\n");
    }
}