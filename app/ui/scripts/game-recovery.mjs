import assert from 'node:assert/strict';

async function records(page){return page.evaluate(async()=>{
 const values=[];for(const {name} of await indexedDB.databases()){
  const db=await new Promise((resolve,reject)=>{const r=indexedDB.open(name);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
  for(const name of db.objectStoreNames)values.push(...await new Promise((resolve,reject)=>{const r=db.transaction(name).objectStore(name).getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);}));db.close();
 }return values;
});}
async function open(page,base){await page.goto(base);await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();}
async function start(page,name,generated=false){
 const another=page.getByRole('button',{name:'Start another adventure',exact:true});if(await another.count())await another.click();
 await page.getByRole('button',{name,exact:false}).click();
 await page.getByRole('button',{name:/^Customize adventure/}).click();
 if(generated)await page.getByRole('checkbox',{name:/generated|AI/i}).check();
 while(await page.getByRole('button',{name:'Next',exact:true}).count())await page.getByRole('button',{name:'Next',exact:true}).click();
 await page.getByRole('button',{name:'Review',exact:true}).click();await page.getByRole('button',{name:'Start adventure',exact:true}).click();
 await page.locator('.game-run').waitFor();
}
async function assertPrivate(page,sentinel,logs){
 const stored=await page.evaluate(async()=>{
  const cache=[];for(const name of await caches.keys()){const c=await caches.open(name);for(const key of await c.keys()){const response=await c.match(key);cache.push(await response.text());}}
  return JSON.stringify({local:localStorage,session:sessionStorage,history:history.state,url:location.href,cache});
 });assert.equal(stored.includes(sentinel),false,'private text entered browser storage');assert.equal(JSON.stringify(await records(page)).includes(sentinel),false,'private text entered IndexedDB');assert.equal(logs.some(line=>line.includes(sentinel)),false,'private text entered console');
}
export async function checkRecovery({page,context,base,requests,logs}){
 await context.addInitScript(()=>Object.defineProperty(navigator,'locks',{configurable:true,value:undefined}));
 await page.evaluate(()=>Object.defineProperty(navigator,'locks',{configurable:true,value:undefined}));
 await start(page,'Fixture — Minimal');
 const scene=await page.locator('.game-run').innerText();
 await context.setOffline(true);
 await page.getByRole('button',{name:'Wsiadam do łódki',exact:true}).click();
 await page.getByText('Waiting for connection',{exact:true}).waitFor();
 let queued=[];for(let attempt=0;attempt<100;attempt++){queued=(await records(page)).filter(v=>v?.type==='gameCommand');if(queued.length)break;await page.waitForTimeout(25);}assert.equal(queued.length,1);assert.equal(queued[0].poll,undefined);const id=queued[0].clientId;
 assert.equal(await page.locator('.game-run').innerText().then(s=>s.includes('Stoisz')),true);
 // Hold the acknowledgement only after the server receives the original UUID.
 // Reload can then deterministically prove status recovery, rather than racing
 // a navigation-cancelled, provably unsent request (which may safely be retried).
 let received=false,releaseOfflineAck;
 const offlineAck=new Promise(resolve=>{releaseOfflineAck=resolve;});
 await context.route('**/api/game/runs/*/commands',async route=>{
  if(route.request().method()==='POST'&&route.request().postDataJSON()?.id===id){
   const response=await route.fetch();received=true;await offlineAck;await route.fulfill({response}).catch(()=>{});
  }else await route.continue();
 });
 await context.setOffline(false);
 for(let attempt=0;attempt<200&&!received;attempt++)await page.waitForTimeout(25);
 assert.equal(received,true,'original offline UUID did not reach the server');
 await page.reload();releaseOfflineAck();await context.unroute('**/api/game/runs/*/commands');
 await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();await page.getByRole('button',{name:'Continue',exact:true}).click();
 await page.waitForFunction(()=>['Saved','The adventure changed. Check the saved scene.'].includes(document.querySelector('.game-run [role=status]')?.textContent),null,{timeout:60000});
 assert.equal(requests.filter(r=>r.url.endsWith('/commands')&&r.body.id===id).length,1,'offline intent was replayed as another POST');
 assert.equal((await records(page)).some(v=>v?.type==='gameCommand'),false);
 assert.notEqual(await page.locator('.game-run').innerText(),scene);
 // A second page competes from the same accepted scene. Server revisions arbitrate.
 const peer=await context.newPage();await open(peer,base);await peer.getByRole('button',{name:'Continue',exact:true}).click();
 const prior=await context.request.get(base+'/api/game/runs/current');const current=await prior.json();
 await Promise.all([page.locator('.game-run fieldset button:enabled').first().click(),peer.locator('.game-run fieldset button:enabled').first().click()]);
 await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved'||document.querySelector('.game-run [role=status]')?.textContent?.includes('changed'));
 const after=await context.request.get(base+'/api/game/runs/'+current.runId);assert.equal((await after.json()).revision,current.revision+1,'competing tabs advanced twice');await peer.close();
 // Text is online-only and never durable, including the ambiguous/lost-response path.
 await start(page,'Fixture Text Input');
 const sentinel='gameplay-private-'+crypto.randomUUID().replace(/[0-9]/g,d=>String.fromCharCode(107+Number(d)));
 await context.setOffline(true);await page.locator('.game-run textarea').waitFor();assert.equal(await page.locator('.game-run textarea').isDisabled(),true);await context.setOffline(false);
 await page.locator('.game-run textarea').fill(sentinel);
 let dropped=false;
 await context.route('**/api/game/runs/*/commands',async route=>{if(!dropped&&route.request().method()==='POST'&&route.request().postDataJSON()?.command?.type==='SUBMIT_TEXT'){dropped=true;await route.fetch();await route.abort();}else await route.continue();});
 await page.getByRole('button',{name:'Send',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved',null,{timeout:60000});
 assert.equal(dropped,true);assert.equal(await page.locator('.game-run textarea').inputValue(),'');await assertPrivate(page,sentinel,logs);
 await context.unroute('**/api/game/runs/*/commands');
 const privatePosts=requests.filter(r=>r.body?.command?.text===sentinel);assert.equal(privatePosts.length,1);
 await page.reload();await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();await page.getByRole('button',{name:'Continue',exact:true}).click();
 assert.equal(requests.filter(r=>r.body?.command?.text===sentinel).length,1);await assertPrivate(page,sentinel,logs);
 await page.getByRole('button',{name:'End adventure',exact:true}).click();await page.locator('dialog').getByRole('button',{name:'End adventure',exact:true}).click();await page.getByText('Abandoned',{exact:true}).waitFor();
 await start(page,'Fixture Generated',true);await page.locator('.game-run fieldset button:enabled').first().waitFor();
 // Lose a confirmed start response and reload while the foreground POST is unresolved.
 await page.getByRole('button',{name:'End adventure',exact:true}).click();await page.locator('dialog').getByRole('button',{name:'End adventure',exact:true}).click();await page.getByText('Abandoned',{exact:true}).waitFor();
 await page.getByRole('button',{name:'Start another adventure',exact:true}).click();await page.getByRole('button',{name:/New AI-created adventure/}).click();
 await page.getByRole('button',{name:/^Customize adventure/}).click();
 while(await page.getByRole('button',{name:'Next',exact:true}).count())await page.getByRole('button',{name:'Next',exact:true}).click();
 await page.getByRole('button',{name:'Review',exact:true}).click();
 let published=false,startId,release;
 const gate=new Promise(resolve=>{release=resolve;});
 await context.route('**/api/game/dynamic-starts',async route=>{if(route.request().method()==='POST'){startId=route.request().postDataJSON().requestId;await route.fetch();published=true;await gate;await route.abort().catch(()=>{});}else await route.continue();});
 await page.getByRole('button',{name:'Start adventure',exact:true}).click();
 for(let attempt=0;attempt<200&&!published;attempt++)await page.waitForTimeout(25);assert.equal(published,true);
 await page.reload();release();await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
 await page.locator('.game-run').waitFor();
 assert.equal(requests.filter(r=>r.url.endsWith('/dynamic-starts')&&r.body.requestId===startId).length,1,'reload reposted a paid start');
 await context.unroute('**/api/game/dynamic-starts');
 // Owner mismatch is rejected before mechanics execute and returns no saved content.
 const rejected=await context.request.get(base+'/api/game/runs/current',{headers:{'X-Game-Owner':'different-owner'}});assert.equal(rejected.status(),409);assert.equal((await rejected.json()).error.code,'GAME_OWNER_CHANGED');assert.equal(rejected.headers()['cache-control'],'no-store');
 // Static-only service worker cache survives a cold offline shell load.
 await page.evaluate(()=>navigator.serviceWorker.ready);
 const cold=await context.newPage();await cold.goto(base);await cold.evaluate(()=>navigator.serviceWorker.ready);await cold.reload();
 await context.setOffline(true);await cold.reload();assert.equal(await cold.locator('.game-run').count(),0);await context.setOffline(false);await cold.close();
 // Token changes in a different tab hide the mounted scene before identity is rechecked.
 const tokenPeer=await context.newPage();await tokenPeer.goto(base);
 const postsBeforeTokenChange=requests.length;
 await tokenPeer.evaluate(()=>localStorage.setItem('capybee.sessionToken',JSON.stringify({token:'fixture-cross-tab-token',expiresAt:Date.now()+60000})));
 await page.locator('.game-run').waitFor({state:'detached'});await tokenPeer.close();
 await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();await page.getByRole('button',{name:'Continue',exact:true}).click();
 await page.locator('.game-run').waitFor();assert.equal(requests.length,postsBeforeTokenChange,'identity refresh submitted a new game action');
 // Exercise the app's existing auth-restoration path under a test-only network double.
 await page.bringToFront();
 const auth=await (await context.request.get(base+'/api/auth-status')).json();
 await page.evaluate(()=>localStorage.setItem('capybee.sessionToken',JSON.stringify({token:'fixture-only-token',expiresAt:Date.now()+60000})));
 let failRead=true,restores=0,restoreOwner=auth.id;
 await context.route('**/api/game/runs/*',async route=>{if(route.request().method()==='GET'&&failRead){failRead=false;await route.fulfill({status:401,contentType:'application/json',body:'{"error":{"code":"UNAUTHENTICATED"}}'});}else await route.continue();});
 await context.route('**/api/session/restore',async route=>{restores++;await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({authenticated:true,id:restoreOwner,sessionToken:'fixture-rotated-token'})});});
 const currentResponse=status=>page.waitForResponse(response=>response.url().endsWith('/api/game/runs/current')&&response.status()===status);
 const rejectedPreview=currentResponse(401);
 await page.getByRole('button',{name:'Back to Play',exact:true}).click();await rejectedPreview;
 await page.locator('.adventure-resume').waitFor({state:'detached'});
 assert.equal(restores,0,'the background shortcut requested session restoration');
 failRead=true;await page.getByRole('button',{name:/Adventures/}).click();
 for(let i=0;i<200&&restores===0;i++)await page.waitForTimeout(25);
 assert.equal(restores,1);await page.getByRole('button',{name:'Continue',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved',null,{timeout:60000});
 const acceptedPreview=currentResponse(200);
 await page.getByRole('button',{name:'Back to Play',exact:true}).click();await acceptedPreview;
 restoreOwner='different-owner';failRead=true;
 await page.getByRole('button',{name:/Adventures/}).click();
 for(let i=0;i<200&&restores<2;i++)await page.waitForTimeout(25);assert.equal(restores,2);
 await page.locator('.adventure-overlay').waitFor({state:'detached'});
 assert.equal((await records(page)).some(v=>v?.owner===auth.id&&v?.type==='gameCommand'),false);
 assert.equal(await page.evaluate(()=>Object.keys(sessionStorage).some(key=>key.startsWith('game-operation:'))),false);
 await context.unroute('**/api/game/runs/*');await context.unroute('**/api/session/restore');
 console.log('PASS: recovery, offline intent UUID, reload, competing tabs, lost text response, private storage, generated fallback, owner guard and static PWA shell');
}
