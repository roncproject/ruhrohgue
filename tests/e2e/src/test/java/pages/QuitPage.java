package pages;

import com.microsoft.playwright.Page;

public class QuitPage {
    private final Page page;

    // Pas deze selectors aan op basis van hoe je Quit-modal of pagina eruitziet
    private final String quitConfirmationText = "#quit-message"; 
    private final String confirmQuitBtn = "#confirm-quit-btn";

    public QuitPage(Page page) {
        this.page = page;
    }

    public boolean isQuitScreenVisible() {
        return page.locator(quitConfirmationText).isVisible();
    }
}
