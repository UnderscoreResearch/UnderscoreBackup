module.exports = function (grunt) {

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),

        clean: {
            src: [
                "../src/main/resources/web/**",
                ".parcel-cache",
                "build"
            ],
            options: {
                force: true
            }
        },

        exec: {
            build: "parcel build  --public-url . --no-source-maps ./src/index.html --dist-dir ../src/main/resources/web"
        },

        copy: {
            main: {
                src: 'node_modules/argon2-browser/dist/argon2-bundled.min.js',
                dest: 'src/api/argon2.js',
            }
        }
    });

    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-contrib-copy');
    grunt.loadNpmTasks('grunt-exec');
    grunt.registerTask('default', ['clean', 'copy', 'exec:build']);
    grunt.registerTask('copyargon2', ['copy'])
}