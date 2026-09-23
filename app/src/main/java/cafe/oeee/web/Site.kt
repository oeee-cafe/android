package cafe.oeee.web

/** The site the app shows. */
object Site {
    const val BASE_URL = "https://oeee.cafe"

    /**
     * What the site looks for at the end of the web view's user agent to know it is in this
     * app: `OeeeCafe platform/<app>`, from which it marks the root `data-app="android"` and
     * `data-form="handheld"` (theme_head.jinja in oeee-cafe/web). No `store/` after it: this
     * build sells nothing. What the app fetches itself -- a drawing for its menu, a file
     * through the download manager -- goes with the web view's user agent, and so with this.
     */
    const val USER_AGENT_SUFFIX = "OeeeCafe platform/android"
}
