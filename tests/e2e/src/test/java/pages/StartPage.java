package pages;

import com.microsoft.playwright.Page;

public class StartPage {
    private final Page page;

    // Selectors
    private final String nameInput = "input[name='character-name']";
    private final String classSelect = "select[name='character-class']";
    private final String seedInput = "input[name='seed']";
    private final String descendButton = "#descend-btn";

    public StartPage(Page page) {
        this.page = page;
    }

    public void navigateTo(String url) {
        page.navigate(url);
    }

    public String getTitle() {
        return page.title();
    }

    public boolean isNameInputVisible() {
        return page.locator(nameInput).isVisible();
    }

    public boolean isClassSelectVisible() {
        return page.locator(classSelect).isVisible();
    }

    public boolean isSeedInputVisible() {
        return page.locator(seedInput).isVisible();
    }

    public void fillForm(String name, String seed) {
        page.fill(nameInput, name);
        page.fill(seedInput, seed);
    }

    public void clickDescend() {
        page.click(descendButton);
    }
}
