import assert from 'node:assert/strict';

export async function checkAnonymousNote({page, context, base, requests}) {
  const episode = page.locator('.game-episode');
  const action = name => episode.getByRole('button', {name, exact: true});
  const openCatalog = async () => {
    await page.getByRole('button', {name: 'Play', exact: true}).click();
    await page.getByRole('button', {name: /Adventures/}).click();
  };
  const readCurrent = async () => {
    const response = await context.request.get(base + '/api/game/runs/current');
    assert.equal(response.status(), 200);
    assert.equal(response.headers()['cache-control'], 'no-store');
    return response.json();
  };

  await page.getByRole('button', {name: 'Start another adventure', exact: true}).click();
  const originalViewport = page.viewportSize();
  await page.setViewportSize({width: 360, height: 640});

  for (const withMap of [true, false]) {
    const starts = requests.filter(r => r.url.endsWith('/api/game/runs') && r.body.packId === 'capybee-anonymous-note').length;
    await page.getByRole('button', {name: /Tajemnicza wiadomość/}).click();
    const first = action('Przeczytaj wiadomość');
    await first.waitFor();
    const box = await first.boundingBox();
    assert.ok(box && box.y >= 0 && box.y + box.height <= 640, 'First action is below the fold');
    assert.ok(box.height >= 44 && box.width >= 44);
    assert.equal(requests.filter(r => r.url.endsWith('/api/game/runs') && r.body.packId === 'capybee-anonymous-note').length, starts + 1);

    const map = episode.locator('details');
    assert.equal(await map.evaluate(node => node.open), false);
    await map.locator('summary').click();
    assert.equal(await map.locator('[aria-current="location"] strong').textContent(), 'Polana Nowego Ula');
    assert.equal(await action('Przejdź do: Cicha Łąka').count(), 0);
    await map.locator('summary').click();

    await first.click();
    await action('Odwiedź Melę').waitFor();
    if (withMap) {
      await map.locator('summary').click();
      await action('Przejdź do: Cicha Łąka').click();
    } else {
      await action('Odwiedź Melę').click();
    }
    const branch = withMap ? 'Pokaż Meli wiadomość' : 'Sprawdź ślady przy kwiatach';
    await action(branch).waitFor();
    assert.equal(await map.evaluate(node => node.open), false);
    assert.equal((await readCurrent()).view.places.currentPlaceId, 'quiet-meadow');

    const writes = requests.length;
    const art = episode.locator('.game-encounter-backdrop');
    await action('Tryb tekstowy').click();
    assert.equal(await art.isVisible(), false);
    assert.equal(await action(branch).isVisible(), true);
    assert.equal(requests.length, writes);
    await page.emulateMedia({forcedColors: 'active'});
    await action('Tryb tekstowy').click();
    assert.equal(await art.isVisible(), false);
    await page.emulateMedia({forcedColors: 'none'});
    assert.equal(await art.isVisible(), true);
    await episode.evaluate(node => { node.style.fontSize = '200%'; });
    assert.ok(await episode.evaluate(node => node.scrollWidth <= node.clientWidth));
    await episode.evaluate(node => { node.style.fontSize = ''; });

    await action(branch).click();
    const clue = withMap ? 'Znam te trzy kropki' : 'Ślady prowadzą za kwiaty';
    await episode.getByText(new RegExp(clue)).waitFor();
    const accepted = await readCurrent();
    const beforeReload = requests.length;
    await page.reload();
    await openCatalog();
    await page.getByRole('button', {name: 'Continue', exact: true}).click();
    await episode.getByText(new RegExp(clue)).waitFor();
    const restored = await readCurrent();
    assert.equal(restored.revision, accepted.revision);
    assert.deepEqual(restored.view, accepted.view);
    assert.equal(requests.length, beforeReload);

    await action('Sprawdź drogowskaz').click();
    await episode.getByText('Zagadka rozwiązana', {exact: true}).waitFor();
    await episode.getByText(withMap ? /Twój rysunek pomógł/ : /To twoje odkrycie/).waitFor();
    const ending = await (await context.request.get(base + '/api/game/runs/' + accepted.runId)).json();
    assert.equal(ending.endReason, 'COMPLETED');
    await action('Wróć do gier').click();
    await page.locator('.adventure-overlay').waitFor({state: 'detached'});
    await openCatalog();
    await page.getByRole('button', {name: /Tajemnicza wiadomość/}).waitFor();
  }

  await page.setViewportSize(originalViewport);
  console.log('PASS: Anonymous Note direct start, both branches, map and scene travel, text mode, phone layout and exact reload');
}
