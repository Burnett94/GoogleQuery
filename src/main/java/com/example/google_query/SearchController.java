package com.example.google_query;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.bind.annotation.CrossOrigin; // 引入 CORS

@RestController
@RequestMapping("/api")
// 允許來自所有來源的跨域請求 (開發時方便，生產環境應更嚴格)
@CrossOrigin(origins = "*") 
public class SearchController {

    // 從 application.properties 注入
    @Value("${google.api.key}")
    private String apiKey;

    @Value("${google.cx}")
    private String cx;

    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/search")
    public ResponseEntity<GoogleSearchResponse> search(@RequestParam("q") String query) {
        
        // Google Custom Search API 的 URL
        String url = "https://www.googleapis.com/customsearch/v1";

        // 建立帶有查詢參數的 URL
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("key", apiKey)
                .queryParam("cx", cx)
                .queryParam("q", query);

        try {
            // 呼叫 Google API 並指定回應應映射到我們的 POJO
            GoogleSearchResponse response = restTemplate.getForObject(
                builder.toUriString(), 
                GoogleSearchResponse.class
            );
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            // 簡單的錯誤處理
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }
}