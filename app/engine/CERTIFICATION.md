# Engine CLI certification

Normal builds and tests are offline. Live certification is a separate, explicit, paid operation and never runs during `mvn verify`.

## Offline release gate

Build the release directory and run all three Willow Marsh fallback routes:

```powershell
./dev.ps1 game-package
./dev.ps1 game-certify-offline
```

The authoritative unpacked command is:

```text
java -jar bin/engine-cli.jar certify --mode offline --pack-dir packs/lanterns-of-willow-marsh --suite certification/willow-marsh-v1/suite.json --evidence-dir evidence/offline
```

Offline mode never constructs a provider, even when credentials exist. It uses isolated stores, disables prefetch, completes the success, partial-success, and responsible-postponement routes, and restarts the partial route after its fourth accepted choice.

## Live certification

Set `GENERATION_PROVIDER_MODE=certification`, `GENERATION_MODEL_ID` to an explicitly pinned model ID, and `ANTHROPIC_API_KEY` through the operator's secret store. Do not place a key in arguments, files, shell history, or logs. `ANTHROPIC_DEBUG` must be unset or false.

Prepare a pricing file conforming to `certification/willow-marsh-v1/schemas/provider-pricing-v1.schema.json`. Its provider/model must match, its review timestamp must be no older than seven days, and its decimal rates and conservative per-call reservation must be reviewed against the provider's current price page.

```text
java -jar bin/engine-cli.jar certify --mode live --scope full --pack-dir packs/lanterns-of-willow-marsh --suite certification/willow-marsh-v1/suite.json --pricing-file certification/provider-pricing.json --max-provider-calls <reviewed-limit> --max-cost-usd <reviewed-limit> --evidence-dir evidence/live --confirm-live-provider-cost
```

Use `--scope canary` only for diagnostics; it is never a full certificate. Each run receives a new UUID and evidence directory. Live execution enforces admission before every call, exact returned-model identity, usage presence, and the 27-second attempt ceiling. A failure never disables authored fallback play.

Evidence contains identifiers, hashes, counts, timings, usage, costs, and stable result codes only. It excludes prompts, response JSON, generated prose, setup free text, credentials, and raw save data. A finalized evidence directory has no `.incomplete` suffix and includes `checksums.sha256`.

Capability labels mean:

- `development`: locally produced; no production certification claim.
- `offline-ready`: deterministic verification and packaged offline certification passed.
- `live-certified`: the exact distribution/provider/model/pack/pricing tuple has an unexpired full certificate.

A live certificate expires after 14 days by policy and is invalid immediately when any tuple digest changes.
