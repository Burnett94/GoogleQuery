package com.example.google_query;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleSearchResponse {
    private ArrayList<SearchItem> items;

    // --- 省略 Getter 和 Setter ---
    public ArrayList<SearchItem> getItems() { return items; }
    public void setItems(ArrayList<SearchItem> items) { this.items = items; }
}