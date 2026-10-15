package pages;

import com.microsoft.playwright.Page;

/**
 * The connection-lost notice (#offline-banner). Hidden by default; hack.js
 * toggles it via the browser's online/offline events, which Playwright can
 * trigger directly with {@code page.context().setOffline(true)} rather than
 * needing a real network outage.
 */
public class OfflineBannerPage {
    private final Page page;

    private final String banner = "#offline-banner";

    public OfflineBannerPage(Page page) {
        this.page = page;
    }

    public boolean isVisible() {
        return page.locator(banner).isVisible();
    }
}
