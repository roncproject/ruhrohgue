Feature: Pre-Cloudflare Local Acceptance Verification with Page Objects
  As a QA Engineer
  I want to validate core layout behaviors, tab titles, and screens using Page Objects
  So that the test suite code is highly maintainable before pushing to production

  Scenario: Validate entry page layout parameters and browser metadata
    Given I boot the local browser environment against the acceptance port
    Then the active tab title must be exactly "RuhRohgue"
    And the character name text input field must be visible on the Start screen
    And the character class dropdown option selector must be visible on the Start screen
    And the name-format hint must be visible on the Start screen
    And the descend button must be visible on the Start screen

  Scenario: Descend into a new game from the Start screen
    Given I boot the local browser environment against the acceptance port
    When I type "ABC" on the Start screen
    And I click the action button to descend into the dungeon
    Then the Game screen must render the interactive ASCII grid map successfully
    And the player character icon should be present on the grid
    And the status panel must be visible on the Game screen
    And the inventory panel must be visible on the Game screen
    And the message log must be visible on the Game screen

  Scenario: Quit a game from the Game screen
    Given I boot the local browser environment against the acceptance port
    When I type "ABC" on the Start screen
    And I click the action button to descend into the dungeon
    Then the Game screen must render the interactive ASCII grid map successfully
    When I quit the game
    Then the quit screen must show "You Quit"

  Scenario: Privacy banner shows on first load and can be dismissed
    Given I boot the local browser environment against the acceptance port
    Then the privacy banner must be visible
    When I dismiss the privacy banner
    Then the privacy banner must no longer be visible

  Scenario: Offline banner appears when the connection drops
    Given I boot the local browser environment against the acceptance port
    When the network connection is lost
    Then the offline banner must become visible
