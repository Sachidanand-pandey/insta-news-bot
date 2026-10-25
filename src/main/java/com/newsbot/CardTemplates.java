package com.newsbot;

/**
 * HTML for the 1080x1350 (4:5) slides. Selenium renders these and screenshots #card.
 * Every slide carries the page handle as a watermark; news slides show a photo when one was found.
 */
final class CardTemplates {
    private CardTemplates() {}

    private static final String PAGE = """
            <!doctype html><html><head><meta charset="utf-8"><style>
            *{box-sizing:border-box;margin:0;padding:0}
            body{width:1200px;height:1500px;background:#ffffff}
            #card{width:1080px;height:1350px;position:relative;overflow:hidden;isolation:isolate;
              font-family:'Segoe UI','Helvetica Neue',Arial,'Liberation Sans',sans-serif;color:#ffffff;
              background:linear-gradient(160deg,#0b1220 0%,#14233f 55%,#1d3557 100%);
              padding:76px 84px;display:flex;flex-direction:column}
            #card:before{content:'';position:absolute;right:-220px;top:-220px;width:620px;height:620px;
              border-radius:50%;background:rgba(255,183,3,0.10)}
            .top{display:flex;justify-content:space-between;align-items:center}
            .brand{font-size:30px;letter-spacing:6px;text-transform:uppercase;color:#7fb4ff;font-weight:700}
            .handle{font-size:28px;font-weight:700;color:#ffffff;opacity:.92;letter-spacing:.5px}
            .bar{height:8px;width:140px;background:#ffb703;border-radius:4px;margin:30px 0}
            .footer{margin-top:auto;display:flex;justify-content:space-between;align-items:center;
              font-size:28px;color:#9fb3d1}
            .badge{display:inline-block;margin-top:56px;font-size:34px;font-weight:700;color:#0b1220;
              background:#ffb703;padding:10px 26px;border-radius:40px;align-self:flex-start}
            .photo{position:relative;margin-top:30px;border-radius:24px;overflow:hidden;
              flex:none;background:#0b1220}
            .photo img{width:100%;height:100%;object-fit:cover;display:block;filter:saturate(1.08) contrast(1.05)}
            .photo:before{content:'';position:absolute;inset:0;z-index:1;
              background:linear-gradient(135deg,rgba(20,50,110,.30),rgba(255,140,40,.10))}
            .photo.tile{display:flex;align-items:center;justify-content:center;
              background:linear-gradient(135deg,#14233f,#2a4a7f)}
            .photo.tile:before{display:none}
            .photo.tile img.lg{width:260px;height:260px;border-radius:50%;object-fit:cover;filter:none;
              box-shadow:0 10px 36px rgba(0,0,0,.45)}
            .logo{border-radius:50%;object-fit:cover;display:block;box-shadow:0 10px 36px rgba(0,0,0,.45)}
            .photo:after{content:'';position:absolute;left:0;right:0;bottom:0;height:45%;
              background:linear-gradient(rgba(0,0,0,0),rgba(0,0,0,.62))}
            .photo .badge{position:absolute;left:22px;top:22px;margin:0;font-size:30px;padding:8px 22px;z-index:2}
            .diag{position:absolute;left:-300px;top:-300px;width:1680px;height:1950px;transform:rotate(-30deg);
              display:flex;flex-direction:column;justify-content:center;align-items:center;gap:260px;
              z-index:5;pointer-events:none}
            .diag span{font-size:66px;font-weight:800;letter-spacing:2px;white-space:nowrap;
              color:rgba(255,255,255,.13);text-shadow:0 2px 10px rgba(0,0,0,.25)}
            .diag span:nth-child(even){transform:translateX(300px)}
            .photo .credit{position:absolute;left:22px;bottom:20px;z-index:2;font-size:20px;
              color:#e6edf8;text-shadow:0 2px 6px rgba(0,0,0,.85)}
            .more{font-size:27px;color:#ffb703;font-weight:700;margin-bottom:22px}
            h1{font-weight:800;line-height:1.16;flex:none}
            p{line-height:1.42;color:#d6e2f5;flex:none}
            .clamp{display:-webkit-box;-webkit-box-orient:vertical;overflow:hidden}
            </style></head><body><div id="card">{{BODY}}</div></body></html>
            """;

    private static String page(String body, String handle) {
        String mark = at(handle);
        StringBuilder rows = new StringBuilder("<div class=\"diag\">");
        for (int i = 0; i < 6; i++) {
            rows.append("<span>").append(mark).append("</span>");
        }
        rows.append("</div>");
        return PAGE.replace("{{BODY}}", body + rows);
    }

    private static String at(String handle) {
        return "@" + esc(handle);
    }

    /** photo may be null; when present it is used (darkened) as the cover background. */
    static String cover(String brand, String handle, String editionLabel, String dateLabel, int storyCount, String photo, String logo) {
        String logoImg = logo == null ? "" : "<img class=\"logo\" src=\"" + logo + "\" style=\"width:230px;height:230px;margin-top:90px\">";
        String bg = photo == null ? "" :
                "<div style=\"position:absolute;inset:0;z-index:-1;background:linear-gradient(160deg,"
                        + "rgba(11,18,32,.90) 0%,rgba(20,35,63,.78) 55%,rgba(29,53,87,.88) 100%),url('" + photo
                        + "') center/cover no-repeat\"></div>";
        return page(bg + """
                <div class="top"><div class="brand">{{BRAND}}</div><div class="handle">{{HANDLE}}</div></div>
                {{LOGO}}
                <div style="margin-top:{{DATEGAP}}px;font-size:34px;letter-spacing:8px;color:#ffb703;font-weight:700">{{DATE}}</div>
                <h1 style="font-size:132px;margin-top:30px">{{EDITION}}</h1>
                <div class="bar"></div>
                <p style="font-size:44px;max-width:760px">The {{COUNT}} stories you need to know right now.</p>
                <div class="footer"><span>Swipe to read</span><span style="font-size:48px;color:#ffb703">&rarr;</span></div>
                """
                .replace("{{BRAND}}", esc(brand))
                .replace("{{HANDLE}}", at(handle))
                .replace("{{LOGO}}", logoImg)
                .replace("{{DATEGAP}}", logo == null ? "250" : "50")
                .replace("{{DATE}}", esc(dateLabel.toUpperCase()))
                .replace("{{EDITION}}", esc(editionLabel))
                .replace("{{COUNT}}", String.valueOf(storyCount)), handle);
    }

    /** photo may be null (no photo found): the slide then falls back to the text-only layout. */
    static String news(String brand, String handle, int index, int total, NewsItem item, String photo, String logo) {
        boolean hasPhoto = photo != null;
        boolean hasImage = hasPhoto || logo != null;   // no photo -> a branded tile with your logo instead
        int len = item.title().length();
        int headlineSize = hasImage
                ? (len < 70 ? 56 : len < 110 ? 52 : len < 150 ? 47 : len < 200 ? 41 : 36)
                : (len < 70 ? 70 : len < 110 ? 60 : len < 150 ? 52 : 44);
        int summarySize = hasImage ? (len < 110 ? 34 : 31) : 36;
        int photoHeight = len < 70 ? 580 : len < 110 ? 540 : len < 150 ? 500 : len < 200 ? 430 : 340;
        if (!hasPhoto && logo != null) {
            photoHeight = Math.min(photoHeight, 420);
        }
        int summaryLines = hasImage ? 3 : 8;
        String summary = item.summary().isBlank() ? "" :
                "<p class=\"clamp\" style=\"font-size:" + summarySize + "px;-webkit-line-clamp:" + summaryLines + "\">"
                        + esc(item.summary()) + "</p>";

        String idx = String.format("%02d / %02d", index, total);
        String photoBlock = hasPhoto
                ? "<div class=\"photo\" style=\"height:" + photoHeight + "px\"><img src=\"" + photo + "\"><div class=\"badge\">" + idx + "</div>"
                        + "<div class=\"credit\">Photo: " + esc(item.source()) + "</div></div>"
                : logo != null
                ? "<div class=\"photo tile\" style=\"height:" + photoHeight + "px\"><img class=\"lg\" src=\"" + logo + "\">"
                        + "<div class=\"badge\">" + idx + "</div></div>"
                : "<div class=\"badge\">" + idx + "</div>";
        String readMore = item.link().isBlank() ? "" : "<div class=\"more\">Read the full story: link in caption</div>";

        return page("""
                <div class="top"><div class="brand">{{BRAND}}</div><div class="handle">{{HANDLE}}</div></div>
                {{PHOTO}}
                <h1 style="font-size:{{SIZE}}px;margin-top:{{GAP}}px">{{TITLE}}</h1>
                <div class="bar"></div>
                {{SUMMARY}}
                <div style="margin-top:auto"></div>
                {{MORE}}
                <div class="footer" style="margin-top:0"><span>Source: {{SOURCE}}</span><span style="font-size:44px;color:#ffb703">&rarr;</span></div>
                """
                .replace("{{BRAND}}", esc(brand))
                .replace("{{HANDLE}}", at(handle))
                .replace("{{SIZE}}", String.valueOf(headlineSize))
                .replace("{{GAP}}", hasImage ? "30" : "48")
                .replace("{{TITLE}}", esc(item.title()))
                .replace("{{SUMMARY}}", summary)
                .replace("{{MORE}}", readMore)
                .replace("{{SOURCE}}", esc(item.source()))
                .replace("{{PHOTO}}", photoBlock), handle);
    }

    static String outro(String brand, String handle, String logo) {
        String logoImg = logo == null ? "" : "<img class=\"logo\" src=\"" + logo + "\" style=\"width:240px;height:240px;margin-top:110px\">";
        return page("""
                <div class="top"><div class="brand">{{BRAND}}</div><div class="handle">{{HANDLE}}</div></div>
                {{LOGO}}
                <h1 style="font-size:96px;margin-top:{{H1GAP}}px">Stay informed, every day.</h1>
                <div class="bar"></div>
                <p style="font-size:42px;max-width:800px">Fresh news every morning and evening. Follow, like and share so you never miss an update.</p>
                <div class="footer"><span style="font-size:36px;color:#ffb703;font-weight:700">Follow {{HANDLE}}</span><span></span></div>
                """
                .replace("{{BRAND}}", esc(brand))
                .replace("{{LOGO}}", logoImg)
                .replace("{{H1GAP}}", logo == null ? "300" : "60")
                .replace("{{HANDLE}}", at(handle)), handle);
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}