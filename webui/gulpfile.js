const gulp = require("gulp");
const inlinesource = require("gulp-inline-source");
const replace = require("gulp-replace");

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

gulp.task('default', gulp.parallel('index', 'copy-woff', 'copy-woff2', 'copy-ttf', 'copy-ico', 'copy-manifest'));
