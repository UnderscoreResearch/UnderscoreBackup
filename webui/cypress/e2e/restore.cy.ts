const backupLocation = Cypress.env('TEST_BACKUP')
const configInterface = Cypress.env("CONFIG_INTERFACE")

it('restore', function () {
    cy.visit(configInterface);
    cy.get('#skipService').click();
    cy.get('#selectType').click();
    cy.get('#typeLocalDirectory').click();
    cy.get('#localFileText').clear();
    cy.get('#localFileText').type(backupLocation);
    cy.get('#next').should("be.visible").and('not.be.disabled').click();
    cy.get('#restorePassword').clear();
    cy.get('#restorePassword').type('bYisMYVs9Qdw');
    cy.get('#next').should("be.visible").and('not.be.disabled').click();
    cy.wait(5000);
    cy.get('#exit').should("be.visible").and('not.be.disabled').click();
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

    cy.visit(configInterface);

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
