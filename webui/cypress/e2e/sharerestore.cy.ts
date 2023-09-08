const shareLocation = Cypress.env('TEST_SHARE');
const configInterface = Cypress.env("CONFIG_INTERFACE")

it('sharerestore', function () {
    cy.visit(configInterface);

    cy.get('#pageShare > .MuiListItemText-root > .MuiTypography-root').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#restorePassword').clear().type('KqNK4bFj8ZTc');
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get('#new-item').click();
    cy.get('#share-name').clear().type('share');
    cy.get('#generate-key').click();
    cy.get('#localFileText').clear().type(shareLocation);
    cy.get('.fileTreeList').find(".treeRow").should('have.length.at.least', 2);
    cy.get('.fileTreeList .treeRow #checkbox__').check({force: true});
    cy.get('#acceptButton').contains("Save").should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#acceptButton').contains("Activate Shares").should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.wait(100);
    cy.get('#currentProgress').contains("Stopped");

    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click()
    cy.get('#cancelButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#restoreSource").click();
    cy.get('ul > li[data-value="share"]').click()
    cy.get('#restorePassword').clear().type('KqNK4bFj8ZTc');
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#currentProgress').contains("Browsing share Contents");
    cy.get('#cancelButton').should("be.visible").and('not.be.disabled').click();

    cy.wait(7500);

    cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').and('not.be.disabled').click();
    cy.get('#currentProgress').contains("Stopped");

    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get("#restoreSource").click();
    cy.get('ul > li[data-value="share"]').click()
    cy.get('#restorePassword').clear().type('KqNK4bFj8ZTc');
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
    cy.get('#currentProgress').contains("Stopped");
});
