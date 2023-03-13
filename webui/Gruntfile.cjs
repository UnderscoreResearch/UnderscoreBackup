module.exports = function (grunt) {

    grunt.initConfig({
        pkg: grunt.file.readJSON('package.json'),

        clean : {
            src : [
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
        }
    });

    grunt.loadNpmTasks('grunt-contrib-clean');
    grunt.loadNpmTasks('grunt-exec');
    grunt.registerTask('default', ['clean', 'exec:build']);
}