package pages;

import com.microsoft.playwright.Page;

public class QuitPage {
    private final Page page;

    // There is no dedicated quit modal: quitting (Q, confirmed with a second Q)
    // reuses the same #gameover-screen overlay as death/escape, with
    // #go-heading's text reading "You Quit". No confirm button exists — the
    // second Q is a keypress, not a click.
    private final String quitHeading = "#go-heading";

    public QuitPage(Page page) {
        this.page = page;
    }

    public boolean isQuitScreenVisible() {
        return "You Quit".equals(page.locator(quitHeading).textContent());
    }
}
