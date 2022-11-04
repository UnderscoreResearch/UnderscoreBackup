it('sharerestore', function() {
  cy.visit('http://localhost:12345/fixed/');

  cy.get('#pageShare > .MuiListItemText-root > .MuiTypography-root').click();
  cy.get("#loading").should('not.be.visible');
  cy.get('#restorePassphrase').clear().type('12345');
  cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
  cy.get('#new-item').click();
  cy.get('#share-name').clear().type('share');
  cy.get('#generate-key').click();
  cy.get('#localFileText').clear().type(Cypress.env('TEST_SHARE'));
  cy.get('.fileTreeList').find(".treeRow").should('have.length.at.least', 2);
  cy.get('.fileTreeList .treeRow #checkbox__').check({force: true});
  cy.get('#acceptButton').contains("Save").should("be.visible").and('not.be.disabled').click();
  cy.get("#loading").should('not.be.visible');
  cy.get('#acceptButton').contains("Activate Shares").should("be.visible").and('not.be.disabled').click();
  cy.get("#loading").should('not.be.visible');
  cy.get('#currentProgress').contains("Currently Inactive");

  cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click()
  cy.get("#cancelButton").should("be.visible").and('not.be.disabled').click();
  cy.get("#loading").should('not.be.visible');
  cy.get("#restoreSource").click();
  cy.get('ul > li[data-value="share"]').click()
  cy.get('#restorePassphrase').clear().type('12345');
  cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
  cy.get("#loading").should('not.be.visible');
  cy.get('#currentProgress').contains("Browsing share Contents");
  cy.get('#cancelButton').should("be.visible").and('not.be.disabled').click();

  cy.wait(5000);

  cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').and('not.be.disabled').click();
  cy.get('#currentProgress').contains("Currently Inactive");

  cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
  cy.get("#restoreSource").click();
  cy.get('ul > li[data-value="share"]').click()
  cy.get('#restorePassphrase').clear().type('12345');
  cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
  cy.get("#loading").should('not.be.visible');
  cy.get('#currentProgress').contains("Browsing share Contents");
  cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
  cy.get('.fileTreeList').find(".treeRow").should('have.length.at.least', 2);
  cy.get('.fileTreeList .treeRow #checkbox__').check({force: true});
  cy.get('#originalLocation').check();
  cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
  cy.get("#loading").should('not.be.visible');
  cy.get('#currentProgress').contains("Restore From share In Progress");
  cy.get("#loading").should('not.be.visible');
  cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
  cy.get('#currentProgress').contains("Browsing share Contents", {timeout: 10 * 60 * 1000});
  cy.get("#loading").should('not.be.visible');
  cy.get('#cancelButton').should("be.visible").and('not.be.disabled').click();
  cy.get("#loading").should('not.be.visible');
  cy.get('#currentProgress').contains("Currently Inactive");
});
