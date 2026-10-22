package com.newsbot;

/** HTML for the 1080x1350 (4:5) slides. Selenium renders these and screenshots #card. */
final class CardTemplates {
    private CardTemplates() {}

    private static final String PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><style>
            *{box-sizing:border-box;margin:0;padding:0}
            body{width:1200px;height:1500px;background:#ffffff}
            #card{width:1080px;height:1350px;position:relative;overflow:hidden;
              font-family:'Segoe UI','Helvetica Neue',Arial,'Liberation Sans',sans-serif;color:#ffffff;
              background:linear-gradient(160deg,#0b1220 0%,#14233f 55%,#1d3557 100%);
              padding:90px 84px;display:flex;flex-direction:column}
            #card:before{content:'';position:absolute;right:-220px;top:-220px;width:620px;height:620px;
              border-radius:50%;background:rgba(255,183,3,0.10)}
            .brand{font-size:30px;letter-spacing:6px;text-transform:uppercase;color:#7fb4ff;font-weight:700}
            .bar{height:8px;width:140px;background:#ffb703;border-radius:4px;margin:36px 0}
            .footer{margin-top:auto;display:flex;justify-content:space-between;align-items:center;
              font-size:28px;color:#9fb3d1}
            .badge{display:inline-block;margin-top:56px;font-size:34px;font-weight:700;color:#0b1220;
              background:#ffb703;padding:10px 26px;border-radius:40px;align-self:flex-start}
            h1{font-weight:800;line-height:1.16}
            p{line-height:1.45;color:#d6e2f5}
            </style></head><body><div id="card">{{BODY}}</div></body></html>
            """;

    private static String page(String body) {
        return PAGE.replace("{{BODY}}", body);
    }

    static String cover(String brand, String editionLabel, String dateLabel, int storyCount) {
        return page("""
                <div class="brand">{{BRAND}}</div>
                <div style="margin-top:250px;font-size:34px;letter-spacing:8px;color:#ffb703;font-weight:700">{{DATE}}</div>
                <h1 style="font-size:132px;margin-top:30px">{{EDITION}}</h1>
                <div class="bar"></div>
                <p style="font-size:44px;max-width:760px">The {{COUNT}} stories you need to know right now.</p>
                <div class="footer"><span>Swipe to read</span><span style="font-size:48px;color:#ffb703">&rarr;</span></div>
                """
                .replace("{{BRAND}}", esc(brand))
                .replace("{{DATE}}", esc(dateLabel.toUpperCase()))
                .replace("{{EDITION}}", esc(editionLabel))
                .replace("{{COUNT}}", String.valueOf(storyCount)));
    }

    static String news(String brand, int index, int total, NewsItem item) {
        int len = item.title().length();
        int headlineSize = len < 70 ? 70 : len < 110 ? 60 : len < 150 ? 52 : 44;
        String summary = item.summary().isBlank() ? "" :
                "<p style=\"font-size:36px\">" + esc(item.summary()) + "</p>";
        return page("""
                <div class="brand">{{BRAND}}</div>
                <div class="badge">{{IDX}} / {{TOTAL}}</div>
                <h1 style="font-size:{{SIZE}}px;margin-top:48px">{{TITLE}}</h1>
                <div class="bar"></div>
                {{SUMMARY}}
                <div class="footer"><span>Source: {{SOURCE}}</span><span style="font-size:44px;color:#ffb703">&rarr;</span></div>
                """
                .replace("{{BRAND}}", esc(brand))
                .replace("{{IDX}}", String.format("%02d", index))
                .replace("{{TOTAL}}", String.format("%02d", total))
                .replace("{{SIZE}}", String.valueOf(headlineSize))
                .replace("{{TITLE}}", esc(item.title()))
                .replace("{{SUMMARY}}", summary)
                .replace("{{SOURCE}}", esc(item.source())));
    }

    static String outro(String brand) {
        return page("""
                <div class="brand">{{BRAND}}</div>
                <h1 style="font-size:96px;margin-top:300px">Stay informed, every day.</h1>
                <div class="bar"></div>
                <p style="font-size:42px;max-width:800px">Fresh news every morning and evening. Follow, like and share so you never miss an update.</p>
                <div class="footer"><span>Follow {{BRAND}}</span><span></span></div>
                """.replace("{{BRAND}}", esc(brand)));
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
