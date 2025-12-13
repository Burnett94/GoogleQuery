package com.example.google_query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.CrossOrigin; // 引入 CORS
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.io.IOException;

@RestController
@RequestMapping("/api")
// 允許來自所有來源的跨域請求 (開發時方便，生產環境應更嚴格)
@CrossOrigin(origins = "*") 
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    // 從 application.properties 注入
    @Value("${google.api.key}")
    private String apiKey;

    @Value("${google.cx}")
    private String cx;

    private final RestTemplate restTemplate;

    // 建構函數：配置 RestTemplate 以支援 UTF-8 編碼
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
    
    //排序 MergeSort
    public ArrayList<SearchItem> Merge(ArrayList<SearchItem> left, ArrayList<SearchItem> right){
        ArrayList<SearchItem> result = new ArrayList<>();
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            if(left.get(i).getScore() > right.get(j).getScore()){
                result.add(left.get(i));
                i++;
            }
            else{
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

    public ArrayList<SearchItem> MergeSort(ArrayList<SearchItem> items){
        if (items.size() <= 1) {
            return items;
        }
        else{
            int mid = items.size() / 2;
            ArrayList<SearchItem> left = MergeSort(new ArrayList<SearchItem>(items.subList(0, mid)));
            ArrayList<SearchItem> right = MergeSort(new ArrayList<SearchItem>(items.subList(mid, items.size())));
            return Merge(left, right);
        }            
    }
    
    /**
     * 增強查詢字串，自動添加餐廳相關關鍵字
     * 如果查詢中已經包含餐廳相關詞彙，則不重複添加
     */
    private String enhanceQueryForRestaurants(String originalQuery) {
        // 餐廳相關關鍵字列表
        String[] restaurantKeywords = {"餐廳", "美食", "小吃", "推薦", "必吃", "食記", "料理", "菜單"};
        
        // 檢查原始查詢是否已經包含餐廳相關關鍵字
        boolean hasRestaurantKeyword = false;
        for (String keyword : restaurantKeywords) {
            if (originalQuery.contains(keyword)) {
                hasRestaurantKeyword = true;
                break;
            }
        }
        
        // 如果已經包含餐廳相關關鍵字，直接返回原查詢
        if (hasRestaurantKeyword) {
            return originalQuery;
        }
        
        // 否則添加餐廳相關關鍵字
        // 使用 Google 搜尋語法：用空格分隔表示 AND，用引號表示精確匹配
        // 這裡我們添加 "餐廳" 和 "推薦" 來縮小搜尋範圍
        return originalQuery + " 餐廳 推薦";
    }
    @GetMapping("/search")
    public ArrayList<SearchItem> search(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "num", defaultValue = "30") int numResults) {
        
        // 記錄接收到的查詢參數（用於除錯中文編碼）
        if (query != null) {
            logger.info("收到搜尋查詢: {}, 請求結果數量: {}", query, numResults);
            logger.info("查詢參數長度: {} 字符", query.length());
            // 檢查是否包含中文字符
            boolean hasChinese = query.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
            logger.info("包含中文字符: {}", hasChinese);
        }
        
        if (query == null || query.trim().isEmpty()) {
            logger.warn("查詢參數為空");
            return new ArrayList<>(); // 返回空列表而不是 ResponseEntity
        }
        
        // 限制結果數量在合理範圍內（Google API 最多支援 100 筆）
        if (numResults < 1) numResults = 10;
        if (numResults > 100) numResults = 100;
        
        // 增強查詢：自動添加餐廳相關關鍵字，讓搜尋結果更聚焦於餐廳
        String enhancedQuery = enhanceQueryForRestaurants(query.trim());
        logger.info("原始查詢: {}, 增強後查詢: {}", query, enhancedQuery);
        
        // 計算需要獲取多少頁（每頁 10 筆）
        int numPages = (int) Math.ceil(numResults / 10.0);
        logger.info("需要獲取 {} 頁結果（每頁 10 筆）", numPages);
        
        // 收集所有結果
        ArrayList<SearchItem> allItems = new ArrayList<>();
        
        try {
            // 循環獲取多頁結果
            for (int page = 0; page < numPages && allItems.size() < numResults; page++) {
                int startIndex = page * 10 + 1; // Google API 的 start 參數從 1 開始
                
                // Google Custom Search API 的 URL
                String url = "https://www.googleapis.com/customsearch/v1";

                // 建立帶有查詢參數的 URL，確保正確編碼（支援中文）
                String apiUrl = UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("key", apiKey)
                        .queryParam("cx", cx)
                        .queryParam("q", enhancedQuery)
                        .queryParam("start", startIndex)  // 分頁參數
                        .queryParam("num", 10)  // 每頁最多 10 筆
                        .build()
                        .toUriString();

                logger.info("發送第 {} 頁請求到 Google API (start={})", page + 1, startIndex);

                // 呼叫 Google API
                GoogleSearchResponse response = restTemplate.getForObject(
                    apiUrl, 
                    GoogleSearchResponse.class
                );
                
                if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
                    logger.info("第 {} 頁沒有更多結果", page + 1);
                    break; // 沒有更多結果，停止分頁
                }
                
                logger.info("第 {} 頁收到 {} 筆搜尋結果", page + 1, response.getItems().size());
                allItems.addAll(response.getItems());
                
                // 如果這一頁結果少於 10 筆，表示已經是最後一頁
                if (response.getItems().size() < 10) {
                    logger.info("已獲取所有可用結果");
                    break;
                }
            }
            
            if (allItems.isEmpty()) {
                logger.warn("Google API 返回空結果");
                return new ArrayList<>();
            }
            
            logger.info("總共收到 {} 筆搜尋結果", allItems.size());
            
            //建立keyword list
            ArrayList<Keyword> KeywordList = new ArrayList<>();
            KeywordList.add(new Keyword("連結", -2));
            KeywordList.add(new Keyword("合作", -2));
            KeywordList.add(new Keyword("招待", -2));
            KeywordList.add(new Keyword("折扣碼", -5));
            KeywordList.add(new Keyword("讀者優惠", -5));
            KeywordList.add(new Keyword("高CP值", -3));
            KeywordList.add(new Keyword("排隊", 2));
            KeywordList.add(new Keyword("不推", 3));
            KeywordList.add(new Keyword("普通", 3));
            KeywordList.add(new Keyword("貴", 3));
            KeywordList.add(new Keyword("餐廳", 5));
            KeywordList.add(new Keyword("小吃", 5));
            KeywordList.add(new Keyword("美食", 5));
            
            //算分
            logger.info("開始計算 {} 筆結果的分數", allItems.size());
            for (int i = 0; i < allItems.size(); i++) {
                SearchItem item = allItems.get(i);
                String webURL = item.getLink();
                
                WordCounter counter = new WordCounter(webURL);
                double totalScore = 0.0;
                
                for (int j = 0; j < KeywordList.size(); j++) {
                    try {
                        int count = counter.countKeyword(KeywordList.get(j).getName());
                        totalScore += count * KeywordList.get(j).getWeight();
                    } catch (IOException e) {
                        logger.warn("計算關鍵字 '{}' 時發生錯誤 (URL: {}): {}", 
                            KeywordList.get(j).getName(), webURL, e.getMessage());
                    }
                }
                
                item.setScore(totalScore);
                
                // 每處理 10 筆顯示進度
                if ((i + 1) % 10 == 0) {
                    logger.info("已處理 {}/{} 筆結果", i + 1, allItems.size());
                }
            }
            
            // 使用 MergeSort 排序
            logger.info("開始排序 {} 筆結果", allItems.size());
            ArrayList<SearchItem> sortedList = MergeSort(allItems);
            
            // 限制返回的結果數量
            if (sortedList.size() > numResults) {
                sortedList = new ArrayList<>(sortedList.subList(0, numResults));
            }
            
            logger.info("排序完成，返回 {} 筆結果", sortedList.size());

            return sortedList;

        } catch (Exception e) {
            // 錯誤處理
            logger.error("搜尋時發生錯誤: ", e);
            e.printStackTrace();
            return new ArrayList<>(); // 發生錯誤時返回空列表
        }
    }
}