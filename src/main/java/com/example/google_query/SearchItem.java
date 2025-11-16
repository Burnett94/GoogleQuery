package com.example.google_query;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 忽略我們沒有定義的 JSON 欄位
@JsonIgnoreProperties(ignoreUnknown = true)
public class SearchItem {
    private String title;
    private String link;
    private String snippet;

    // --- 省略 Getter 和 Setter ---
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }
    public String getSnippet() { return snippet; }
    public void setSnippet(String snippet) { this.snippet = snippet; }
}