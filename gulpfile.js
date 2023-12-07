import gulp from 'gulp';
import tscript from 'gulp-typescript';
import {deleteAsync as del} from 'del';
import npmDist from 'gulp-npm-dist';
import rename from 'gulp-rename';


const tsProject = tscript.createProject('tsconfig.json');

export function ts() {
    var tsResult = tsProject.src()
        .pipe(tsProject());

    return tsResult.js.pipe(gulp.dest('target/sbt/web/public/main/javascripts'));
}

export function copyVendored() {
    return gulp.src('app/assets/vendored/**')
        .pipe(gulp.dest('target/sbt/web/public/main/'));
}

export function copy() {
    return gulp.src(npmDist(), {base:'./node_modules'})
        .pipe(rename(function(path) {
            path.dirname = path.dirname.replace(/\/dist/, '').replace(/\\dist/, '');
        //     path.dirname = path.dirname.replace(/\/build/, '').replace(/\\build/, '');
        }))
        .pipe(gulp.dest('target/sbt/web/public/main/lib'));
}

export function clean() {
    // You can use multiple globbing patterns as you would with `gulp.src`,
    // for example if you are using del 2.0 or above, return its promise
    return del([ 'target/sbt/web/public/main' ]);
}

const build = gulp.series(clean, gulp.parallel(ts, copy, copyVendored));

export default build;
