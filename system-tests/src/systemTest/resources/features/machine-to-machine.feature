@e2e
Feature: Machine-to-machine task access
  A registered machine client should obtain a scoped token and use it to call the protected tasks API.
  This is the non-interactive E2E path; the separate Playwright E2E suite covers browser login, consent, session use and logout.

  Scenario: Registered machine client reads tasks
    Given the valid machine client credentials
    When the machine client requests an api.read access token
    Then the authorization server issues a token for the resource server
    When the machine client calls the tasks endpoint with that token
    Then the resource server returns the subject and all three tasks

  Scenario: Invalid machine credentials are rejected
    Given invalid machine client credentials
    When the machine client requests an api.read access token
    Then the authorization server rejects the machine credentials
