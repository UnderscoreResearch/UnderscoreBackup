const integrationDirectory = Cypress.env('TEST_ROOT')
    .replaceAll("\\", "\\\\");
const integrationData = Cypress.env('TEST_DATA')
    .replaceAll("\\", "\\\\");
const backupLocation = Cypress.env('TEST_BACKUP')
    .replaceAll("\\", "\\\\");

it('sourcerestore', function () {
    cy.visit('http://localhost:12345/fixed/');
    cy.get('#pageSettings > .MuiListItemText-root > .MuiTypography-root').click();
    cy.get('#showConfiguration').click();
    cy.get('#configurationTextField').clear({force: true});
    cy.get('#configurationTextField').type('{\n' +
        '  "sets": [{\n' +
        '    "id": "home",\n' +
        '    "roots": [{"path": "' + integrationData + '" }],\n' +
        '    "destinations": ["d0"]\n' +
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
        '    }\n' +
        '  },\n' +
        '  "manifest": {\n' +
        '    "destination": "d0",\n' +
        '    "pauseOnBattery": false,\n' +
        '    "localLocation": "' + integrationDirectory + '"\n' +
        '  }\n' +
        '}', {force: true, parseSpecialCharSequences: false});
    cy.get("#submitConfigChange").click();
    cy.get('#acceptButton').contains("Save").click();
    cy.get('#acceptButton').should('not.be.disabled').contains("Start Backup");

    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').click()
    cy.get("#restoreSource").click();
    cy.get('ul > li[data-value="same"]').click()
    cy.get('#restorePassphrase').clear();
    cy.get('#restorePassphrase').type('1234');
    cy.get('#acceptButton').click();
    cy.get('#currentProgress').contains("Browsing same Contents", {timeout: 10 * 1000});
    cy.get('#cancelButton').click();
    cy.get('#currentProgress').contains("Currently Inactive", {timeout: 10 * 1000});

    cy.get('#pageRestore > .MuiListItemText-root > .MuiTypography-root').click();
    cy.get("#restoreSource").click();
    cy.get('ul > li[data-value="same"]').click()
    cy.get('#restorePassphrase').clear();
    cy.get('#restorePassphrase').type('1234');
    cy.get('#acceptButton').click();
    cy.get('#currentProgress').contains("Browsing same Contents", {timeout: 10 * 1000});
    cy.wait(2000);
    cy.get('#checkbox__').check();
    cy.get('#originalLocation').check();
    cy.get('#acceptButton').click();
    cy.get('#currentProgress').contains("Restore From same In Progress", {timeout: 10 * 1000});
    cy.get('#currentProgress').contains("Browsing same Contents", {timeout: 10 * 60 * 1000});
    cy.get('#cancelButton').click();
    cy.get('#currentProgress').contains("Currently Inactive", {timeout: 10 * 1000});
});
