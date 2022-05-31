const backupLocation = Cypress.env('TEST_BACKUP')

it('setup', function() {
  cy.visit('http://localhost:12345/fixed/');
  cy.get('#tabLocalDirectory').click();
  cy.get('#localFileText').clear();
  cy.get('#localFileText').type(backupLocation);
  cy.get('#acceptButton').click();
  cy.get('#restorePassphrase').clear();
  cy.get('#restorePassphrase').type('1234');
  cy.get('#acceptButton').click();
  cy.get('#currentProgress').contains("Currently Inactive", { timeout: 60 * 1000});
});
