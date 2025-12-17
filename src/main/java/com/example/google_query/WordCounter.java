package com.example.google_query;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.net.URI;
import java.util.*;

/**
 * WordCounter
 * - 支援抓取第一層頁面內容
 * - 可選擇再抓第二層子連結頁面（depth=2）
 * - 使用 Boyer-Moore 計算關鍵字出現次數（沿用你原本的演算法）
 */
public class WordCounter {
    private final String urlStr;

    // ✅ 抓過的頁面快取：避免同一個 URL 反覆抓
    private final Map<String, String> contentCache = new HashMap<>();

    public WordCounter(String urlStr) {
        this.urlStr = urlStr;
    }

    /**
     * 使用 Jsoup 抓取 HTML，並轉成可用的 "純文字內容"
     */
    private String fetchTextContent(String url) throws Exception {
        // 已抓過就直接回傳
        if (contentCache.containsKey(url)) return contentCache.get(url);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; GoogleQueryBot/1.0)")
                .timeout(5000)
                .followRedirects(true)
                .get();

        String text = doc.text(); // ✅ 只取文字（去掉 HTML tag）
        contentCache.put(url, text);
        return text;
    }

    /**
     * 從 HTML 文件中抽出子連結（最多 maxLinks 個）
     */
    private List<String> extractChildLinks(String url, int maxLinks) throws Exception {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; GoogleQueryBot/1.0)")
                .timeout(5000)
                .followRedirects(true)
                .get();

        Elements links = doc.select("a[href]");
        List<String> results = new ArrayList<>();

        for (Element a : links) {
            String abs = a.absUrl("href"); // 會自動把相對路徑補成完整 URL
            if (abs == null || abs.isBlank()) continue;

            // 過濾非 http(s)
            if (!(abs.startsWith("http://") || abs.startsWith("https://"))) continue;

            // 過濾常見不要抓的資源
            String lower = abs.toLowerCase();
            if (lower.contains("javascript:") || lower.contains("mailto:")) continue;
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".gif") || lower.endsWith(".svg") || lower.endsWith(".webp") ||
                lower.endsWith(".pdf") || lower.endsWith(".zip") || lower.endsWith(".rar") ||
                lower.endsWith(".mp4") || lower.endsWith(".mp3")) {
                continue;
            }

            results.add(abs);
            if (results.size() >= maxLinks) break;
        }

        return results;
    }

    /**
     * 是否同一個 Host（用來避免爬到整個網路）
     */
    private boolean sameHost(String a, String b) {
        try {
            URI ua = URI.create(a);
            URI ub = URI.create(b);
            if (ua.getHost() == null || ub.getHost() == null) return false;
            return ua.getHost().equalsIgnoreCase(ub.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    // ==============================
    // ✅ 你原本的 Boyer-Moore 演算法
    // ==============================

    public int BoyerMoore(String T, String P) {
        int i = P.length() - 1;
        int j = P.length() - 1;

        while (i <= T.length() - 1) {
            if (T.charAt(i) == P.charAt(j)) {
                if (j == 0) {
                    return i;
                } else {
                    i--;
                    j--;
                }
            } else {
                i = i + P.length() - min(j, 1 + last(T.charAt(i), P));
                j = P.length() - 1;
            }
        }
        return -1;
    }

    public int last(char c, String P) {
        for (int i = P.length() - 1; i >= 0; i--) {
            if (P.charAt(i) == c) {
                return i;
            }
        }
        return -1;
    }

    public int min(int a, int b) {
        if (a < b) return a;
        else if (b < a) return b;
        else return a;
    }

    /**
     * ✅ 原本的功能：只算第一層 URL 的 keyword 次數
     */
    public int countKeyword(String keyword) throws Exception {
        String content = fetchTextContent(this.urlStr);

        content = content.toUpperCase(Locale.ROOT);
        keyword = keyword.toUpperCase(Locale.ROOT);

        int retVal = 0;
        int startIdx = 0;

        while (startIdx < content.length()) {
            int found = BoyerMoore(content.substring(startIdx), keyword);
            if (found == -1) break;
            retVal++;
            startIdx += found + keyword.length();
        }

        return retVal;
    }

    /**
     * ✅ 新功能：支援第二層（子連結頁面）一起算
     *
     * @param keyword 關鍵字
     * @param depth 1=只算第一層，2=再抓第二層子頁
     * @param maxChildLinksPerPage 每個第一層最多抓幾個子連結
     */
    public int countKeywordWithDepth(String keyword, int depth, int maxChildLinksPerPage) throws Exception {
        if (depth < 1) depth = 1;
        if (maxChildLinksPerPage < 0) maxChildLinksPerPage = 0;

        String kw = keyword.toUpperCase(Locale.ROOT);

        // visited 避免循環（A->B->A）
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Map<String, Integer> level = new HashMap<>();

        queue.add(urlStr);
        level.put(urlStr, 1);

        int total = 0;

        while (!queue.isEmpty()) {
            String url = queue.poll();
            int lv = level.getOrDefault(url, 1);

            if (lv > depth) continue;
            if (visited.contains(url)) continue;
            visited.add(url);

            // 1) 計算目前頁面
            String content;
            try {
                content = fetchTextContent(url);
            } catch (Exception e) {
                // 抓不到就略過，不要讓整個流程爆掉
                continue;
            }

            String upper = content.toUpperCase(Locale.ROOT);

            int pageCount = 0;
            int startIdx = 0;
            while (startIdx < upper.length()) {
                int found = BoyerMoore(upper.substring(startIdx), kw);
                if (found == -1) break;
                pageCount++;
                startIdx += found + kw.length();
            }
            total += pageCount;

            // 2) 抽子連結（第二層）
            if (lv < depth && maxChildLinksPerPage > 0) {
                List<String> childLinks;
                try {
                    childLinks = extractChildLinks(url, maxChildLinksPerPage);
                } catch (Exception e) {
                    continue;
                }

                for (String child : childLinks) {
                    // ✅ 建議：只爬同網域，避免亂飛（可把這行拿掉變成全網爬）
                    if (!sameHost(urlStr, child)) continue;

                    if (!visited.contains(child)) {
                        queue.add(child);
                        level.put(child, lv + 1);
                    }
                }
            }
        }

        return total;
    }
}
