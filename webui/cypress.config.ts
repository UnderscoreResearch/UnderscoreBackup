import {defineConfig} from "cypress";

export default defineConfig({
    projectId: "1ovgvq",
    viewportHeight: 1200,
    viewportWidth: 1000,
    video: true,
    videoCompression: true,
    defaultCommandTimeout: 15000,
    trashAssetsBeforeRuns: false,

    e2e: {
        experimentalStudio: true,
        setupNodeEvents(on, config) {
            // implement node event listeners here
        },
    },
});
