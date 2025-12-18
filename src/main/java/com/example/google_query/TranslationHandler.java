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
            // 1. 組裝 URL (這是 Google 的免費公開接口，不需要 API Key)
            // client=gtx: 通用免費客戶端
            // sl=auto: 來源語言自動偵測 (Source Language)
            // tl=zh-TW: 目標語言繁體中文 (Target Language)
            // dt=t: 回傳翻譯文字 (Data Type = text)
            
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-TW&dt=t&q=" + encodedQuery;

            // 2. 發送請求
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0") // 偽裝成瀏覽器
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
     * 解析 GTX 接口回傳的醜醜 JSON
     * 格式長這樣: [[["你好","Hello",null,null,1]], ...]
     */
    private String parseGtxJson(String json) {
        try {
            // GTX 回傳的是一個多層陣列，不是物件
            JsonArray rootArray = JsonParser.parseString(json).getAsJsonArray();
            
            // 翻譯結果在第一層陣列的第 0 個元素裡
            JsonArray sentences = rootArray.get(0).getAsJsonArray();
            
            StringBuilder result = new StringBuilder();
            // 迴圈把所有斷句接起來
            for (int i = 0; i < sentences.size(); i++) {
                JsonArray sentence = sentences.get(i).getAsJsonArray();
                // 翻譯好的文字在 [0]
                result.append(sentence.get(0).getAsString());
            }
            
            return result.toString();
            
        } catch (Exception e) {
            e.printStackTrace();
            return json; // 解析失敗就回傳原始 JSON (或是回傳空字串)
        }
    }
}

