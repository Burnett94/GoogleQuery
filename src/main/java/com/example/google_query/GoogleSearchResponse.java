package com.example.google_query;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleSearchResponse {
    private List<SearchItem> items;

    // --- 省略 Getter 和 Setter ---
    public List<SearchItem> getItems() { return items; }
    public void setItems(List<SearchItem> items) { this.items = items; }
}