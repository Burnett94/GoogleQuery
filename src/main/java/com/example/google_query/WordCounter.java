package com.example.google_query;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WordCounter {
    private String urlStr;
    private String content;

    public WordCounter(String urlStr) {
        this.urlStr = urlStr;
    }

    /**
     * 抓取網頁內容 (加入 User-Agent 避免被擋)
     */
    private String fetchContent() throws IOException {
        if (this.content != null) {
            return this.content;
        }

        URL url = new URL(this.urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        // 偽裝成 Chrome 瀏覽器
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(5000); // 5秒連線超時
        conn.setReadTimeout(5000);    // 5秒讀取超時
        
        String charset = "UTF-8"; // 預設編碼
        String contentType = conn.getContentType();
        if (contentType != null && contentType.contains("charset=")) {
            charset = contentType.substring(contentType.indexOf("charset=") + 8);
        }

        InputStream in = conn.getInputStream();
        BufferedReader br = new BufferedReader(new InputStreamReader(in, charset));
        StringBuilder retVal = new StringBuilder();
        String line = null;

        while ((line = br.readLine()) != null) {
            retVal.append(line).append("\n");
        }

        this.content = retVal.toString();
        return this.content;
    }

    /**
     * 抓取該網頁內的所有連結 (href)
     * 並自動將 "/news/123" 轉為 "https://site.com/news/123"
     */
    public Set<String> getHyperlinks() {
        Set<String> links = new HashSet<>();
        try {
            String html = fetchContent();
            
            // Regex 抓取 href="..."
            Pattern pattern = Pattern.compile("href\\s*=\\s*[\"']?([^\"'\\s>]+)[\"']?", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);

            URL baseUrl = new URL(this.urlStr); // 用來解析相對路徑

            while (matcher.find()) {
                String link = matcher.group(1);
                
                // 過濾無效連結
                if (link.isEmpty() || link.startsWith("#") || link.startsWith("javascript:") || link.startsWith("mailto:")) {
                    continue;
                }

                try {
                    // 自動轉絕對路徑
                    URL absoluteUrl = new URL(baseUrl, link);
                    
                    // 只保留 http/https
                    if (absoluteUrl.getProtocol().startsWith("http")) {
                        links.add(absoluteUrl.toString());
                    }
                } catch (Exception e) {
                    // 忽略格式錯誤
                }
            }
        } catch (Exception e) {
            // 抓取失敗時保持安靜或印出錯誤
            System.err.println("WordCounter 錯誤 (" + urlStr + "): " + e.getMessage());
        }
        return links;
    }

    /**
     * 計算關鍵字出現次數 (簡單字串比對)
     */
    public int countKeyword(String keyword) throws IOException {
        if (content == null) {
            fetchContent();
        }

        // 轉大寫忽略大小寫
        String contentUpper = content.toUpperCase();
        String keywordUpper = keyword.toUpperCase();

        int count = 0;
        int idx = 0;
        while ((idx = contentUpper.indexOf(keywordUpper, idx)) != -1) {
            count++;
            idx += keywordUpper.length();
        }
        return count;
    }
}