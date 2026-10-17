import {createRideQuoteHandler} from './handler.mjs';
Deno.serve(createRideQuoteHandler(Deno.env));
