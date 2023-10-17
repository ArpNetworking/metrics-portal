import gulp from 'gulp';
import tscript from 'gulp-typescript';
import {deleteAsync as del} from 'del';
import npmDist from 'gulp-npm-dist';

const tsProject = tscript.createProject('tsconfig.json');

export function ts() {
    console.log("We're starting...")
    console.log("tsProject.config = ", tsProject.config);
    var tsResult = tsProject.src()
        .pipe(tsProject());

    return tsResult.js.pipe(gulp.dest('target/sbt/web/public/main/javascripts'));
}

export function copy() {
    return gulp.src(npmDist(), {base:'./node_modules'})
        .pipe(gulp.dest('target/sbt/web/public/main/lib'));
}

export function clean() {
    // You can use multiple globbing patterns as you would with `gulp.src`,
    // for example if you are using del 2.0 or above, return its promise
    return del([ 'target/sbt/web/public/main' ]);
}

const build = gulp.series(clean, gulp.parallel(ts, copy));

export default build;
