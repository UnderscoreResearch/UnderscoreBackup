import gulp from "gulp";
import inlinesource from "gulp-inline-source";
import replace from "gulp-replace";
import {deleteAsync} from "del";
import {exec} from "child_process";

gulp.task("index", () => {
    return gulp
        .src("./build/index.html")
        .pipe(replace('<meta charset="utf-8">', ""))
        .pipe(replace("<head>", '<head><meta charset="utf-8">'))
        .pipe(replace(/type=\"module\" src=\"(\/[^\"]*\.js)\"\>\<\/script\>/, 'src=".$1" inline></script>'))
        .pipe(replace(/rel=\"stylesheet\" href=\"(\/[^\"]*\.css)\"\>/, 'rel="stylesheet" href=".$1" inline>'))
        .pipe(
            inlinesource()
        )
        .pipe(gulp.dest("../src/main/resources/web"));
});

gulp.task('copy-woff', () => {
    return gulp.src('./build/*.woff').pipe(gulp.dest('../src/main/resources/web'));
});

gulp.task('copy-woff2', () => {
    return gulp.src('./build/*.woff2').pipe(gulp.dest('../src/main/resources/web'));
});

gulp.task('copy-ttf', () => {
    return gulp.src('./build/*.ttf').pipe(gulp.dest('../src/main/resources/web'));
});

gulp.task('copy-ico', () => {
    return gulp.src('./build/*.ico').pipe(gulp.dest('../src/main/resources/web'));
});

gulp.task('copy-manifest', () => {
    return gulp.src('./build/manifest.webmanifest').pipe(gulp.dest('../src/main/resources/web'));
});

gulp.task('package', gulp.parallel('index', 'copy-woff', 'copy-woff2', 'copy-ttf', 'copy-ico', 'copy-manifest'));

gulp.task('delete-parcel-cache', () => {
    return deleteAsync('.parcel-cache/**', {force: true});
});

gulp.task('delete-resources', () => {
    return deleteAsync('../src/main/resources/web/**', {force: true});
});

gulp.task('delete-build', () => {
    return deleteAsync('build/**', {force: true});
});

gulp.task('clean', gulp.parallel('delete-parcel-cache', 'delete-build', 'delete-resources'));

gulp.task('parcel-build', function (cb) {
    exec('parcel build ./src/index.html --dist-dir build', function (err, stdout, stderr) {
        console.log(stdout);
        console.log(stderr);
        cb(err);
    });
})

gulp.task('default', gulp.series("clean", "parcel-build", "package"));