import assert from 'node:assert/strict';

export async function checkHexMap({page, context, base, requests}) {
 const game=page.locator('.game-run');
 const another=game.getByRole('button',{name:'Start another adventure',exact:true});
 if(await another.count())await another.click();
 await page.getByRole('button',{name:/Fixture — Hex Places/}).click();
 await page.getByRole('button',{name:'Review',exact:true}).click();
 await page.getByRole('button',{name:'Start adventure',exact:true}).click();
 await game.getByRole('status').filter({hasText:'Saved'}).waitFor();
 const beforeOpen=requests.length;
 await game.getByText('Places',{exact:true}).click();
 const map=game.locator('.game-place-list'), current=map.locator('[aria-current=location]');
 assert.equal(await map.locator('li').count(),5);
 assert.equal(await current.innerText().then(t=>t.includes('Home by the quiet meadow')),true);
 assert.equal(requests.length,beforeOpen,'opening map mutated game');
 const inspect=map.getByRole('button',{name:/Inspect: Dom/});
 await inspect.focus();await page.keyboard.press('Enter');
 await game.locator('.game-place-inspection h4').waitFor();
 assert.equal(await game.locator('.game-place-inspection h4').evaluate(n=>n===document.activeElement),true);
 assert.equal(await game.locator('.game-place-inspection').innerText().then(t=>t.includes('<img src=x')),true);
 assert.equal(await game.locator('img,script').count(),0);
 await page.keyboard.press('Tab');await page.keyboard.press('Enter');
 assert.equal(await inspect.evaluate(n=>n===document.activeElement),true);
 assert.equal(requests.length,beforeOpen,'inspection mutated game');
 await page.setViewportSize({width:1280,height:900});
 assert.equal(await map.evaluate(n=>getComputedStyle(n).display),'grid');
 const parity=await map.locator('li').allTextContents();
 const disabled=await map.locator('button').evaluateAll(nodes=>nodes.map(n=>n.disabled));
 const toggle=game.getByRole('button',{name:'Show place list',exact:true});
 await toggle.click();assert.deepEqual(await map.locator('li').allTextContents(),parity);
 assert.deepEqual(await map.locator('button').evaluateAll(nodes=>nodes.map(n=>n.disabled)),disabled);
 await toggle.click();
 await map.locator('li').first().scrollIntoViewIfNeeded();
 await page.screenshot({path:'/tmp/293-hex-desktop.png'});
 await page.setViewportSize({width:360,height:800});await page.emulateMedia({reducedMotion:'reduce'});
 for(const zoom of ['1','2']){
  await game.evaluate((n,zoom)=>n.style.zoom=zoom,zoom);
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth),'map overflows narrow/zoomed viewport');
  assert.equal(await map.evaluate(n=>getComputedStyle(n).display),'block');
  assert.ok(await map.locator('button').evaluateAll(nodes=>nodes.every(n=>{const r=n.getBoundingClientRect();return r.width>=44&&r.height>=44;})));
 }
 await map.locator('li').first().scrollIntoViewIfNeeded();
 await page.screenshot({path:'/tmp/293-hex-360-zoom.png'});await game.evaluate(n=>n.style.zoom='1');
 // Keep the accepted map visible and locked while the existing dispatcher waits.
 let release,intercepted=false;
 const gate=new Promise(resolve=>{release=resolve;});
 await context.route('**/api/game/runs/*/commands',async route=>{
  if(!intercepted&&route.request().method()==='POST'&&route.request().postDataJSON()?.command?.type==='MOVE'){
   intercepted=true;await gate;
  }
  await route.continue();
 });
 await map.getByRole('button',{name:'Go to: Corridor',exact:true}).click();
 await game.getByText('Saving / checking',{exact:true}).waitFor();
 assert.equal(await current.innerText().then(t=>t.includes('Home by the quiet meadow')),true);
 assert.equal(await map.getByRole('button',{name:/^Go to:/}).evaluateAll(nodes=>nodes.every(n=>n.disabled)),true);
 release();await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
 await context.unroute('**/api/game/runs/*/commands');assert.equal(intercepted,true);
 assert.equal(await current.innerText().then(t=>t.includes('Corridor')),true);
 // Offline MOVE retains the original revision through the shared recovery path.
 await context.setOffline(true);
 await map.getByRole('button',{name:/^Go to: Dom/}).click();
 await game.getByText('Waiting for connection',{exact:true}).waitFor();
 assert.equal(await current.innerText().then(t=>t.includes('Corridor')),true);
 await context.setOffline(false);
 await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
 // A status notification can precede the accepted map's React commit. Wait
 // for the destination itself before tearing down the controller on reload.
 await page.waitForFunction(()=>document.querySelector('.game-place-list [aria-current=location]')?.textContent?.includes('Home by the quiet meadow'));
 const accepted=await (await context.request.get(base+'/api/game/runs/current')).json();
 assert.equal(accepted.view.places.currentPlaceId,'cell','offline destination was not saved');
 await page.reload();await page.getByRole('button',{name:'Play',exact:true}).click();
 await page.getByRole('button',{name:/Adventures/}).click();await page.getByRole('button',{name:'Continue',exact:true}).click();
 await game.getByText('Places',{exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('.game-place-list [aria-current=location]')?.textContent?.includes('Home by the quiet meadow'));
 assert.equal(await current.innerText().then(t=>t.includes('Home by the quiet meadow')),true);
 // A second tab accepts a move without broadcasting; the first tab's stale
 // selection must reconcile, never rebase and execute against the new revision.
 const peer=await context.newPage();await peer.goto(base);
 try {
  const run=await (await context.request.get(base+'/api/game/runs/current')).json();
  const commandId=crypto.randomUUID();
  const status=await peer.evaluate(async ({run,commandId})=>{
   const response=await fetch(`/api/game/runs/${run.runId}/commands`,{method:'POST',headers:{'Content-Type':'application/json','X-Preparation-Fixture':'fixture'},body:JSON.stringify({id:commandId,expectedRevision:run.revision,command:{type:'MOVE',placeId:'corridor'}})});return response.status;
  },{run,commandId});assert.ok([200,202].includes(status));
  await page.waitForFunction(async ({runId,revision})=>{const response=await fetch(`/api/game/runs/${runId}`,{headers:{'X-Preparation-Fixture':'fixture'}});return (await response.json()).revision===revision+1;},{runId:run.runId,revision:run.revision});
  await map.getByRole('button',{name:'Go to: Corridor',exact:true}).click();
  await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent?.includes('changed'));
  const saved=await (await context.request.get(base+'/api/game/runs/'+run.runId)).json();
  assert.equal(saved.revision,run.revision+1,'stale map move advanced again');
  assert.equal(await current.innerText().then(t=>t.includes('Corridor')),true);
 } finally {await peer.close();}
 await game.getByRole('button',{name:'End adventure',exact:true}).click();
 await game.locator('dialog').getByRole('button',{name:'End adventure',exact:true}).click();
 await game.getByText('Abandoned',{exact:true}).waitFor();
 console.log('PASS: HEX map/list parity, keyboard inspection, pending lock, narrow/zoom/reduced motion, offline reload and two-tab stale MOVE');
}
