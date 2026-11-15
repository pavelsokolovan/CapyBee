import assert from 'node:assert/strict';

export async function checkPolishPack({page,context,base,requests}) {
 const game=page.locator('.game-run');
 const saved=()=>page.waitForFunction(()=>['Saved','Zapisano'].includes(document.querySelector('.game-run [role=status]')?.textContent));
 const choose=async label=>{await game.getByRole('button',{name:label,exact:true}).click();await saved();};
 const start=async(locale='en')=>{
  const another=game.getByRole('button',{name:locale==='en'?'Start another adventure':'Rozpocznij inną przygodę',exact:true});
  if(await another.count())await another.click();
  const pack=page.getByRole('button',{name:/CapyBee — Nowy ul, dwa światła/});
  assert.ok((await pack.innerText()).includes(locale==='en'?'Story language: Polish':'Język historii: polski'));
  await pack.click();assert.equal(await page.getByRole('checkbox',{name:/Use AI narration|Użyj narracji AI/}).count(),0);
  await page.getByRole('button',{name:locale==='en'?'Review':'Sprawdź',exact:true}).click();
  const before=requests.length;
  await page.getByRole('button',{name:locale==='en'?'Start adventure':'Rozpocznij przygodę',exact:true}).click();
  await game.waitFor();await saved();assert.equal(await game.getAttribute('lang'),'pl');
  await game.getByText(locale==='en'?'Story language: Polish':'Język historii: polski',{exact:true}).waitFor();
  const starts=requests.slice(before).filter(r=>r.url.endsWith('/api/game/runs'));assert.equal(starts.length,1);
  assert.equal(starts[0].body.packId,'capybee-new-hive');assert.equal(starts[0].body.locale,'pl');assert.equal(starts[0].body.generated,false);
  assert.deepEqual(starts[0].body.startInputs,{});
 };
 const ending=async outcome=>{
  await game.getByRole('button',{name:/Start another adventure|Rozpocznij inną przygodę/}).waitFor();
  const runId=requests.filter(r=>r.url.endsWith('/api/game/runs')&&r.body.packId==='capybee-new-hive').at(-1).body.id;
  const run=await (await context.request.get(`${base}/api/game/runs/${runId}`)).json();
  assert.equal(run.status,'ENDED');assert.equal(run.endReason,'COMPLETED');assert.equal(run.view.outcome,outcome);assert.equal(run.packVersion,'1.0.0');
 };
 await start();await choose('Continue');
 await game.getByText('Places',{exact:true}).click();assert.equal(await game.locator('.game-place-list li').count(),7);
 assert.equal(await game.getByRole('button',{name:'Go to: Mostek Dwóch Świateł',exact:true}).isDisabled(),true);
 await choose('Go to: Latarnia Wspomnień');await choose('Go to: Polana Nowego Ula');
 const current=await (await context.request.get(base+'/api/game/runs/current')).json(),posts=requests.length;
 await page.reload();await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
 await page.getByRole('button',{name:'Continue',exact:true}).click();await saved();
 const resumed=await (await context.request.get(`${base}/api/game/runs/${current.runId}`)).json();
 assert.equal(resumed.revision,current.revision);assert.deepEqual(resumed.view,current.view);assert.equal(requests.length,posts,'reload created another start/action');
 for(const label of ['Idę do Warsztatu Plastra','Wiążę poręcz i ruszam do mostku','Zapalam światło przy nowej ścieżce'])await choose(label);
 await ending('new-path');
 await start();
 for(const label of ['Continue','Zaglądam do Latarni Wspomnień','Zabieram światło wspomnienia na drogę','Continue',
  'Odwiedzam Melę na Cichej Łące','Siadam na chwilę i słucham Meli','Continue',
  'Odwiedzam Borysa nad trzciną','Przytrzymuję trzcinę dla Borysa','Continue',
  'Odwiedzam Lunę pod wierzbą','Układam z Luną znak z liści','Continue',
  'Idę do Warsztatu Plastra','Wiążę poręcz i ruszam do mostku','Zostawiam miejsce na oba światła'])await choose(label);
 await ending('two-lights');
 await page.getByRole('button',{name:'Back to Play',exact:true}).click();
 await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/child-profile')&&r.request().method()==='PATCH'&&r.status()===200),page.locator('.locale-control select').first().selectOption('pl')]);
 await page.getByRole('button',{name:'Graj',exact:true}).click();await page.getByRole('button',{name:/Przygody/}).click();
 await start('pl');await choose('Dalej');await choose('Na dziś wybieram odpoczynek');await ending('rest-here');
 await page.emulateMedia({reducedMotion:'reduce'});await game.evaluate(n=>n.style.zoom='2');
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));
 await game.evaluate(n=>n.style.zoom='1');await page.screenshot({path:'/tmp/291-polish-rest-360.png'});
 await page.getByRole('button',{name:'Wróć do gier',exact:true}).click();await Promise.all([page.waitForResponse(r=>r.url().endsWith('/api/child-profile')&&r.request().method()==='PATCH'&&r.status()===200),page.locator('.locale-control select').first().selectOption('en')]);
 await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
 console.log('PASS: packaged Polish authored Pack, three resolutions, optional companions, legal map movement, exact reload, EN/PL language labels and narrow/zoom/reduced motion');
}
