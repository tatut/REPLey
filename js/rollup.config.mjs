import {nodeResolve} from "@rollup/plugin-node-resolve"
import terser from '@rollup/plugin-terser';

export default {
  input: "js/editor.mjs",
  output: {
    file: "resources/public/editor.js",
    format: "iife",
    name: "repl"
  },
  plugins: [nodeResolve()] //, terser()]
}