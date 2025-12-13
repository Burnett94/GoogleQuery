document.addEventListener("DOMContentLoaded", () => {
    const searchButton = document.getElementById("search-button");
    const searchInput = document.getElementById("search-query");
    const resultsContainer = document.getElementById("results-container");

    // 按下按鈕或 Enter 鍵時觸發
    searchButton.addEventListener("click", performSearch);
    searchInput.addEventListener("keypress", (e) => {
        if (e.key === "Enter") {
            performSearch();
        }
    });

    async function performSearch() {
        const query = searchInput.value.trim();
        if (!query) {
            alert("請輸入搜尋關鍵字");
            return;
        }

        // 顯示載入中...
        resultsContainer.innerHTML = "<div class='loading'>搜尋中...</div>";

        // 除錯：記錄要搜尋的關鍵字
        console.log("搜尋關鍵字:", query);
        const apiUrl = `http://localhost:8080/api/search?q=${encodeURIComponent(query)}`;
        console.log("API URL:", apiUrl);

        try {
            // **關鍵：呼叫我們的 Spring Boot 後端 API**
            // 確保您的 Spring Boot 正在 http://localhost:8080 上運行
            // 設置 headers 確保正確處理 UTF-8 編碼
            const response = await fetch(apiUrl, {
                method: 'GET',
                headers: {
                    'Accept': 'application/json; charset=utf-8',
                    'Content-Type': 'application/json; charset=utf-8'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP 錯誤! 狀態: ${response.status}`);
            }

            const data = await response.json(); // 解析 Spring Boot 傳回的 JSON
            console.log("收到回應:", data);
            // 後端現在直接返回 ArrayList<SearchItem>，所以 data 本身就是陣列
            console.log("結果數量:", Array.isArray(data) ? data.length : 0);
            
            renderResults(Array.isArray(data) ? data : []);
            
        } catch (error) {
            console.error("搜尋時發生錯誤:", error);
            resultsContainer.innerHTML = "<div class='error'>搜尋失敗，請稍後再試。</div>";
        }
    }

    function renderResults(items) {
        // 清空舊結果
        resultsContainer.innerHTML = "";

        if (!items || items.length === 0) {
            resultsContainer.innerHTML = "<div class='no-results'>找不到相關結果。</div>";
            return;
        }

        // 遍歷結果並建立 HTML
        items.forEach(item => {
            const itemElement = document.createElement("div");
            itemElement.classList.add("result-item");

            // 格式化分數顯示
            const score = item.score !== undefined ? item.score : 0;
            const scoreFormatted = score.toFixed(1);
            
            // 根據分數決定樣式類別
            let scoreClass = 'neutral';
            if (score > 0) {
                scoreClass = 'positive';
            } else if (score < 0) {
                scoreClass = 'negative';
            }

            itemElement.innerHTML = `
                <div class="score ${scoreClass}">分數: ${scoreFormatted}</div>
                <a href="${item.link}" target="_blank">${item.title}</a>
                <div class="link">${item.link}</div>
                <p>${item.snippet}</p>
            `;
            
            resultsContainer.appendChild(itemElement);
        });
    }
});