export function resetSceneScroll(scroller: HTMLElement | null) {
  scroller?.scrollTo({top: 0, left: 0, behavior: 'instant'});
}

export function focusScene(target: HTMLElement | null, scroller: HTMLElement | null) {
  resetSceneScroll(scroller);
  target?.focus({preventScroll: true});
}
