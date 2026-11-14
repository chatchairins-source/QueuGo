import {createRouteBundleMatrixHandler} from './handler.mjs';
Deno.serve(createRouteBundleMatrixHandler(Deno.env));
