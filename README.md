# Instagram News Bot (Java + Selenium + Jenkins)

Posts a news carousel to your Instagram page every day at **6:00 AM** and **6:00 PM IST**.

Each post is a 6-slide carousel: a cover, 4 headline slides (headline, short summary, source) and a closing slide, plus a caption with the headlines, sources and hashtags.

## How it works

1. **Fetch**: reads RSS feeds, keeps the newest stories (last 24 h by default), removes duplicates, and balances across sources.
2. **Render**: Selenium opens headless Chrome, loads each HTML slide and screenshots it as a 1080x1350 JPEG.
3. **Upload**: Instagram needs public image URLs, so slides go to imgbb (auto-expire after 24 h).
4. **Publish**: official Instagram Graph API (child containers, carousel container, `media_publish`).
5. **Remember**: posted headlines are saved in `state/posted.txt` so the 6 PM post never repeats the 6 AM one.
6. **Schedule**: Jenkins runs `Jenkinsfile` on two cron triggers.

> Why not Selenium on instagram.com? Automating the website breaks Instagram's terms and usually ends in login challenges or a blocked account. Selenium is used here for what it does safely: rendering the post images. Publishing goes through the official API.

## One-time setup

### 1. Instagram
- Convert the page to a **Business or Creator** account (Instagram app, Settings, Account type).
- Create an app at <https://developers.facebook.com>, add the **Instagram** product (API setup with Instagram login).
- Generate an access token for your account with permissions `instagram_business_basic` and `instagram_business_content_publish`.
- Exchange it for a **long-lived token** (valid 60 days). Note your **Instagram user ID**.
- Docs: <https://developers.facebook.com/docs/instagram-platform/instagram-graph-api/content-publishing>

Refresh the token before it expires (about every 50 days):
```
curl "https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token&access_token=<TOKEN>"
```
Then update the `ig-access-token` credential in Jenkins.

### 2. Image host
Get a free API key at <https://api.imgbb.com>.

### 3. Jenkins agent (Linux)
Install: JDK 17+, Maven, Google Chrome (or Chromium), and fonts (`sudo apt install fonts-liberation`).
Selenium Manager downloads the matching chromedriver automatically, so the agent needs internet access.
If Chrome is in a custom location, set `CHROME_BIN`.

### 4. Jenkins job
1. Push this folder to a Git repo.
2. Add three **Secret text** credentials: `ig-user-id`, `ig-access-token`, `imgbb-api-key`.
3. Create a **Pipeline** job, "Pipeline script from SCM", pointing at the repo. Run it once manually so Jenkins registers the cron triggers.
4. Do not wipe the workspace between builds (it holds `state/posted.txt`).

## Run locally first

```bash
# Render slides and print the caption without posting
DRY_RUN=true mvn -q compile exec:java -Dexec.args=morning
# slides appear in output/<date>-morning/

# Real post
export IG_USER_ID=... IG_ACCESS_TOKEN=... IMGBB_API_KEY=...
mvn -q compile exec:java -Dexec.args=auto
```

## Settings (environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `IG_USER_ID`, `IG_ACCESS_TOKEN`, `IMGBB_API_KEY` | required | Credentials |
| `NEWS_FEEDS` | The Hindu, NDTV, Times of India | `Name\|url,Name\|url` (RSS 2.0 feeds) |
| `STORY_COUNT` | 4 | Headlines per post |
| `MAX_AGE_HOURS` | 24 | Ignore stories older than this |
| `BRAND_NAME` | Daily News | Shown on every slide |
| `HASHTAGS` | news hashtags | Appended to the caption |
| `BOT_TZ` | Asia/Kolkata | Decides morning vs evening |
| `DRY_RUN` | false | Render only, no posting |
| `STATE_FILE` | state/posted.txt | Posted-headline memory |
| `CHROME_BIN` | auto | Chrome path override |
| `IG_GRAPH_HOST`, `IG_GRAPH_VERSION` | graph.instagram.com, v26.0 | Change if Meta retires the version |

## Notes

- **"Real time"**: each post uses the newest headlines available at 6 AM / 6 PM. To post more often, add cron lines in the `Jenkinsfile` (Instagram allows about 100 API posts per 24 h).
- **Timing**: Jenkins can start a build a minute or two late when its queue is busy.
- **Content**: slides use headline, a short summary and source credit. Check each publisher's terms for reuse, and prefer official feeds meant for syndication.
- **Feed URLs** can change. If a feed fails the bot logs a warning and continues with the others.
- **Failures**: the publish step is never retried automatically, to avoid double posts. If a run fails after the image upload, just re-run the job.

## Project layout

```
Jenkinsfile                      schedule + pipeline
pom.xml                          Java 17, Selenium 4, Jackson
src/main/java/com/newsbot/
  Main.java                      orchestration + caption
  NewsFetcher.java               RSS fetch, de-dupe, balanced selection
  CardTemplates.java             slide HTML/CSS
  CardRenderer.java              Selenium screenshot to JPEG
  ImageHost.java                 imgbb upload
  InstagramPublisher.java        Graph API carousel publishing
  PostedStore.java               remembers posted headlines
```
