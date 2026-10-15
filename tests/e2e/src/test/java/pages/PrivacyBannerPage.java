package pages;

import com.microsoft.playwright.Page;

/**
 * The zero-cookie notice (#privacy-banner). Shown once per browser; dismissal
 * is remembered in localStorage (see hack.js), so a fresh browser context is
 * required for isVisible() to be meaningful — a reused context that already
 * dismissed it will correctly report not-visible.
 */
public class PrivacyBannerPage {
    private final Page page;

    private final String banner = "#privacy-banner";
    private final String dismissButton = "#btn-dismiss-privacy";

    public PrivacyBannerPage(Page page) {
        this.page = page;
    }

    public boolean isVisible() {
        return page.locator(banner).isVisible();
    }

    public String getText() {
        return page.locator(banner).textContent();
    }

    public void dismiss() {
        page.click(dismissButton);
    }
}
