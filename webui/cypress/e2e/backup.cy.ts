const integrationDirectory = Cypress.env('TEST_ROOT')
    .replaceAll("\\", "\\\\");
const integrationData = Cypress.env('TEST_DATA')
    .replaceAll("\\", "\\\\");
const backupLocation = Cypress.env('TEST_BACKUP')
    .replaceAll("\\", "\\\\");

const backupLocationRaw = Cypress.env('TEST_BACKUP');
const configInterface = Cypress.env("CONFIG_INTERFACE");

it('backup', function () {
    cy.visit(configInterface);
    cy.get('#skipService').click();
    cy.get('#selectType').click();
    cy.get('#typeLocalDirectory').click();
    cy.get('#localFileText').clear();
    cy.get('#localFileText').type(backupLocationRaw);
    cy.get('#next').should("be.visible").and('not.be.disabled').click();
    cy.get('#passwordFirst').clear();
    cy.get('#passwordFirst').type('bYisMYVs9Qdw');
    cy.get('#passwordSecond').clear();
    cy.get('#passwordSecond').type('bYisMYVs9Qdw');
    cy.get('#uiAuthentication').click();
    cy.get('#next').should("be.visible").and('not.be.disabled').click();
    cy.get('#exit').should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#password').type('bYisMYVs9Qdw');
    cy.get('#actionButton').should("be.visible").and('not.be.disabled').click();
    cy.get('#pageSettings > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
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
            '  "manifest": {\n' +
            '    "destination": "d0", "pauseOnBattery": false, "hideNotifications": true, ' +
            '    "authenticationRequired": true, "maximumUnsyncedSeconds": 1\n' +
            '  },\n' +
            '  "properties": { "largeBlockAssignment.maximumSize": "262144" }\n' +
            '}', {force: true, parseSpecialCharSequences: false})
        .blur({force: true});
    cy.get("#submitConfigChange").click();
    cy.get('#acceptButton').contains("Save").should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#acceptButton').contains("Backup Now").should("be.visible").and('not.be.disabled').click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get('#currentProgress').contains("Backup in progress");
    cy.get('#currentProgress').contains("Idle", {timeout: 10 * 60 * 1000});
});
