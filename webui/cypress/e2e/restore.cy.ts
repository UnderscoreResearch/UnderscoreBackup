const backupLocation = Cypress.env('TEST_BACKUP')

it('restore', function () {
    cy.visit('http://localhost:12345/fixed/');
    cy.get('#skipService').click();
    cy.get('#selectType').click();
    cy.get('#typeLocalDirectory').click();
    cy.get('#localFileText').clear();
    cy.get('#localFileText').type(backupLocation);
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get('#restorePassword').clear();
    cy.get('#restorePassword').type('bYisMYVs9Qdw');
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get('#currentProgress').contains("Currently Inactive", {timeout: 60 * 1000});
    cy.wait(1000);
    cy.get('#pageSettings > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get('#showChangePassword').click();
    cy.get('#oldPassword').clear();
    cy.get('#oldPassword').type('bYisMYVs9Qdw');

    cy.get('#passwordFirst').clear();
    cy.get('#passwordFirst').type('KqNK4bFj8ZTc');
    cy.get('#passwordSecond').clear();
    cy.get('#passwordSecond').type('KqNK4bFj8ZTc');
    cy.get("#submitPasswordChange").click();
    cy.get("#loading").should('not.be.visible');

    cy.visit('http://localhost:12345/fixed/');

    cy.get("#loading").should('not.be.visible');
    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get('#restorePassword').clear();
    cy.get('#restorePassword').type('KqNK4bFj8ZTc');
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('.fileTreeList').find(".treeRow").should('have.length.at.least', 2);
    cy.get('.fileTreeList .treeRow #checkbox__').check({force: true});
    cy.get('#originalLocation').check();
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#currentProgress').contains("Restore In Progress");
    cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get('#currentProgress').contains("Currently Inactive", {timeout: 60 * 1000});
});
