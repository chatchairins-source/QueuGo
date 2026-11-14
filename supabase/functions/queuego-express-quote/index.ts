import {createExpressQuoteHandler} from './handler.mjs';
Deno.serve(createExpressQuoteHandler(Deno.env));
