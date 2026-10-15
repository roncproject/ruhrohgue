package pages;

import com.microsoft.playwright.Page;

public class QuitPage {
    private final Page page;

    // There is no dedicated quit modal: quitting (Q, then confirmed with y)
    // reuses the same #gameover-screen overlay as death/escape, with
    // #go-heading's text reading "You Quit". No confirm button exists -
    // confirmation is a lowercase "y" keypress, not a click or a second Q
    // (verified against hack.js's keydown handler; this comment previously
    // claimed "a second Q", which was never actually exercised until the
    // Quit scenario was added and caught it).
    private final String quitHeading = "#go-heading";

    public QuitPage(Page page) {
        this.page = page;
    }

    public boolean isQuitScreenVisible() {
        return "You Quit".equals(page.locator(quitHeading).textContent());
    }
}
