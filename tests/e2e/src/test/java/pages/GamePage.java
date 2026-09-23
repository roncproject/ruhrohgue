package pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;

public class GamePage {
    private final Page page;

    // Selectors — match the real ids in templates/index.html
    private final String terminalGrid = "#dungeon-grid";
    private final String playerIcon = "[data-type='player']";

    public GamePage(Page page) {
        this.page = page;
    }

    public void waitForMapToLoad() {
        page.locator(terminalGrid).waitFor(new Locator.WaitForOptions().setTimeout(5000));
    }

    public boolean isMapVisible() {
        return page.locator(terminalGrid).isVisible();
    }

    public boolean isPlayerVisible() {
        return page.locator(playerIcon).isVisible();
    }
}
