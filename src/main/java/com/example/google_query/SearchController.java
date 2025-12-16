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
    
    /**
     * 限制搜尋結果只來自 PTT
     * 使用 Google 搜尋語法 site: 來限制搜尋範圍
     */
    private String restrictToPTT(String query) {
        // 使用 Google 搜尋語法：site:ptt.cc
        // 將原始查詢和網站限制組合在一起
        // 格式: "原始查詢 site:ptt.cc"
        return query + " site:ptt.cc";
    }
    
    /**
     * 檢查 URL 是否來自 PTT
     * @param url 要檢查的 URL
     * @return 如果是來自 PTT 返回 true，否則返回 false
     */
    private boolean isFromPTT(String url) {
        if (url == null) {
            return false;
        }
        
        String lowerUrl = url.toLowerCase();
        // 檢查是否包含 ptt.cc
        return lowerUrl.contains("ptt.cc");
    }
    @GetMapping("/search")
    public ArrayList<SearchItem> search(@RequestParam(value = "q", required = false) String query) {
        
        // 記錄接收到的查詢參數（用於除錯中文編碼）
        if (query != null) {
            logger.info("收到搜尋查詢: {}", query);
            logger.info("查詢參數長度: {} 字符", query.length());
            // 檢查是否包含中文字符
            boolean hasChinese = query.chars().anyMatch(ch -> ch >= 0x4E00 && ch <= 0x9FFF);
            logger.info("包含中文字符: {}", hasChinese);
        }
        
        if (query == null || query.trim().isEmpty()) {
            logger.warn("查詢參數為空");
            return new ArrayList<>(); // 返回空列表而不是 ResponseEntity
        }
        
        // 增強查詢：自動添加餐廳相關關鍵字，讓搜尋結果更聚焦於餐廳
        String enhancedQuery = enhanceQueryForRestaurants(query.trim());
        
        // 限制搜尋結果只來自 PTT
        String finalQuery = restrictToPTT(enhancedQuery);
        logger.info("原始查詢: {}, 增強後查詢: {}, 最終查詢: {}", query, enhancedQuery, finalQuery);
        
        // Google Custom Search API 的 URL
        String url = "https://www.googleapis.com/customsearch/v1";

        // 建立帶有查詢參數的 URL，確保正確編碼（支援中文）
        // 測試階段：限制為 10 筆結果
        String apiUrl = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("key", apiKey)
                .queryParam("cx", cx)
                .queryParam("q", finalQuery)  // 使用限制後的查詢
                .queryParam("num", 10)  // 限制為 10 筆
                .build()  // build() 會自動編碼所有參數
                .toUriString();

        logger.info("發送到 Google API 的 URL: {}", apiUrl.replace(apiKey, "API_KEY_HIDDEN"));

        try {
            // 呼叫 Google API 並指定回應應映射到我們的 POJO
            GoogleSearchResponse response = restTemplate.getForObject(
                apiUrl, 
                GoogleSearchResponse.class
            );
            
            if (response == null || response.getItems() == null || response.getItems().isEmpty()) {
                logger.warn("Google API 返回空結果");
                return new ArrayList<>();
            }
            
            logger.info("收到 {} 筆原始搜尋結果", response.getItems().size());
            
            // 過濾結果，只保留來自 PTT 的結果
            ArrayList<SearchItem> filteredItems = new ArrayList<>();
            for (SearchItem item : response.getItems()) {
                if (isFromPTT(item.getLink())) {
                    filteredItems.add(item);
                } else {
                    logger.debug("過濾掉非 PTT 的結果: {}", item.getLink());
                }
            }
            
            if (filteredItems.isEmpty()) {
                logger.warn("過濾後沒有符合條件的結果（只保留 PTT）");
                return new ArrayList<>();
            }
            
            logger.info("過濾後剩餘 {} 筆結果（僅 PTT）", filteredItems.size());
            
            //建立keyword list
            ArrayList<Keyword> KeywordList = new ArrayList<>();
KeywordList.add(new Keyword("廣宣", -100)); // 廣告
KeywordList.add(new Keyword("邀約", -50));  // 業配關鍵字
KeywordList.add(new Keyword("體驗", -20));  // 軟文常用詞
KeywordList.add(new Keyword("折扣碼", -50)); // 商業意圖極強
KeywordList.add(new Keyword("完全沒有雷", -100));
KeywordList.add(new Keyword("每一道都讓人驚豔", -50));
KeywordList.add(new Keyword("收藏起來", -30));
KeywordList.add(new Keyword("下次聚餐就選這間", -30));
KeywordList.add(new Keyword("留言告訴我你最愛哪一道", -100));
KeywordList.add(new Keyword("留言告訴我你最愛哪一間", -100));
KeywordList.add(new Keyword("留言告訴我你最喜歡哪一間", -100));
KeywordList.add(new Keyword("留言告訴我你最喜歡哪一間", -100));
KeywordList.add(new Keyword("每個人口味不同", -20));
KeywordList.add(new Keyword("每個人喜好不同", -20));




// --- 真實負評/平價訊號 (大幅加分 - 因為我們想看真實的，負評最真實) ---
KeywordList.add(new Keyword("難吃", 15));
KeywordList.add(new Keyword("不推", 15));
KeywordList.add(new Keyword("抱怨", 20)); // 文章標題或內容出現抱怨
KeywordList.add(new Keyword("雷", 10));   // 例如：踩雷、超雷
KeywordList.add(new Keyword("盤", 10));   // 例如：超盤、盤子 (太貴)
KeywordList.add(new Keyword("我家", 15));
KeywordList.add(new Keyword("家", 10));
KeywordList.add(new Keyword("至少", 15));
KeywordList.add(new Keyword("蚊子", 15));
KeywordList.add(new Keyword("XD", 10));

// --- 真實體驗訊號 (中度加分) ---
KeywordList.add(new Keyword("普通", 20));  // 誠實的評價
KeywordList.add(new Keyword("還行", 3));
KeywordList.add(new Keyword("排隊", 3));  // 描述現場狀況
KeywordList.add(new Keyword("態度", 5));  // 提到服務態度
KeywordList.add(new Keyword("回訪", 10)); // 關鍵指標：願不願意再來*/
KeywordList.add(new Keyword("廣宣", -100)); // 廣告
KeywordList.add(new Keyword("邀約", -50));  // 業配關鍵字
KeywordList.add(new Keyword("折扣碼", -50)); // 商業意圖極強
KeywordList.add(new Keyword("排隊時間", 20));
KeywordList.add(new Keyword("等待時間", 20));
KeywordList.add(new Keyword("等很久", 10)); 


 // --- 真實負評/平價訊號 (大幅加分 - 因為我們想看真實的，負評最真實) ---
KeywordList.add(new Keyword("難吃", 15));
KeywordList.add(new Keyword("不推", 15));
KeywordList.add(new Keyword("雷", 10));   // 例如：踩雷、超雷
KeywordList.add(new Keyword("盤", 10));   // 例如：超盤、盤子 (太貴)
KeywordList.add(new Keyword("貴", 15));     // 提到價格討論
KeywordList.add(new Keyword("CP值低", 20)); // 直接且負面的價值判斷
KeywordList.add(new Keyword("等很久", 10)); // 客觀描述用餐痛點
KeywordList.add(new Keyword("失望", 15));   // 負面情感表達
KeywordList.add(new Keyword("不會再來", 20)); // 強烈且明確的負面結論
KeywordList.add(new Keyword("不會特別來", 20));
KeywordList.add(new Keyword("不會特地來", 20));
KeywordList.add(new Keyword("不用特別來", 20));
KeywordList.add(new Keyword("不用特地來", 20));
KeywordList.add(new Keyword("不用特別排", 20));

// --- 真實體驗訊號 (中度加分) ---
KeywordList.add(new Keyword("普通", 5));  // 誠實的評價
KeywordList.add(new Keyword("還行", 5));
KeywordList.add(new Keyword("態度", 5));  // 提到服務態度
KeywordList.add(new Keyword("回訪", 10)); 

// --- PTT 鄉民用語 (高真實度的情緒表達) ---
KeywordList.add(new Keyword("大推", 6));    // 比普通推薦更強
KeywordList.add(new Keyword("神", 10));      // 例如：神店、神之料理
KeywordList.add(new Keyword("屌打", 9));    // 比較級，例如：屌打X店 (表示這家很強)
KeywordList.add(new Keyword("頂", 8));       // 近期流行語，表示頂尖

// --- 一般正面體驗 (中度加分，需小心業配混用) ---
KeywordList.add(new Keyword("驚艷", 5));     // 超出預期
KeywordList.add(new Keyword("CP值高", 6));   // PTT 最在意的性價比
KeywordList.add(new Keyword("吃得飽", 5));   // 份量足夠，鄉民很在意
KeywordList.add(new Keyword("不錯", 5));     // 保守但正面的評價
KeywordList.add(new Keyword("喜歡", 3));     // 普通正面
KeywordList.add(new Keyword("好喝", 3));     // 普通正面
KeywordList.add(new Keyword("好吃", 3));     // *注意*：這是業配最愛用的詞，權重建議給低一點

// --- 標題結構判定 (需稍微修改邏輯) ---
// 註：這部分建議直接在迴圈內判斷 item.getTitle()，而不只是用 WordCounter 算內文
            //算分（只對過濾後的結果計算）
            for (int i = 0; i < filteredItems.size(); i++) {
                SearchItem item = filteredItems.get(i);
                String webURL = item.getLink();
                String title = item.getTitle() != null ? item.getTitle() : "無標題";
                
                WordCounter counter = new WordCounter(webURL);
                double totalScore = 0.0;
                ArrayList<String> scoreDetails = new ArrayList<>(); // 記錄分數明細
                
                logger.info("");
                logger.info("========== 計算網站分數: {} ==========", title);
                logger.info("");
                logger.info("URL: {}", webURL);
                logger.info("");
                
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < KeywordList.size(); j++) {
                    try {
                        Keyword keyword = KeywordList.get(j);
                        int count = counter.countKeyword(keyword.getName());
                        double contribution = count * keyword.getWeight();

                        if (count > 0) {
                            // 收集簡潔明細
                            String detail = String.format("%s:%d*%.1f=%.1f", 
                                keyword.getName(), count, keyword.getWeight(), contribution);
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(detail);
                        }

                        totalScore += contribution;
                    } catch (IOException e) {
                        logger.warn("");
                        logger.warn("計算關鍵字 '{}' 時發生錯誤 (URL: {}): {}", 
                            KeywordList.get(j).getName(), webURL, e.getMessage());
                    }
                }

                item.setScore(totalScore);
                String totalScoreStr = String.format("%.1f", totalScore);
                if (sb.length() == 0) {
                    logger.info("總分: {}", totalScoreStr);
                    logger.info("  無匹配關鍵字");
                } else {
                    logger.info("總分: {} | 明細: {}", totalScoreStr, sb.toString());
                }
                logger.info("");
                logger.info("==========================================");
                logger.info("");
            }
            
            // 使用 MergeSort 排序
            ArrayList<SearchItem> sortedList = MergeSort(filteredItems);
            
            // 確保只返回最多 10 筆結果（測試階段限制）
            if (sortedList.size() > 10) {
                sortedList = new ArrayList<>(sortedList.subList(0, 10));
            }
            
            logger.info("排序完成，返回 {} 筆結果（限制為 10 筆）", sortedList.size());

            return sortedList;

        } catch (Exception e) {
            // 錯誤處理
            logger.error("搜尋時發生錯誤: ", e);
            e.printStackTrace();
            return new ArrayList<>(); // 發生錯誤時返回空列表
        }
    }
}