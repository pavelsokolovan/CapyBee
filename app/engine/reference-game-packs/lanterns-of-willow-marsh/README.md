# Lanterns of Willow Marsh

A cooperative mystery for ages 8–12 about restoring a marsh beacon before an evening lantern festival. Every choice is safe; postponing is a responsible ending, never a punishment. A complete route takes about 15–25 minutes.

Generated presentation may use the configured AI text provider. Trusted choices, consequences, routes, objectives, and outcomes are always authored, and a complete reviewed English version is available offline. Use the normal CLI with this pack directory; forced-fallback mode needs no provider. During play, `/save` and `/quit` preserve the run, which can be selected on the next launch.

```powershell
./dev.ps1 game `
  --pack-dir ./app/engine/reference-game-packs/lanterns-of-willow-marsh `
  --generated
```

Normal play saves automatically; relaunch that command to continue. Use `/help` for commands, `/save` to see the saved revision, `/quit` to pause safely, and `/abandon` only to end a run permanently. `--memory` is temporary, while `--offline` forces the reviewed provider-free version. When a provider is available, setup explains the optional transfer before asking for consent; setup free text is not persisted.

Content notes: mild time pressure and changing weather; no danger, combat, injury, villains, blame, or frightening failure.

## Developer guide (spoilers)

Every route has eight choices and visits nine scenes. The opening branches through `reed-bank` or `orchard-path`, converges at `old-boathouse`, and ends at one of three ending scenes.

- Success: `follow-reeds`, `listen-with-pip`, `share-discovery`, `tune-by-listening`, `coordinate-gate`, `repair-carefully`, `guide-everyone`, `light-together`.
- Partial: `inspect-orchard`, `trace-wheel-marks`, `inspect-lens-first`, `follow-the-diagram`, `brace-and-measure`, `make-safe-patch`, `mark-path-first`, `hold-gentle-lighting`.
- Failure: use either route, then select `postpone-with-plan`.

Change the pack version whenever trusted mechanics, route meaning, generation context, or serialized content changes. Update fallbacks, truth phrases, deterministic corpus, transcripts, and traceability together. Setup and free text may influence presentation only. Add a locale only when all strings, fallbacks, quality resources, and review evidence are complete.

The frozen release workload is certification suite `willow-marsh-v1`. Any pack version or content change invalidates its previous pack digest and requires review of the suite followed by new offline and any claimed live certification.
