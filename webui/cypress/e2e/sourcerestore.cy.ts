const integrationDirectory = Cypress.env('TEST_ROOT')
    .replaceAll("\\", "\\\\");
const integrationData = Cypress.env('TEST_DATA')
    .replaceAll("\\", "\\\\");
const backupLocation = Cypress.env('TEST_BACKUP')
    .replaceAll("\\", "\\\\");
const shareLocation = Cypress.env('TEST_SHARE')
    .replaceAll("\\", "\\\\");
const configInterface = Cypress.env("CONFIG_INTERFACE")

it('sourcerestore', function () {
    cy.visit(configInterface);

    cy.get("#loading").should('not.be.visible');
    cy.get('#pageSettings > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#showConfiguration').click();
    cy.get('#configurationTextField')
        .clear({force: true})
        .type('{\n' +
            '  "sets": [{\n' +
            '    "id": "home", "destinations": ["d0"],\n' +
            '    "roots": [{"path": "' + integrationData + '" }]\n' +
            '  }],\n' +
            '  "destinations": {\n' +
            '    "d0": {\n' +
            '      "type": "FILE", "encryption": "AES256", "errorCorrection": "NONE",\n' +
            '      "endpointUri": "' + backupLocation + '"\n' +
            '    }\n' +
            '  },\n' +
            '  "additionalSources": {\n' +
            '    "same": {\n' +
            '      "type": "FILE", "encryption": "AES256", "errorCorrection": "NONE",\n' +
            '      "endpointUri": "' + backupLocation + '"\n' +
            '    },\n' +
            '    "share": {\n' +
            '      "type": "FILE", "encryption": "AES256", "errorCorrection": "NONE",\n' +
            '      "endpointUri": "' + shareLocation + '"\n' +
            '    }\n' +
            '  },\n' +
            '  "manifest": {\n' +
            '    "destination": "d0", "pauseOnBattery": false, "hideNotifications": true, "maximumUnsyncedSeconds": 1\n' +
            '  }\n' +
            '}', {force: true, parseSpecialCharSequences: false})
        .blur({force: true});
    cy.get("#submitConfigChange").click();
    cy.get('#acceptButton').contains("Save").should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#acceptButton').contains("Start Backup").should("be.visible").and('not.be.disabled');

    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click()
    cy.get("#restoreSource").click();
    cy.get('ul > li[data-value="same"]').click()
    cy.get('#restorePassword').clear().type('KqNK4bFj8ZTc');
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#currentProgress').contains("Browsing same Contents");
    cy.get('#cancelButton').should("be.visible").and('not.be.disabled').click();

    cy.wait(7500);

    cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').and('not.be.disabled').click();
    cy.get('#currentProgress').contains("Currently Inactive");

    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get("#restoreSource").click();
    cy.get('ul > li[data-value="same"]').click()
    cy.get('#restorePassword').clear().type('KqNK4bFj8ZTc');
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#currentProgress').contains("Browsing same Contents");
    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('.fileTreeList').find(".treeRow").should('have.length.at.least', 2);
    cy.get('.fileTreeList .treeRow #checkbox__').check({force: true});
    cy.get('#originalLocation').check();
    cy.get('#acceptButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#currentProgress').contains("Restore From same In Progress");
    cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get('#currentProgress').contains("Browsing same Contents", {timeout: 10 * 60 * 1000});
    cy.get('#cancelButton').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#currentProgress').contains("Currently Inactive");
});
