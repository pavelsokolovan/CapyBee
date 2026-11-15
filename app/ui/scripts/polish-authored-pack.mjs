import assert from 'node:assert/strict';

export async function checkPolishPack({page,context,base,requests}) {
 const game=page.locator('.game-run');
 const saved=()=>page.waitForFunction(()=>['Saved','Zapisano'].includes(document.querySelector('.game-run [role=status]')?.textContent));
 const currentRun=async()=> (await context.request.get(base+'/api/game/runs/current')).json();
 const assertPublic=run=>{
  const p=run.view.presentation;assert.equal(p.schemaVersion,1);assert.equal(p.catalogVersion,1);
  assert.equal(p.playerAssetId,'character.capy');assert.equal(p.places.length,7);
  assert.equal(new Set(p.characters.map(c=>c.id)).size,p.characters.length);
  assert.deepEqual(p.party,p.characters.filter(c=>c.recruited).map(c=>c.id));
  for(const member of p.characters){
   assert.ok(run.view.characters.some(c=>c.id===member.id));
   assert.deepEqual(Object.keys(member).sort(),['assetId','expression','id','need','recruited','trait']);
  }
  assert.equal(JSON.stringify(p).includes('recruitedFlag'),false);assert.equal(JSON.stringify(p).includes('sceneIds'),false);
 };
 const choose=async label=>{
  await game.getByRole('button',{name:label,exact:true}).click();await saved();
  if(label==='Odwiedzam Melę na Cichej Łące'||label==='Siadam na chwilę i słucham Meli'){
   const run=await currentRun();assertPublic(run);const p=run.view.presentation,member=p.characters.find(c=>c.id==='actor:mela-bee');
   assert.equal(member.trait,'Cierpliwa');assert.ok(member.need.length>0);assert.equal(member.assetId,'character.mela-bee');
   assert.equal(member.recruited,label==='Siadam na chwilę i słucham Meli');
   if(member.recruited){
    const command=requests.filter(r=>r.url.includes('/commands')).at(-1);
    const replay=await context.request.post(command.url,{data:command.body});assert.equal(replay.status(),200);
    const envelope=await replay.json();assert.deepEqual(envelope.view.presentation,p);
    const retained=await currentRun();assert.equal(retained.revision,run.revision);assert.deepEqual(retained.view.presentation,p);
   }
  }
 };
 const start=async(locale='en')=>{
  const another=game.getByRole('button',{name:locale==='en'?'Start another adventure':'Rozpocznij inną przygodę',exact:true});
  if(await another.count())await another.click();
  const pack=page.getByRole('button',{name:/CapyBee — Nowy ul, dwa światła/});
  assert.ok((await pack.innerText()).includes(locale==='en'?'Story language: Polish':'Język historii: polski'));
  await pack.click();assert.equal(await page.getByRole('checkbox',{name:/Use AI narration|Użyj narracji AI/}).count(),1);
  await page.getByRole('button',{name:locale==='en'?'Review':'Sprawdź',exact:true}).click();
  const before=requests.length;
  await page.getByRole('button',{name:locale==='en'?'Start adventure':'Rozpocznij przygodę',exact:true}).click();
  await game.waitFor();await saved();assert.equal(await game.getAttribute('lang'),'pl');
  await game.getByText(locale==='en'?'Story language: Polish':'Język historii: polski',{exact:true}).waitFor();
  const starts=requests.slice(before).filter(r=>r.url.endsWith('/api/game/runs'));assert.equal(starts.length,1);
  assert.equal(starts[0].body.packId,'capybee-new-hive');assert.equal(starts[0].body.locale,'pl');assert.equal(starts[0].body.generated,false);
  assert.deepEqual(starts[0].body.startInputs,{'story-tone':'cozy',interests:'',mood:''});
  const run=await currentRun();assertPublic(run);assert.deepEqual(run.view.presentation.party,[]);
  assert.deepEqual(run.view.presentation.characters.map(c=>c.id),['actor:hive-guide']);
 };
 const ending=async outcome=>{
  await game.getByRole('button',{name:/Start another adventure|Rozpocznij inną przygodę/}).waitFor();
  const runId=requests.filter(r=>r.url.endsWith('/api/game/runs')&&r.body.packId==='capybee-new-hive').at(-1).body.id;
  const run=await (await context.request.get(`${base}/api/game/runs/${runId}`)).json();
  assert.equal(run.status,'ENDED');assert.equal(run.endReason,'COMPLETED');assert.equal(run.view.outcome,outcome);assert.equal(run.packVersion,'1.2.0');
  const panel=game.locator('.game-completion');await panel.waitFor();
  const completion=await (await context.request.get(`${base}/api/game/runs/${runId}/completion`)).json();
  const key=outcome==='new-path'?'say_thank_you_helper':outcome==='two-lights'?'listen_song_reminds_home':'rest';
  assert.equal(completion.suggestionKey,key);assert.equal(completion.suggestionVersion,1);
  if(key==='rest'){
   assert.equal(completion.mission,null);assert.equal(await panel.getByRole('button',{name:'Wybierz tę Misję',exact:true}).count(),0);
   await panel.getByText('Możesz tu odpocząć. Nie ma Misji do przyjęcia.',{exact:true}).waitFor();
  }else{
   assert.equal(completion.mission.code,key);
   if(outcome==='new-path'){
    const before=requests.length;await context.setOffline(true);await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
    await panel.getByRole('button',{name:'Choose this Mission',exact:true}).click();await panel.getByText('Waiting for the saved choice',{exact:true}).waitFor();assert.equal(requests.length,before);
    await context.setOffline(false);await page.evaluate(()=>window.dispatchEvent(new Event('online')));
    await panel.getByText('Mission chosen. Do it in real life when you are ready; it is not completed yet.',{exact:true}).waitFor();
    const writes=requests.slice(before).filter(r=>r.url.endsWith('/completion/mission'));assert.equal(writes.length,1);assert.deepEqual(Object.keys(writes[0].body).sort(),['expectedRevision','id','suggestionVersion']);
    const acknowledged=await (await context.request.get(`${base}/api/game/runs/${runId}/completion`)).json();assert.equal(acknowledged.missionAccepted,true);assert.equal(acknowledged.cellAdded,false);
   }else{
    const before=requests.length;await panel.getByRole('button',{name:'Not now',exact:true}).click();assert.equal(requests.length,before);
   }
  }

 };
 await start();await choose('Continue');
 await game.getByText('Places',{exact:true}).click();assert.equal(await game.locator('.game-place-list li').count(),7);
 assert.equal(await game.getByRole('button',{name:'Go to: Mostek Dwóch Świateł',exact:true}).isDisabled(),true);
 await choose('Go to: Latarnia Wspomnień');await choose('Go to: Polana Nowego Ula');
 const current=await (await context.request.get(base+'/api/game/runs/current')).json(),posts=requests.length;
 assertPublic(current);assert.deepEqual(current.view.presentation.characters.map(c=>c.id),['actor:hive-guide'],'map movement disclosed a future hero');
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
 console.log('PASS: Polish reviewed ending bindings, offline Mission acceptance, optional dismissal, equally valid rest; packaged Polish authored Pack, three resolutions, optional companions, legal map movement, exact reload, EN/PL language labels and narrow/zoom/reduced motion');
}
