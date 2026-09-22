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

    private final String ACCEPTANCE_URL = "http://localhost:8081";

    @Before
    public void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
        page = browser.newPage();
        
        // Initialiseer de Page Objects met de actieve pagina-context
        startPage = new StartPage(page);
        gamePage = new GamePage(page);
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
        assertEquals(expectedTitle, startPage.getTitle());
    }

    @Then("the character name text input field must be visible on the Start screen")
    public void the_character_name_field_must_be_visible() {
        assertTrue(startPage.isNameInputVisible());
    }

    @Then("the character class dropdown option selector must be visible on the Start screen")
    public void the_character_class_dropdown_must_be_visible() {
        assertTrue(startPage.isClassSelectVisible());
    }

    @Then("the optional execution seed text input field must be visible on the Start screen")
    public void the_optional_seed_field_must_be_visible() {
        assertTrue(startPage.isSeedInputVisible());
    }

    @When("I type {string} and seed {string} on the Start screen")
    public void i_type_character_details(String name, String seed) {
        startPage.fillForm(name, seed);
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
}
