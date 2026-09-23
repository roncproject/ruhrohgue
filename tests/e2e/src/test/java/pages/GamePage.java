package pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.TimeoutError;

public class GamePage {
    private final Page page;

    // Selectors — match the real ids in templates/index.html
    private final String terminalGrid = "#dungeon-grid";
    private final String playerIcon = "[data-type='player']";

    // Generous on purpose: CI runners are several times slower than a laptop.
    private static final double RENDER_TIMEOUT_MS = 10000;

    public GamePage(Page page) {
        this.page = page;
    }

    // #dungeon-grid is already visible behind the new-game overlay before
    // POST /new returns, so waiting on the grid proves nothing. The player
    // cell only exists once renderMap() has run.
    public void waitForMapToLoad() {
        page.locator(terminalGrid).waitFor(new Locator.WaitForOptions().setTimeout(RENDER_TIMEOUT_MS));
        page.locator(playerIcon).waitFor(new Locator.WaitForOptions().setTimeout(RENDER_TIMEOUT_MS));
    }

    public boolean isMapVisible() {
        return page.locator(terminalGrid).isVisible();
    }

    public boolean isPlayerVisible() {
        try {
            page.locator(playerIcon).waitFor(new Locator.WaitForOptions().setTimeout(RENDER_TIMEOUT_MS));
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }
}
