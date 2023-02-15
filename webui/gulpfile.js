import gulp from "gulp";
import {deleteAsync} from "del";
import {exec} from "child_process";

gulp.task('copy', () => {
    return gulp.src('./build/**').pipe(gulp.dest('../src/main/resources/web'));
});

gulp.task('delete-parcel-cache', () => {
    return deleteAsync('.parcel-cache/**', {force: true});
});

gulp.task('delete-resources', () => {
    return deleteAsync('../src/main/resources/web/**', {force: true});
});

gulp.task('delete-build', () => {
    return deleteAsync('build/**', {force: true});
});

gulp.task('delete-dist', () => {
    return deleteAsync('dist/**', {force: true});
});

gulp.task('clean', gulp.parallel('delete-parcel-cache', 'delete-build', 'delete-dist', 'delete-resources'));

gulp.task('parcel-build', function (cb) {
    exec('parcel build  --public-url . --no-source-maps ./src/index.html --dist-dir build', function (err, stdout, stderr) {
        console.log(stdout);
        console.log(stderr);
        cb(err);
    });
})

gulp.task('default', gulp.series("clean", "parcel-build", "copy"));