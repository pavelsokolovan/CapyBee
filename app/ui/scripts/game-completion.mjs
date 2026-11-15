import assert from 'node:assert/strict';
export async function checkCompletion({page,context,base,requests}) {
 const game=page.locator('.game-run');
 const start=async()=>{
  const another=game.getByRole('button',{name:'Start another adventure',exact:true});if(await another.count())await another.click();
  await page.getByRole('button',{name:/Fixture — Minimal/}).click();
  while(await page.getByRole('button',{name:'Next',exact:true}).count())await page.getByRole('button',{name:'Next',exact:true}).click();
  await page.getByRole('button',{name:'Review',exact:true}).click();await page.getByRole('button',{name:'Start adventure',exact:true}).click();
  await game.waitFor();await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
 };
 const finish=async()=>{
  await game.getByRole('button',{name:'Wsiadam do łódki',exact:true}).click();
  await game.getByRole('button',{name:'Continue',exact:true}).click();
  await game.getByRole('button',{name:'Choose this Mission',exact:true}).waitFor();
 };
 await start();
 const active=await (await context.request.get(base+'/api/game/runs/current')).json();assert.ok(active.runId,'new authored run not ready');
 const early=await context.request.post(`${base}/api/game/runs/${active.runId}/completion/cell`,{data:{id:crypto.randomUUID(),expectedRevision:active.revision,suggestionVersion:1}});
 assert.equal(early.status(),409);
 const fabricated=await context.request.post(base+'/api/games/results',{data:{id:crypto.randomUUID(),gameKey:'capybee_rpg',durationMs:1,metrics:{completed:true,runId:active.runId}}});
 assert.equal(fabricated.status(),400);assert.equal(fabricated.headers()['cache-control'],'no-store');
 await finish();
 const path=`${base}/api/game/runs/${active.runId}/completion`;
 const offer=await (await context.request.get(path)).json();assert.equal(offer.mission.code,'say_thank_you_helper');
 const oldPosts=requests.length;
 await page.waitForTimeout(100);assert.equal(requests.length,oldPosts,'render issued a completion/reward POST');
 const mismatch=await context.request.get(path,{headers:{'X-Game-Owner':'other-owner'}});
 assert.equal(mismatch.status(),409);assert.equal(mismatch.headers()['cache-control'],'no-store');
 const beforeCompletions=(await (await context.request.get(base+'/api/missions/completions')).json()).length;
 // Accept offline, reload before dispatch, then reconcile one idempotent choice.
 await context.setOffline(true);
 await game.getByRole('button',{name:'Choose this Mission',exact:true}).click();
 await game.getByText('Waiting for the saved choice',{exact:true}).waitFor();
 assert.equal(requests.length,oldPosts);
 const records=await page.evaluate(async()=>{
  const db=await new Promise(resolve=>{const r=indexedDB.open('keyval-store');r.onsuccess=()=>resolve(r.result);});
  const values=await new Promise(resolve=>{const r=db.transaction('keyval').objectStore('keyval').getAll();r.onsuccess=()=>resolve(r.result);});db.close();
  return values.filter(v=>v?.type==='gameCompletion');
 });assert.equal(records.length,1);assert.equal(records[0].payload.kind,'MISSION');
 assert.deepEqual(Object.keys(records[0].payload).sort(),['expectedRevision','kind','suggestionVersion']);
 await context.setOffline(false);
 await page.reload();await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
 // Ended runs are not the active-run catalog entry; read them directly to check
 // server acknowledgement after the existing background dispatcher recovers.
 await page.waitForFunction(async path=>{const r=await fetch(path,{headers:{'X-Preparation-Fixture':'fixture'}});return r.ok&&(await r.json()).missionAccepted;},path,{timeout:60000});
 assert.equal((await (await context.request.get(base+'/api/missions/completions')).json()).length,beforeCompletions);
 const accepted=await (await context.request.get(path)).json();
 const selectedResponse=await context.request.get(base+'/api/game/mission-selections');
 assert.equal(selectedResponse.headers()['cache-control'],'no-store');
 assert.ok((await selectedResponse.json()).includes(accepted.mission.id));
 await page.getByRole('button',{name:'Back to Play',exact:true}).click();
 await page.getByRole('button',{name:'Missions',exact:true}).click();
 await page.getByText('Chosen in an adventure',{exact:true}).waitFor();
 await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
 const peer=await context.newPage();await peer.goto(base);
 try {
  const responses=await Promise.all([context.request.post(path+'/mission',{data:{id:crypto.randomUUID(),expectedRevision:accepted.revision,suggestionVersion:1}}),
   peer.evaluate(async({path,revision})=>(await fetch(path+'/mission',{method:'POST',headers:{'X-Preparation-Fixture':'fixture','Content-Type':'application/json'},body:JSON.stringify({id:crypto.randomUUID(),expectedRevision:revision,suggestionVersion:1})})).status,{path,revision:accepted.revision})]);
  assert.equal(responses[0].status(),200);assert.equal(responses[1],200);
 }finally{await peer.close();}
 // A fresh ending provides the opt-in cell UI; its projection is server-derived.
 await start();await finish();
 const cell=game.getByRole('button',{name:'Add to my hive',exact:true});await cell.waitFor();await cell.click();
 await game.getByText('Added to your hive',{exact:true}).waitFor();
 const history=await (await context.request.get(base+'/api/games/results?limit=1000')).json();
 const rpg=history.filter(row=>row.gameKey==='capybee_rpg');assert.equal(rpg.length,1);assert.deepEqual(rpg[0].metrics,{});assert.equal(rpg[0].durationMs,0);
 assert.equal((await (await context.request.get(base+'/api/missions/completions')).json()).length,beforeCompletions);
 await page.screenshot({path:'/tmp/294-completion-360.png'});
 // Dismiss never accepts the offered Mission.
 const beforeDismiss=requests.length;await game.getByRole('button',{name:'Not now',exact:true}).click();assert.equal(requests.length,beforeDismiss);
 const last=requests.filter(r=>r.url.endsWith('/completion/cell')).at(-1);assert.ok(last);
 const cellPath=last.url.replace(/\/cell$/,'');
 await start();await finish();
 assert.equal(await game.getByRole('button',{name:'Add to my hive',exact:true}).count(),0,'replayed RPG offered a second daily cell');
 const removed=await context.request.delete(cellPath);assert.equal(removed.status(),204);
 assert.equal((await context.request.get(cellPath)).status(),404);
 const afterArchive=await (await context.request.get(base+'/api/games/results?limit=1000')).json();assert.equal(afterArchive.some(row=>row.gameKey==='capybee_rpg'),false);
 console.log('PASS: authoritative completion, reserved key, owner guards, offline Mission acceptance/reload, duplicate devices, separate cell and Mission effects, dismissal/archive');
}
