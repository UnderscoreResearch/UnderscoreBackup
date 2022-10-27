const integrationDirectory = Cypress.env('TEST_ROOT')
    .replaceAll("\\", "\\\\");
const integrationData = Cypress.env('TEST_DATA')
    .replaceAll("\\", "\\\\");
const backupLocation = Cypress.env('TEST_BACKUP')
    .replaceAll("\\", "\\\\");

const backupLocationRaw = Cypress.env('TEST_BACKUP');

it('backup', function () {
    cy.visit('http://localhost:12345/fixed/');
    cy.get('#selectType').click();
    cy.get('#typeLocalDirectory').click();
    cy.get('#localFileText').clear();
    cy.get('#localFileText').type(backupLocationRaw);
    cy.get('#acceptButton').click();
    cy.get('#passphraseFirst').clear();
    cy.get('#passphraseFirst').type('1234');
    cy.get('#passphraseSecond').clear();
    cy.get('#passphraseSecond').type('1234');
    cy.get('#acceptButton').click();

    cy.get("#loading").should('not.be.visible');
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
        '    "destination": "d0", "pauseOnBattery": false,\n' +
        '    "localLocation": "' + integrationDirectory + '"\n' +
        '  },\n' +
        '  "properties": { "largeBlockAssignment.maximumSize": "262144" }\n' +
        '}', {force: true, parseSpecialCharSequences: false})
        .blur({force: true});
    cy.get("#submitConfigChange").click();
    cy.get('#acceptButton').contains("Save").click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#acceptButton').should('not.be.disabled').contains("Start Backup").click();
    cy.get("#loading").should('not.be.visible');
    cy.get('#pageStatus > .MuiListItemText-root > .MuiTypography-root').should('not.be.disabled').click();
    cy.get('#currentProgress').contains("Backup In Progress");
    cy.get('#currentProgress').contains("Currently Inactive", {timeout: 10 * 60 * 1000});
});
