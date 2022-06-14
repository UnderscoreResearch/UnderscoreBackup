const backupLocation = Cypress.env('TEST_BACKUP')

it('setuprebuild', function () {
    cy.visit('http://localhost:12345/fixed/');
    cy.get('#selectType').click();
    cy.get('#typeLocalDirectory').click();
    cy.get('#localFileText').clear();
    cy.get('#localFileText').type(backupLocation);
    cy.get('#acceptButton').click();
    cy.get('#restorePassphrase').clear();
    cy.get('#restorePassphrase').type('1234');
    cy.get('#acceptButton').click();
    cy.get('#currentProgress').contains("Currently Inactive", {timeout: 60 * 1000});

    cy.get('#pageSettings > .MuiListItemText-root > .MuiTypography-root').click();
    cy.get('#showChangePassword').click();

    cy.get('#oldPassphrase').clear();
    cy.get('#oldPassphrase').type('1234');

    cy.get('#passphraseFirst').clear();
    cy.get('#passphraseFirst').type('12345');
    cy.get('#passphraseSecond').clear();
    cy.get('#passphraseSecond').type('12345');
    cy.get("#submitPasswordChange").click();
});
