package cafe.oeee.web

/** The site the app shows. */
object Site {
    const val BASE_URL = "https://oeee.cafe"

    /**
     * What the site looks for at the end of the web view's user agent to know it is in this
     * app: `OeeeCafe platform/<app>`, from which it marks the root `data-app="android"` and
     * `data-form="handheld"` (theme_head.jinja in oeee-cafe/web); `store/google` after it
     * says this build sells through Google Play, so the site draws /supporter's buttons for
     * it and the page asks the app to sell (PlayBilling). What the app fetches itself -- a
     * drawing for its menu, a file through the download manager -- goes with the web view's
     * user agent, and so with this.
     */
    const val USER_AGENT_SUFFIX = "OeeeCafe platform/android store/google"
}
