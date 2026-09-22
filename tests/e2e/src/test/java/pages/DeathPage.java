package pages;

import com.microsoft.playwright.Page;

public class DeathPage {
    private final Page page;

    // Pas deze selectors aan op basis van je legendarische RIP grafsteen HTML element
    private final String tombstoneContainer = "#tombstone";
    private final String finalScoreDisplay = ".final-score";

    public DeathPage(Page page) {
        this.page = page;
    }

    public boolean isDeathScreenVisible() {
        return page.locator(tombstoneContainer).isVisible();
    }
}
