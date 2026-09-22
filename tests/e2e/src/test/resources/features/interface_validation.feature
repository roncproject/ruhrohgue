Feature: Pre-Cloudflare Local Acceptance Verification with Page Objects
  As a QA Engineer
  I want to validate core layout behaviors, tab titles, and screens using Page Objects
  So that the test suite code is highly maintainable before pushing to production

  Scenario: Validate entry page layout parameters and browser metadata
    Given I boot the local browser environment against the acceptance port
    Then the active tab title must be exactly "RuhRohgue"
    And the character name text input field must be visible on the Start screen
    And the character class dropdown option selector must be visible on the Start screen
    And the optional execution seed text input field must be visible on the Start screen

  Scenario: Execute deterministic match initialization on fixed seed
    Given I boot the local browser environment against the acceptance port
    When I type "ABC" and seed "20" on the Start screen
    And I click the action button to descend into the dungeon
    Then the Game screen must render the interactive ASCII grid map successfully
    And the player character icon should be present on the grid
