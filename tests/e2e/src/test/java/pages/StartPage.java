package pages;

import com.microsoft.playwright.Page;

public class StartPage {
    private final Page page;

    // Selectors — match the real ids in templates/index.html
    private final String nameInput = "#input-name";
    private final String classSelect = "#input-role";
    private final String descendButton = "#btn-start";

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

    public void fillForm(String name) {
        page.fill(nameInput, name);
    }

    public void clickDescend() {
        page.click(descendButton);
    }
}
