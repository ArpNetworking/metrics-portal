var gulp = require('gulp');
var ts = require('gulp-typescript');

var tsProject = ts.createProject('tsconfig.json');

gulp.task('default', function() {
    console.log("We're starting...")
    console.log("tsProject.config = ", tsProject.config);
    var tsResult = tsProject.src()
        .pipe(tsProject());

    return tsResult.js.pipe(gulp.dest('target/sbt/web/public/main/javascripts'));
});
