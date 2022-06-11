const backupLocation = Cypress.env('TEST_BACKUP');

it('setup', function () {
    cy.visit('http://localhost:12345/fixed/');
    cy.get('#tabLocalDirectory').click();
    cy.get('#localFileText').clear();
    cy.get('#localFileText').type(backupLocation);
    cy.get('#acceptButton').click();
    cy.get('#passphraseFirst').clear();
    cy.get('#passphraseFirst').type('1234');
    cy.get('#passphraseSecond').clear();
    cy.get('#passphraseSecond').type('1234');
    cy.get('#acceptButton').click();
});
