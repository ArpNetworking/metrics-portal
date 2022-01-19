// to support hot reloading in development mode, files are written to sbt-web folder.
// for a production build, files are written to a folder which is configured
// to be bundled into the public assets folder.
const path = require('path');
const webpack = require('webpack');
const TsconfigPathsPlugin = require('tsconfig-paths-webpack-plugin');

var DEBUG = !process.argv.production;

var GLOBALS = {
  'process.env.NODE_ENV': DEBUG ? '"development"' : '"production"',
  '__DEV__': DEBUG
};


function getOutputPath(mode) {
  const dir = (mode === 'production') ? 'target/webpack/js' : 'target/web/public/main/js';
  return path.resolve(__dirname, dir);
}



module.exports = (env, argv) => {
  return {
    // Main entry directory and file
    entry: {
      app: [
        // 'webpack/hot/dev-server',
        path.join(__dirname, 'app', 'assets', 'javascripts', 'metrics_portal.ts')
      ]
    },

    // Output directories and file
    output: {
      path: getOutputPath(argv.mode),
      filename: '[name].js',
      sourceMapFilename: "[name].map",
      publicPath: '/assets/javascripts/'
    },

    // Custom plugins
    plugins: [
      // new webpack.DefinePlugin(GLOBALS),
    ]
        .concat(DEBUG ? [] : [
          new webpack.optimize.DedupePlugin(),
          new webpack.optimize.UglifyJsPlugin(),
          new webpack.optimize.AggressiveMergingPlugin()
        ]),

    module: {
      rules: [
        {
          test: /\.tsx?$/,
          use: 'ts-loader',
          exclude: /node_modules/,
        },
        {
          test: /\.html$/,
          use: 'html'
        },
        {
          test: /\.json$/,
          use: 'json'
        }
      ],
    },
    context: path.join(__dirname, 'app', 'assets', 'javascripts'),

    resolve: {
      extensions: ['.tsx', '.ts', '', '.js', '.jsx', '.json'],
      plugins: [new TsconfigPathsPlugin({})],

      modules: [
        'node_modules',
        'app/assets/javascripts'
      ],

      roots: [path.join(__dirname, 'app/assets/javascripts/')],

      alias: {
        'durandal': 'durandal-es6'
        // plugins: 'durandal-es6/js/plugins'
      }
    },

    devServer: {
      contentBase: __dirname,
      hot: false,
      inline: true,
      historyApiFallback: true,
      stats: {colors: true},
      progress: true
    }
  }
};
