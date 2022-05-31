it('restore', function() {
  cy.visit('http://localhost:12345/fixed/');
  cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').click();
  cy.get('#restorePassphrase').clear();
  cy.get('#restorePassphrase').type('1234');
  cy.get('#acceptButton').click();
  cy.wait(1000);
  cy.get('#checkbox__').check();
  cy.get('#originalLocation').check();
  cy.get('#acceptButton').click();
  cy.get('#currentProgress').contains("Restore In Progress", { timeout: 10 * 1000});
  cy.get('#currentProgress').contains("Currently Inactive", { timeout: 5 * 60 * 1000});
});
