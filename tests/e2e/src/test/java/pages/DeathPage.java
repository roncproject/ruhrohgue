package pages;

import com.microsoft.playwright.Page;

public class DeathPage {
    private final Page page;

    // Selectors — the game reuses one overlay (#gameover-screen) for death,
    // escape and quit; #go-heading's text distinguishes which one occurred.
    private final String tombstoneContainer = "#gameover-screen";
    private final String finalScoreDisplay = "#go-score";

    public DeathPage(Page page) {
        this.page = page;
    }

    public boolean isDeathScreenVisible() {
        return page.locator(tombstoneContainer).isVisible();
    }
}
