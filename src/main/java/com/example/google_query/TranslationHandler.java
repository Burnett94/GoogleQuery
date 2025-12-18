
package com.example.google_query;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class TranslationHandler {

    private final HttpClient client;

    public TranslationHandler() {
        this.client = HttpClient.newHttpClient();
    }

    /**
     * 判斷是否需要翻譯
     * 邏輯：只要輸入的字串裡「沒有包含中文字」，就全部丟去翻譯
     */
    public boolean needsTranslation(String query) {
        if (query == null) return false;
        // Regex: 檢查是否包含 CJK (中日韓) 的 "中" 文字範圍
        boolean hasChinese = query.matches(".*[\\u4e00-\\u9fa5].*");
        return !hasChinese;
    }

    /**
     * 使用 Google Translate GTX (免費通道) 翻譯成繁體中文
     */
    public String translateToChinese(String query) {
        if (query == null || query.isEmpty()) return "";

        try {
            // 1. 組裝 URL
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-TW&dt=t&q=" + encodedQuery;

            // 2. 發送請求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 3. 解析結果
            if (response.statusCode() == 200) {
                return parseGtxJson(response.body());
            } else {
                System.err.println("翻譯失敗 HTTP Code: " + response.statusCode());
                return query; // 失敗回傳原文
            }

        } catch (Exception e) {
            System.err.println("翻譯系統錯誤: " + e.getMessage());
            return query;
        }
    }

    /**
     * 解析 GTX 接口回傳的 JSON
     */
    private String parseGtxJson(String json) {
        try {
            JsonArray rootArray = JsonParser.parseString(json).getAsJsonArray();
            JsonArray sentences = rootArray.get(0).getAsJsonArray();
            
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < sentences.size(); i++) {
                JsonArray sentence = sentences.get(i).getAsJsonArray();
                result.append(sentence.get(0).getAsString());
            }
            
            return result.toString();
            
        } catch (Exception e) {
            e.printStackTrace();
            return json;
        }
    }
}
