import assert from 'node:assert/strict';

export async function checkAnonymousNote({page, context, base, requests}) {
  const episode = page.locator('.game-episode');
  const shortcut = page.locator('.adventure-resume');
  const play = page.getByRole('button', {name: 'Play', exact: true});
  const action = name => episode.getByRole('button', {name, exact: true});
  const openCatalog = async () => {
    await play.click();
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
    if (withMap) {
      await play.click();
      await shortcut.getByText('Cicha Łąka', {exact: true}).waitFor();
      assert.equal(await shortcut.getByText('Tajemnicza wiadomość', {exact: true}).isVisible(), true);
      assert.equal(await shortcut.getByText('Resume adventure', {exact: true}).isVisible(), true);
      assert.equal(await shortcut.getByText('Cicha Łąka', {exact: true}).getAttribute('lang'), 'pl');
      await context.setOffline(true);
      await page.evaluate(() => window.dispatchEvent(new Event('offline')));
      await shortcut.waitFor({state: 'detached'});
      await context.setOffline(false);
      await page.evaluate(() => window.dispatchEvent(new Event('online')));
      await shortcut.waitFor();

      // The preview was accepted before the click. Simulate it becoming stale on the next read.
      await page.evaluate(() => {
        const fetchCurrent = window.fetch;
        window.fetch = (input, options) => {
          if (input === '/api/game/runs/current' && document.querySelector('[data-preparation-phase="resuming"]')) {
            window.fetch = fetchCurrent;
            return Promise.resolve(new Response(JSON.stringify({error: {code: 'RUN_NOT_FOUND'}}), {
              status: 404, headers: {'Content-Type': 'application/json', 'Cache-Control': 'no-store'}
            }));
          }
          return fetchCurrent.call(window, input, options);
        };
      });
      await shortcut.focus();
      await page.keyboard.press('Enter');
      await page.getByText('Your saved adventure changed. Choose an adventure below.', {exact: true}).waitFor();
      assert.equal(await episode.count(), 0);
      await page.getByRole('button', {name: 'Back to Play', exact: true}).click();
      await page.locator('.adventure-overlay').waitFor({state: 'detached'});
      await page.waitForFunction(() => document.activeElement?.classList.contains('adventure-resume'));

      const catalogReads = [];
      const watchCatalog = request => {
        if (/\/api\/game\/(packs|generator-profiles)$/.test(request.url()) && request.method() === 'GET') {
          catalogReads.push(request.url());
        }
      };
      page.on('request', watchCatalog);
      await page.keyboard.press('Enter');
      await episode.getByText(new RegExp(clue)).waitFor();
      page.off('request', watchCatalog);
      assert.deepEqual(catalogReads, []);
    } else {
      await openCatalog();
      await page.getByRole('button', {name: 'Continue', exact: true}).click();
    }
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
    const endingHeading = episode.getByRole('heading', {name: 'Zagadka rozwiązana'});
    assert.equal(await endingHeading.evaluate(node => node === document.activeElement), true);
    const interest = action('Chcę kolejny odcinek');
    const beforeInterest = requests.length;
    const storage = () => page.evaluate(() => JSON.stringify([localStorage, sessionStorage]));
    const savedStorage = await storage();
    assert.equal(await interest.getAttribute('aria-pressed'), 'false');
    if (withMap) {
      await interest.focus();
      await page.keyboard.press('Space');
      assert.equal(await interest.evaluate(node => node === document.activeElement), true);
      assert.equal(await interest.getAttribute('aria-pressed'), 'true');
      await episode.getByText('Dzięki! Zaznaczono zainteresowanie.', {exact: true}).waitFor();
      await action('Tryb tekstowy').click();
      assert.equal(await art.isVisible(), false);
      assert.equal(await interest.getAttribute('aria-pressed'), 'true');
      await episode.getByText(/Twój rysunek pomógł/).waitFor();
      await interest.click();
      assert.equal(await interest.getAttribute('aria-pressed'), 'false');
      assert.equal(await episode.getByText('Dzięki! Zaznaczono zainteresowanie.', {exact: true}).count(), 0);
      await interest.click();
    }
    assert.equal(requests.length, beforeInterest);
    assert.equal(await storage(), savedStorage);
    await action('Wróć do gier').click();
    await page.locator('.adventure-overlay').waitFor({state: 'detached'});
    await shortcut.waitFor({state: 'detached'});
    if (withMap)await page.waitForFunction(() => document.activeElement?.querySelector('strong')?.textContent === 'Adventures');
    await openCatalog();
    await page.getByRole('button', {name: /Tajemnicza wiadomość/}).waitFor();
  }

  await page.getByRole('button', {name: /Tajemnicza wiadomość/}).click();
  await action('Przeczytaj wiadomość').waitFor();
  await action('Zakończ przygodę').click();
  await episode.getByRole('dialog').getByRole('button', {name: 'Zakończ przygodę', exact: true}).click();
  await episode.getByRole('heading', {name: 'Zakończono', exact: true}).waitFor();
  assert.equal(await action('Chcę kolejny odcinek').count(), 0);
  await action('Wróć do gier').click();
  await page.locator('.adventure-overlay').waitFor({state: 'detached'});
  await openCatalog();
  await page.getByRole('button', {name: /Tajemnicza wiadomość/}).waitFor();

  await page.setViewportSize(originalViewport);
  console.log('PASS: Anonymous Note both branches, text mode, fresh Play shortcut, stale and offline handling, focus, exact reload and catalog resume');
}
