import assert from 'node:assert/strict';
import {checkExpedition, captureExpeditionHub, checkSceneFocus} from './game-expeditions.mjs';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const evidence=fileURLToPath(new URL('../../../docs/evidence/38/',import.meta.url));
const current=async(context,base)=>(await context.request.get(base+'/api/game/runs/current')).json();
export async function mapScreenshot(page,name) {
 await mkdir(evidence,{recursive:true});
 await page.screenshot({path:evidence+name+'.png'});
}
export async function openExpeditionMap(game) {
 const sheet=game.locator('.game-map-sheet');
 if (!await sheet.isVisible()) await game.locator('.game-map-button').click();
 await sheet.waitFor();
 return sheet;
}
export async function selectPlace(sheet,id) {
 await sheet.locator(`button[data-place-id="${id}"]`).click();
 return sheet.locator('.game-map-card');
}
export async function travelTo(game,id) {
 const sheet=await openExpeditionMap(game);
 const card=await selectPlace(sheet,id);
 await card.getByRole('button').click();
 await sheet.waitFor({state:'hidden'});
 await game.locator('.game-run-status').filter({hasText:/^(Saved|Zapisano)$/}).waitFor({state:'attached'});
}
async function accepted(page,context,base,placeId,revision){
 await page.waitForFunction(place=>
  document.querySelector('.game-map-tile[aria-current=location]')?.dataset.placeId===place
  && document.querySelector('.game-run .game-run-status')?.textContent==='Saved',placeId);
 const run=await current(context,base);
 assert.equal(run.revision,revision);assert.equal(run.view.places.currentPlaceId,placeId);
 return run;
}
async function focusWithTab(page,target) {
 for(let tries=0;tries<12;tries++){
  if(await target.evaluate(node=>node===document.activeElement))return;
  await page.keyboard.press('Tab');
 }
 assert.fail('Tab could not reach the map control');
}

export async function checkBoardRoute({page,context,base,requests}){
 await page.emulateMedia({reducedMotion:'no-preference',forcedColors:'none'});
 const game=page.locator('.game-run'),mapButton=game.locator('.game-map-button');
 const sheet=game.locator('.game-map-sheet'),card=sheet.locator('.game-map-card');
 const tiles=sheet.locator('.game-map-tile'),close=sheet.getByRole('button',{name:'Close',exact:true});
 const menu=game.locator('.game-scene-menu > summary');
 const initial=await current(context,base);assert.equal(initial.packVersion,'1.4.0');
 await captureExpeditionHub({page,game});
 const viewport=page.viewportSize(),beforeOpen=requests.length;
 await page.setViewportSize({width:360,height:640});
 for(const button of [mapButton,menu]){
  const bounds=await button.boundingBox();
  assert.ok(bounds.width>=44&&bounds.height>=44&&bounds.y>=0&&bounds.y+bounds.height<=640);
 }
 await menu.click();await mapScreenshot(page,'menu-open');
 await page.keyboard.press('Escape');
 assert.equal(await menu.evaluate(node=>!node.parentElement.open&&node===document.activeElement),true);
 await menu.click();
 await game.getByRole('button',{name:'End adventure',exact:true}).click();
 await game.locator('dialog[aria-labelledby=game-abandon-title]').getByRole('button',{name:'Cancel',exact:true}).click();
 assert.equal(await menu.evaluate(node=>!node.parentElement.open&&node===document.activeElement),true);

 // A full keyboard open/close cycle exercises native modality and the overlay Escape guard.
 await mapButton.focus();await page.keyboard.press('Enter');
 await sheet.waitFor();
 const title=sheet.getByRole('heading',{name:'Adventure map',exact:true});
 assert.equal(await title.evaluate(node=>node===document.activeElement),true);
 await page.keyboard.press('Tab');
 assert.equal(await close.evaluate(node=>node===document.activeElement),true);
 await page.keyboard.press('Escape');await sheet.waitFor({state:'hidden'});
 assert.equal(await mapButton.evaluate(node=>node===document.activeElement),true);
 const sceneBody=game.locator('.game-scene-body');
 const scrolled=await sceneBody.evaluate(node=>{node.scrollTop=node.scrollHeight;return node.scrollTop;});
 assert.ok(scrolled>0);
 await openExpeditionMap(game);
 for(const region of [sheet,sheet.locator('.game-map-scroll')]){
  assert.equal(await region.evaluate(node=>getComputedStyle(node).overscrollBehavior),'contain');
 }
 assert.equal(await sceneBody.evaluate(node=>getComputedStyle(node).overflowY),'hidden');
 await page.mouse.move(1,1);await page.mouse.wheel(0,-300);
 await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
 assert.equal(await sceneBody.evaluate(node=>node.scrollTop),scrolled);
 await page.mouse.click(1,1);await sheet.waitFor({state:'hidden'});
 assert.equal(await mapButton.evaluate(node=>node===document.activeElement),true);
 await openExpeditionMap(game);await close.click();await sheet.waitFor({state:'hidden'});
 assert.equal(await mapButton.evaluate(node=>node===document.activeElement),true);

 await openExpeditionMap(game);await sheet.locator('.game-map-grid').waitFor();
 assert.equal(await tiles.count(),7);
 assert.ok(await tiles.evaluateAll(nodes=>nodes.every(node=>node.getBoundingClientRect().width>=92&&!node.hasAttribute('aria-label'))));
 assert.equal(await sheet.locator('.game-tile-marker').count(),1);
 assert.equal(await sheet.locator('[aria-current=location]').getAttribute('aria-pressed'),'true');
 assert.equal(await card.locator('button').count(),0);
 await mapScreenshot(page,'map-sheet');
 const bridge=initial.view.places.places.find(place=>place.id==='small-crossing');
 assert.ok(bridge.moveHint);
 await selectPlace(sheet,bridge.id);
 assert.equal(await sheet.getByText(bridge.moveHint,{exact:true}).count(),1);
 assert.equal(await card.getByText(bridge.moveHint,{exact:true}).isVisible(),true);
 assert.equal(await card.getByRole('button').count(),0);
 await mapScreenshot(page,'map-sheet-hint');
 assert.equal(requests.length,beforeOpen,'opening, closing or selecting a tile sent a command');

 // A true 200% zoom reduces the CSS viewport to half its width and height.
 await page.setViewportSize({width:180,height:320});
 await sheet.locator('.game-map-list').waitFor();
 assert.deepEqual(await sheet.evaluate(node=>{
  const area=node.getBoundingClientRect(),card=node.querySelector('.game-map-card').getBoundingClientRect();
  return {fits:area.width<=innerWidth&&area.height<=innerHeight*.88+1,
   cardInside:card.top>=area.top&&card.bottom<=area.bottom,
   overflows:[document.documentElement,node,node.querySelector('.game-map-scroll'),node.querySelector('.game-map-card')]
    .filter(region=>region.scrollWidth>region.clientWidth).map(region=>region.className||region.tagName)};
 }),{fits:true,cardInside:true,overflows:[]});
 await page.setViewportSize({width:360,height:640});
 await page.emulateMedia({forcedColors:'active'});
 await sheet.locator('.game-map-list').waitFor();
 assert.equal(await sheet.getByText("You're here",{exact:true}).isVisible(),true);
 assert.equal(await card.getByText(bridge.moveHint,{exact:true}).isVisible(),true);
 await page.emulateMedia({forcedColors:'none'});await close.click();
 await menu.click();await game.getByRole('button',{name:'Text mode',exact:true}).click();
 await openExpeditionMap(game);
 await sheet.locator('.game-map-list').waitFor();
 assert.equal(await sheet.locator('svg').count(),0);
 assert.equal(await sheet.getByText("You're here",{exact:true}).count(),2);
 assert.ok(await tiles.evaluateAll(nodes=>nodes.every(node=>node.innerText.trim().length>0)));
 await mapScreenshot(page,'map-sheet-list');
 await close.click();await menu.click();await game.getByRole('button',{name:'Text mode',exact:true}).click();
 assert.equal(requests.length,beforeOpen);

 // Hold the one keyboard-confirmed MOVE so the accepted scene and selection can be inspected.
 const url=`${base}/api/game/runs/${initial.runId}/commands`;
 let release;const barrier=new Promise(resolve=>{release=resolve;});
 const handler=async route=>{if(route.request().method()==='POST')await barrier;await route.continue();};
 await page.route(url,handler);
 try{
  await mapButton.focus();await page.keyboard.press('Enter');await sheet.waitFor();
  const meadow=sheet.locator('[data-place-id=quiet-meadow]');
  await focusWithTab(page,meadow);await page.keyboard.press('Enter');
  assert.equal(requests.length,beforeOpen);
  const visit=card.getByRole('button',{name:'Visit: Cicha Łąka',exact:true});
  await focusWithTab(page,visit);
  const outgoing=page.waitForRequest(r=>r.url()===url&&r.method()==='POST');
  await page.keyboard.press('Enter');await outgoing;
  await sheet.getByText('Saving / checking',{exact:true}).waitFor();
  assert.equal(await sheet.locator('[aria-current=location]').getAttribute('data-place-id'),'hive-square');
  assert.ok(await tiles.evaluateAll(buttons=>buttons.every(button=>button.disabled)));
  assert.equal(await visit.isDisabled(),true);
  assert.equal((await current(context,base)).revision,initial.revision);
  release();
  const run=await accepted(page,context,base,'quiet-meadow',initial.revision+1);
  await sheet.waitFor({state:'hidden'});
  const location=game.getByRole('heading',{name:'Cicha Łąka',exact:true});
  assert.equal(await location.evaluate(node=>node===document.activeElement),true);
  await checkSceneFocus(game);
  assert.ok(run.view.options.some(o=>o.id==='help-mela'));assert.equal(run.view.presentation.party.length,0);
  const writes=requests.slice(beforeOpen).filter(r=>r.url===url);
  assert.equal(writes.length,1);assert.deepEqual(writes[0].body.command,{type:'MOVE',placeId:'quiet-meadow'});
  const replay=await context.request.post(url,{data:writes[0].body});assert.equal(replay.status(),200);
  assert.deepEqual((await current(context,base)).view,run.view);
  await checkExpedition({page,game,requests});
 }finally{release();await page.unroute(url,handler);}

 // Lose an offline move's acknowledgement after the server accepts it, then resume.
 const mela=await current(context,base),pendingStart=requests.length;
 await openExpeditionMap(game);await selectPlace(sheet,'reed-bank');
 await context.setOffline(true);await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
 await card.getByRole('button',{name:'Visit: Trzcinowy Brzeg',exact:true}).click();
 await sheet.getByText('Waiting for connection',{exact:true}).waitFor();
 assert.equal(await sheet.locator('[aria-current=location]').getAttribute('data-place-id'),'quiet-meadow');
 let serverAccepted=false,releaseAck;const ack=new Promise(resolve=>{releaseAck=resolve;});
 const holdAck=async route=>{
  if(route.request().method()==='POST'&&route.request().postDataJSON()?.command?.placeId==='reed-bank'){
   await route.fetch();serverAccepted=true;await ack;await route.abort().catch(()=>{});
  }else await route.continue();
 };
 await page.route(url,holdAck);
 try{
  await context.setOffline(false);await page.evaluate(()=>window.dispatchEvent(new Event('online')));
  for(let i=0;i<200&&!serverAccepted;i++)await page.waitForTimeout(25);assert.equal(serverAccepted,true);
  await page.reload();releaseAck();
  await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
  await page.getByRole('button',{name:'Continue',exact:true}).click();
  const run=await accepted(page,context,base,'reed-bank',mela.revision+1);
  assert.equal(await sheet.isVisible(),false);
  assert.ok(run.view.options.some(o=>o.id==='help-borys'));assert.equal(run.view.presentation.party.length,0);
  assert.equal(requests.slice(pendingStart).filter(r=>r.url===url).length,1,'reload reposted uncertain board move');
 }finally{releaseAck();await page.unroute(url,holdAck);}

 // The server rejects a stale command after a peer moved from that revision.
 const beforePeer=await current(context,base);
 const peer=await context.newPage();await peer.goto(base);
 let releaseStale;const fireStale=new Promise(resolve=>{releaseStale=resolve;});
 const holdStale=async route=>{if(route.request().method()==='POST')await fireStale;await route.continue();};
 await page.route(url,holdStale);
 try{
  await openExpeditionMap(game);await selectPlace(sheet,'quiet-meadow');
  const posted=page.waitForRequest(r=>r.url()===url&&r.method()==='POST');
  await card.getByRole('button').click();await posted;
  const peerStatus=await peer.evaluate(async({url,revision})=>{
   const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({
    id:crypto.randomUUID(),expectedRevision:revision,command:{type:'MOVE',placeId:'hive-square'},
   })});return response.status;
  },{url,revision:beforePeer.revision});
  assert.ok([200,202].includes(peerStatus));
  let peerRun;
  for(let i=0;i<200;i++){peerRun=await current(context,base);if(peerRun.revision>beforePeer.revision)break;await page.waitForTimeout(25);}
  assert.equal(peerRun.revision,beforePeer.revision+1);releaseStale();
  await sheet.getByText('The adventure changed. Check the saved scene.',{exact:true}).waitFor();
  assert.equal(await sheet.locator('[aria-current=location][aria-pressed=true]').getAttribute('data-place-id'),'hive-square');
  assert.equal((await current(context,base)).revision,peerRun.revision);
  await close.click();
 }finally{releaseStale();await page.unroute(url,holdStale);await peer.close();}
 await page.setViewportSize(viewport);
 console.log('PASS: map sheet focus, keyboard travel, locks, prerequisite, zoom, list, offline reload, replay and peer conflict');
}
