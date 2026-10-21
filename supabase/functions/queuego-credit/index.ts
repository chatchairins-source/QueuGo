import {createCreditHandler,creditReadiness} from './handler.mjs';
const configured= Deno.env.get('OMISE_SECRET_KEY') || '';
const normalized=configured.trim();
const keyKind=normalized.startsWith('skey_test_')?'test':normalized.startsWith('skey_live_')?'live':/^skey_[A-Za-z0-9]+$/.test(normalized)?'legacy_live':normalized.startsWith('pkey_')?'public_key':normalized?'unrecognized':'absent';
console.info('queuego-credit-readiness '+JSON.stringify(creditReadiness(Deno.env)));
console.info('queuego-credit-config-diagnostics '+JSON.stringify({omise_secret_present:!!configured,omise_secret_kind:keyKind,outer_whitespace:configured!==normalized,wrapped_in_quotes:/^["']|["']$/.test(normalized)}));
Deno.serve(createCreditHandler(Deno.env));
