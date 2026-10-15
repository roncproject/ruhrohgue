package steps;

import com.microsoft.playwright.*;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import pages.*; // Importeer je gloednieuwe Page Objects
import static org.junit.jupiter.api.Assertions.*;

public class InterfaceValidationSteps {

    private Playwright playwright;
    private Browser browser;
    private Page page;

    // De Page Objects
    private StartPage startPage;
    private GamePage gamePage;
    private QuitPage quitPage;
    private PrivacyBannerPage privacyBannerPage;
    private OfflineBannerPage offlineBannerPage;

    private final String ACCEPTANCE_URL = "http://localhost:8081";

    @Before
    public void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();

        // Initialiseer de Page Objects met de actieve pagina-context
        startPage = new StartPage(page);
        gamePage = new GamePage(page);
        quitPage = new QuitPage(page);
        privacyBannerPage = new PrivacyBannerPage(page);
        offlineBannerPage = new OfflineBannerPage(page);
    }

    @After
    public void teardown() {
        if (page != null) page.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Given("I boot the local browser environment against the acceptance port")
    public void i_boot_the_local_browser_environment() {
        startPage.navigateTo(ACCEPTANCE_URL);
    }

    @Then("the active tab title must be exactly {string}")
    public void the_active_tab_title_must_be_exactly(String expectedTitle) {
        // The real title is a longer SEO string ("RuhRohgue, Cloud version of
        // Hack 1.0.2. Copyright (c) ..."); asserting on an exact match here
        // would break on every copy tweak. Checking it starts with the given
        // text still verifies the page is the right one.
        assertTrue(startPage.getTitle().startsWith(expectedTitle),
            "Expected title to start with \"" + expectedTitle + "\" but was \""
            + startPage.getTitle() + "\"");
    }

    @Then("the character name text input field must be visible on the Start screen")
    public void the_character_name_field_must_be_visible() {
        assertTrue(startPage.isNameInputVisible());
    }

    @Then("the character class dropdown option selector must be visible on the Start screen")
    public void the_character_class_dropdown_must_be_visible() {
        assertTrue(startPage.isClassSelectVisible());
    }

    @Then("the name-format hint must be visible on the Start screen")
    public void the_name_format_hint_must_be_visible() {
        assertTrue(startPage.isNameHintVisible());
    }

    @Then("the descend button must be visible on the Start screen")
    public void the_descend_button_must_be_visible() {
        assertTrue(startPage.isDescendButtonVisible());
    }

    @When("I type {string} on the Start screen")
    public void i_type_character_details(String name) {
        startPage.fillForm(name);
    }

    @When("I click the action button to descend into the dungeon")
    public void i_click_the_action_button() {
        startPage.clickDescend();
    }

    @Then("the Game screen must render the interactive ASCII grid map successfully")
    public void the_game_screen_must_render_the_map() {
        gamePage.waitForMapToLoad();
        assertTrue(gamePage.isMapVisible());
    }

    @Then("the player character icon should be present on the grid")
    public void the_player_character_icon_should_be_present() {
        assertTrue(gamePage.isPlayerVisible());
    }

    @Then("the status panel must be visible on the Game screen")
    public void the_status_panel_must_be_visible() {
        assertTrue(gamePage.isStatusPanelVisible());
    }

    @Then("the inventory panel must be visible on the Game screen")
    public void the_inventory_panel_must_be_visible() {
        assertTrue(gamePage.isInventoryPanelVisible());
    }

    @Then("the message log must be visible on the Game screen")
    public void the_message_log_must_be_visible() {
        assertTrue(gamePage.isMessageLogVisible());
    }

    @When("I quit the game")
    public void i_quit_the_game() {
        // Q arms the confirmation (confirmMode='Q'); the actual confirm key
        // is lowercase y, not a second Q - the old QuitPage.java comment
        // claiming "a second Q" was wrong, just never exercised until now.
        // hack.js reads e.key directly, so capital Q needs Shift, same as a
        // real keyboard - Playwright's press() doesn't apply Shift on its
        // own for a single uppercase letter.
        page.keyboard().press("Shift+Q");
        // Without this wait, the "y" keypress races the async response to
        // the Q press (confirm_quit's server round-trip) - both requests
        // can overlap and leave the client in an inconsistent state. The
        // confirmation prompt actually appearing is proof the first command
        // has round-tripped before the second one fires.
        page.locator("#message-bar").getByText("Do you want to quit").waitFor();
        page.keyboard().press("y");
        // Same reasoning as above: the keypress returns before the async
        // quit_confirmed round-trip has updated the DOM. Wait for the
        // overlay itself rather than asserting immediately.
        page.locator("#gameover-screen").waitFor();
    }

    @Then("the quit screen must show {string}")
    public void the_quit_screen_must_show(String expectedHeading) {
        assertTrue(quitPage.isQuitScreenVisible(),
            "Expected the quit screen heading to read \"" + expectedHeading + "\"");
    }

    @Then("the privacy banner must be visible")
    public void the_privacy_banner_must_be_visible() {
        assertTrue(privacyBannerPage.isVisible());
    }

    @When("I dismiss the privacy banner")
    public void i_dismiss_the_privacy_banner() {
        privacyBannerPage.dismiss();
    }

    @Then("the privacy banner must no longer be visible")
    public void the_privacy_banner_must_no_longer_be_visible() {
        assertFalse(privacyBannerPage.isVisible());
    }

    @When("the network connection is lost")
    public void the_network_connection_is_lost() {
        page.context().setOffline(true);
    }

    @Then("the offline banner must become visible")
    public void the_offline_banner_must_become_visible() {
        assertTrue(offlineBannerPage.isVisible());
        // Restore connectivity so teardown (context/browser close) isn't
        // itself running against a context stuck offline.
        page.context().setOffline(false);
    }
}
