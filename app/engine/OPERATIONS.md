# Live generation operations

## Disable and fallback

Set `GENERATION_PROVIDER_MODE=disabled`. The player override `play --offline --generated` takes precedence over environment settings and credentials and completes through reviewed authored fallbacks. Do not delete or rewrite existing saves.

After disabling, run packaged offline certification, withdraw any affected live-certified label, and record the detection time, stable failure code, exact distribution/model/pack tuple, and certificate ID. Never copy prompts, provider bodies, generated prose, environment dumps, or keys into tickets.

## Failure actions

| Code | Safe response |
|---|---|
| `LIVE_PROVIDER_NOT_CONFIGURED` | Check key/model presence privately or play with `--offline`. |
| `PROVIDER_AUTHENTICATION_FAILED` | Replace or rotate the secret in secret storage. |
| `PROVIDER_PERMISSION_DENIED` | Verify account and pinned-model access outside logs. |
| `PROVIDER_BILLING_UNAVAILABLE` | Review API billing; a chat subscription is not API credit. |
| `PROVIDER_STRUCTURED_OUTPUT_UNSUPPORTED` | Select a supported pinned model and recertify. |
| `PROVIDER_RESOURCE_NOT_FOUND` | Verify the exact model ID; never fall forward to an alias. |
| `PROVIDER_RATE_LIMITED`, `PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE` | Keep fallback enabled and create a new certification run later. |
| `MODEL_ID_MISMATCH` | Do not accept alias equivalence; review and explicitly pin before recertifying. |
| `COST_LIMIT_REACHED`, `CALL_LIMIT_REACHED` | Stop. Change a limit only through explicit release review. |
| `EVIDENCE_PRIVACY_FAILED` | Stop release, restrict evidence, and rotate a potentially exposed key. |
| `PACKAGE_INTEGRITY_FAILED` | Discard the candidate and rebuild from a clean tag. |

Rollback in order: disable the provider; restore the last approved exact provider tuple; roll back the CLI distribution; roll back pack data only after save compatibility is proven.

Re-enable only after the cause is understood or service recovered, offline gates remain green, a fresh canary passes, a fresh full run passes for a live-certified claim, and the incident owner approves.
