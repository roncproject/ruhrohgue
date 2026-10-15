package pages;

import com.microsoft.playwright.Page;

public class DeathPage {
    private final Page page;

    // Selectors — the game reuses one overlay (#gameover-screen) for death,
    // escape and quit; #go-heading's text distinguishes which one occurred.
    private final String tombstoneContainer = "#gameover-screen";
    private final String heading = "#go-heading";
    private final String finalScoreDisplay = "#go-score";
    private final String killerDisplay = "#go-killer";
    private final String playAgainButton = "#btn-newgame";

    public DeathPage(Page page) {
        this.page = page;
    }

    public boolean isDeathScreenVisible() {
        return "You Died".equals(page.locator(heading).textContent());
    }

    public String getKillerText() {
        return page.locator(killerDisplay).textContent();
    }

    public boolean isPlayAgainButtonVisible() {
        return page.locator(playAgainButton).isVisible();
    }
}
