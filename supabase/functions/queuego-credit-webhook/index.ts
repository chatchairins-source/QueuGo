import {createCreditHandler} from '../queuego-credit/handler.mjs';
Deno.serve(createCreditHandler(Deno.env, fetch, true));
