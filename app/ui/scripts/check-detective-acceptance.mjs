import assert from 'node:assert/strict';
import {chromium} from 'playwright';

const base = process.argv[2];
if (!base || !/^http:\/\/127\.0\.0\.1:\d+$/.test(base)) {
  throw new Error('Use the local preparation HTTP fixture host');
}

const languages = {
  en: {
    play:'Play', catalog:/^Adventures/, card:/^Classic detective mystery/, customize:/^Customize adventure/,
    next:'Next', review:'Review', start:'Start adventure', standard:'Standard', expert:'Expert', language:'English',
    resume:/^Resume adventure/, close:'Back to Play', replace:/pause my current/, retry:'Review and retry',
    continue:'Continue', back:'Back to Adventures', inspect:'Inspect the envelope', skip:'Question the client',
    notes:'Case notes', wax:'The wax has two impressions.', clock:'The hall clock stopped before dusk.',
    steps:['Enter the hall','Inspect the clock','Check the alibi','Inspect the ledger','Compare the records',
      'Review the evidence','Make your deduction'],
    accuse:'Accuse Rin', defer:'Leave case open', solved:'Case solved', unresolved:'Case unresolved', text:'Text mode',
  },
  pl: {
    play:'Graj', catalog:/^Przygody/, card:/^Klasyczna zagadka detektywistyczna/, customize:/^Dostosuj przygodę/,
    next:'Dalej', review:'Sprawdź', start:'Rozpocznij przygodę', standard:'Standardowy', language:'Polski',
    resume:/^Wznów przygodę/, inspect:'Zbadaj kopertę', notes:'Notatki ze śledztwa',
    wax:'Wosk ma dwa odciski.', clock:'Zegar w holu zatrzymał się przed zmrokiem.',
    steps:['Wejdź do holu','Obejrzyj zegar','Sprawdź alibi','Obejrzyj księgę','Porównaj zapisy',
      'Sprawdź dowody','Wyciągnij wniosek'],
    accuse:'Oskarż Rin', solved:'Sprawa rozwiązana', text:'Tryb tekstowy',
  },
};

const browser = await chromium.launch({
  headless:true, executablePath:process.env.CHROMIUM_EXECUTABLE ?? '/usr/bin/chromium', args:['--no-sandbox'],
});
try {
  const junior = await browser.newContext({extraHTTPHeaders:{'X-Preparation-Fixture':'fixture'}});
  try {
    const response = await junior.request.get(base + '/api/game/generator-profiles');
    assert.equal(response.status(), 200);
    assert.deepEqual((await response.json()).map(profile => profile.id), ['generic-adventure']);
  } finally {
    await junior.close();
  }

  for (const [locale, copy] of Object.entries(languages)) {
    try {
      await checkLanguage(locale, copy);
    } catch (error) {
      throw new Error(`Detective acceptance failed for ${locale}`, {cause:error});
    }
  }
  console.log('PASS: bilingual detective play, evidence gates, saved case resume and provider outage recovery');
} finally {
  await browser.close();
}

async function checkLanguage(locale, copy) {
  const context = await browser.newContext({
    locale, viewport:{width:360,height:640}, extraHTTPHeaders:{'X-Preparation-Fixture':`detective-${locale}`},
  });
  try {
    const page = await context.newPage();
    page.setDefaultTimeout(12000);
    const starts = [];
    page.on('request', request => {
      if (request.method() === 'POST' && new URL(request.url()).pathname === '/api/game/dynamic-starts') {
        const {locale, profileId, challengePreset, expectedCurrentRunId} = request.postDataJSON();
        starts.push({locale, profileId, challengePreset, expectedCurrentRunId});
      }
    });
    const button = name => page.getByRole('button',{name,exact:typeof name === 'string'});
    const game = page.locator('.game-detective');
    const choose = name => game.getByRole('button',{name,exact:true}).click();
    const result = game.locator('.game-case-result');

    async function current() {
      const response = await context.request.get(base + '/api/game/runs/current');
      assert.equal(response.status(), 200, 'active case unavailable');
      return response.json();
    }

    async function review(difficulty = copy.standard) {
      await button(copy.card).click();
      await button(copy.customize).click();
      await button(copy.next).click();
      await button(copy.next).click();
      await page.getByRole('radio',{name:difficulty,exact:true}).check();
      await button(copy.next).click();
      await page.getByRole('radio',{name:copy.language,exact:true}).check();
      await button(copy.next).click();
      await button(copy.review).click();
    }

    async function start() {
      const before = starts.length;
      await review();
      assert.equal(starts.length, before, 'preparation submitted a start');
      await button(copy.start).click();
      await game.waitFor();
      assert.equal(await game.getAttribute('lang'), locale);
      assert.deepEqual(starts.at(-1), {
        locale,profileId:'classic-detective',challengePreset:'standard',expectedCurrentRunId:undefined,
      });
      assert.equal(await game.locator('.game-encounter-stage [data-capy-expression]').count(), 1);
      assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'mobile overflow');
    }

    await page.goto(base);
    await button(copy.play).click();
    await button(copy.catalog).click();

    if (locale === 'en') {
      await start();
      await choose(copy.skip);
      for (const step of copy.steps) await choose(step);
      const accusation = game.getByRole('button',{name:copy.accuse,exact:true});
      await game.getByRole('button',{name:copy.defer,exact:true}).and(game.locator('button:enabled')).waitFor();
      assert.equal(await accusation.isEnabled(), false, 'accusation enabled without first clue');
      await choose(copy.defer);
      await result.getByRole('heading',{name:copy.unresolved,exact:true}).waitFor();
      await button(copy.back).click();
    }

    await start();
    await choose(copy.inspect);
    await game.locator('.game-encounter-content').getByText(copy.wax,{exact:true}).waitFor();
    const beforeResume = await current();
    const startsBeforeResume = starts.length;
    await page.reload();
    await button(copy.play).click();
    await button(copy.resume).click();
    await game.locator('.game-encounter-content').getByText(copy.wax,{exact:true}).waitFor();
    const resumed = await current();
    assert.equal(resumed.runId, beforeResume.runId);
    assert.equal(resumed.revision, beforeResume.revision);
    assert.equal(resumed.source.gameFingerprint, beforeResume.source.gameFingerprint);
    assert.ok(JSON.stringify(resumed.view) === JSON.stringify(beforeResume.view), 'accepted case changed after reload');
    assert.equal(starts.length, startsBeforeResume);

    if (locale === 'en') {
      await button(copy.close).click();
      await button(copy.catalog).click();
      await review(copy.expert);
      await page.getByRole('checkbox',{name:copy.replace}).check();
      await button(copy.start).click();
      await button(copy.retry).waitFor();
      assert.equal(starts.length, startsBeforeResume + 1);
      assert.equal(starts.at(-1).expectedCurrentRunId, beforeResume.runId);
      const active = await current();
      assert.equal(active.runId, beforeResume.runId, 'failed replacement lost saved case');
      assert.equal(active.revision, beforeResume.revision);
      await button(copy.continue).click();
      await game.locator('.game-encounter-content').getByText(copy.wax,{exact:true}).waitFor();
      assert.equal(starts.length, startsBeforeResume + 1, 'continue submitted another start');
    }

    await game.getByText(copy.notes,{exact:true}).click();
    await game.locator('.game-case-notes').getByText(copy.wax,{exact:true}).waitFor();
    for (const step of copy.steps) await choose(step);
    await button(copy.text).click();
    assert.equal(await game.locator('.game-encounter-backdrop').count(), 0);
    await choose(copy.accuse);
    const ending = result.getByRole('heading',{name:copy.solved,exact:true});
    await ending.waitFor();
    const summary = await result.innerText();
    assert.ok(summary.includes(copy.wax) && summary.includes(copy.clock), 'reconstruction omitted a decisive observation');
    assert.equal(await ending.evaluate(node => node === document.activeElement), true);
  } finally {
    await context.close();
  }
}
