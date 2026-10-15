import {createCreditHandler} from './handler.mjs';
Deno.serve(createCreditHandler(Deno.env));
