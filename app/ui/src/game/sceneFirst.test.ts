import {expect, it} from 'vitest';
import {anonymousNotePackId} from './anonymousNote';
import {decodeSummary} from './gameDecoders';
import contracts from './fixtures/contracts.json';
import {illustratedRun} from './fixtures/illustratedRun';
import {hiveExpeditionPackId} from './hiveExpedition';
import {sceneFirstKind} from './sceneFirst';

it('keeps the episode layout even when presentation is available', () => {
  const run = {...illustratedRun(), packId: anonymousNotePackId};
  expect(sceneFirstKind(run)).toBe('episode');
  expect(sceneFirstKind({...run, view: null})).toBe('episode');
});

it('keeps the hive expedition layout for retained saves', () => {
  const run = {...decodeSummary(contracts.packV1), packId: hiveExpeditionPackId};
  expect(sceneFirstKind(run)).toBe('expedition');
  expect(sceneFirstKind({...run, view: null})).toBe('expedition');
});

it('selects the expedition layout from accepted presentation with an unknown or missing pack ID', () => {
  const run = illustratedRun();
  expect(sceneFirstKind(run)).toBe('expedition');
  expect(sceneFirstKind({...run, packId: null})).toBe('expedition');
});

it('keeps text-first play without accepted presentation or a special pack', () => {
  const legacy = decodeSummary(contracts.packV1);
  expect(sceneFirstKind(legacy)).toBeNull();
  expect(sceneFirstKind({...legacy, view: null})).toBeNull();
  const illustrated = illustratedRun();
  expect(sceneFirstKind({...illustrated, view: {...illustrated.view, presentation: null}})).toBeNull();
});

it.each(['__proto__', 'constructor', '<script>alert(1)</script>'])('ignores the opaque pack ID %s without presentation', packId => {
  expect(sceneFirstKind({...decodeSummary(contracts.packV1), packId})).toBeNull();
});
