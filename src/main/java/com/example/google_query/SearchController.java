package com.example.google_query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/search")
    public ResponseEntity<GoogleSearchResponse> search(@RequestParam(value = "q", required = false) String query) {
        
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
            return ResponseEntity.badRequest().build();
        }
        
        // Google Custom Search API 的 URL
        String url = "https://www.googleapis.com/customsearch/v1";

        // 建立帶有查詢參數的 URL，確保正確編碼（支援中文）
        // UriComponentsBuilder.queryParam() 會自動使用 UTF-8 進行 URL 編碼
        String apiUrl = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("key", apiKey)
                .queryParam("cx", cx)
                .queryParam("q", query)  // queryParam 會自動進行 UTF-8 URL 編碼
                .build()  // build() 會自動編碼所有參數
                .toUriString();

        logger.info("發送到 Google API 的 URL: {}", apiUrl.replace(apiKey, "API_KEY_HIDDEN"));

        try {
            // 呼叫 Google API 並指定回應應映射到我們的 POJO
            GoogleSearchResponse response = restTemplate.getForObject(
                apiUrl, 
                GoogleSearchResponse.class
            );
            
            if (response != null && response.getItems() != null) {
                logger.info("收到 {} 筆搜尋結果", response.getItems().size());
            } else {
                logger.warn("Google API 返回空結果");
            }
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // 錯誤處理
            logger.error("搜尋時發生錯誤: ", e);
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}