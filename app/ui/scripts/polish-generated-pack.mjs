import assert from 'node:assert/strict';

export async function checkPolishGeneration({page,context,base,requests,logs}) {
 const idea='Fikcyjna baśń srebrnego liścia '+crypto.randomUUID().replace(/[0-9]/g,d=>String.fromCharCode(107+Number(d)));
 await page.getByRole('button',{name:/CapyBee — Nowy ul, dwa światła/}).click();
 await page.getByRole('checkbox',{name:'Use AI narration',exact:true}).check();
 const area=page.locator('textarea');await area.fill('😀'.repeat(241));
 await page.getByRole('button',{name:'Review',exact:true}).click();await page.getByText('Your idea is too long. Shorten it before continuing.',{exact:true}).waitFor();
 await context.setOffline(true);await page.evaluate(()=>window.dispatchEvent(new Event('offline')));await page.waitForFunction(()=>document.querySelector('textarea')?.disabled===true);
 await context.setOffline(false);await page.evaluate(()=>window.dispatchEvent(new Event('online')));await page.waitForFunction(()=>document.querySelector('textarea')?.disabled===false);await area.fill(idea);
 await page.getByRole('button',{name:'Review',exact:true}).click();await page.getByText('Idea added',{exact:true}).waitFor();
 assert.equal((await page.locator('[role=dialog]').innerText()).includes(idea),false);
 const before=requests.filter(r=>r.url.endsWith('/api/game/pack-starts')).length;
 // Send the explicit start, then lose its response. Recovery may only GET the operation.
 await page.route('**/api/game/pack-starts',async route=>{await route.fetch();await route.abort('failed');});
 await page.getByRole('button',{name:'Start adventure',exact:true}).click();await page.locator('.game-run').waitFor();
 await page.unroute('**/api/game/pack-starts');
 assert.equal(requests.filter(r=>r.url.endsWith('/api/game/pack-starts')).length,before+1);
 const body=requests.filter(r=>r.url.endsWith('/api/game/pack-starts')).at(-1).body;
 assert.equal(body.suggestion,idea);assert.equal(body.confirmed,true);assert.equal(body.packVersion,'1.1.0');assert.equal(body.disclosureRevision,1);assert.equal(body.selections['story-tone'][0],'cozy');
 const current=await (await context.request.get(`${base}/api/game/runs/current`)).json();assert.equal(current.source.kind,'PACK');assert.ok(current.view.story);
 await page.reload();await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
 await page.getByRole('button',{name:'Continue',exact:true}).click();await page.locator('.game-run').waitFor();
 const restored=await (await context.request.get(`${base}/api/game/runs/current`)).json();assert.deepEqual(restored.view,current.view);assert.equal(restored.revision,current.revision);
 assert.equal(requests.filter(r=>r.url.endsWith('/api/game/pack-starts')).length,before+1);
 const stores=await page.evaluate(async()=>{
  const values=[];for(const item of await indexedDB.databases()){
   const db=await new Promise((resolve,reject)=>{const r=indexedDB.open(item.name);r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
   for(const name of db.objectStoreNames)values.push(await new Promise((resolve,reject)=>{const r=db.transaction(name).objectStore(name).getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);}));db.close();
  }
  for(const name of await caches.keys()){const cache=await caches.open(name);for(const key of await cache.keys()){const response=await cache.match(key);if(response)values.push(await response.text());}}
  return JSON.stringify({local:localStorage,session:sessionStorage,history:history.state,url:location.href,values});
 });assert.equal(stores.includes(idea),false);assert.equal(logs.some(line=>line.includes(idea)),false);
 const game=page.locator('.game-run');
 await game.getByRole('button',{name:'Continue',exact:true}).click();await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
 await game.getByRole('button',{name:'Na dziś wybieram odpoczynek',exact:true}).click();await game.getByRole('button',{name:'Start another adventure',exact:true}).waitFor();
 console.log('PASS: Polish generated Pack explicit consent, Unicode limit, offline wish, masked review, lost response, one operation, exact reload and private storage exclusions');
}
