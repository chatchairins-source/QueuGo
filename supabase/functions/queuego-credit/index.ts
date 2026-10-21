import {createCreditHandler,creditReadiness} from './handler.mjs';
console.info('queuego-credit-readiness '+JSON.stringify(creditReadiness(Deno.env)));
Deno.serve(createCreditHandler(Deno.env));
