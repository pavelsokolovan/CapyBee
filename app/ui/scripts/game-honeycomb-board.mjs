import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const evidence=fileURLToPath(new URL('../../../docs/evidence/30.3/',import.meta.url));
const current=async(context,base)=>(await context.request.get(base+'/api/game/runs/current')).json();
async function accepted(page,context,base,placeId,revision){
 await page.waitForFunction(({placeId,revision})=>{
  const cell=document.querySelector('.game-board [aria-current=location]');
  return cell?.dataset.placeId===placeId&&document.querySelector('.game-run [role=status]')?.textContent==='Saved';
 },{placeId,revision});
 const run=await current(context,base);assert.equal(run.revision,revision);assert.equal(run.view.places.currentPlaceId,placeId);return run;
}
async function shot(page,selector,name){
 const surface=page.locator(selector),viewport=page.viewportSize();
 const height=Math.ceil((await surface.boundingBox()).height);
 try{await page.setViewportSize({width:viewport.width,height:Math.max(viewport.height,height+260)});await surface.evaluate(n=>n.scrollIntoView({block:'center'}));await surface.screenshot({path:`${evidence}${name}.png`});}
 finally{await page.setViewportSize(viewport);}
}
export async function checkBoardRoute({page,context,base,requests}){
 await mkdir(evidence,{recursive:true});
 await page.emulateMedia({reducedMotion:'no-preference',forcedColors:'none'});
 const game=page.locator('.game-run'),board=game.locator('.game-board');
 const initial=await current(context,base);assert.equal(initial.packVersion,'1.3.0');
 assert.equal(await board.count(),1);assert.equal(await game.locator('.game-places').count(),1);
 assert.equal(await board.locator('[data-place-id]').count(),7);assert.equal(await board.locator('.game-tile-marker').count(),0);
 assert.equal(await board.locator('.game-board-player').count(),1);assert.equal(await board.locator('.game-hex-map').evaluate(n=>getComputedStyle(n).display),'grid');
 assert.ok(await board.locator('.game-board-art').evaluateAll(nodes=>nodes.every(n=>n.getBoundingClientRect().width>=64)));
 assert.ok(await board.locator('.game-board-player').evaluate(n=>n.getBoundingClientRect().width>=32));
 await shot(page,'.game-board','board-mobile');
 const beforeInspect=requests.length;
 await board.getByRole('button',{name:'Look: Cicha Łąka',exact:true}).click();
 assert.equal(await board.locator('.game-place-inspection h4').evaluate(n=>n===document.activeElement),true);
 await page.keyboard.press('Escape');assert.equal(await board.getByRole('button',{name:'Look: Cicha Łąka',exact:true}).evaluate(n=>n===document.activeElement),true);
 assert.equal(requests.length,beforeInspect);
 // Hold the one MOVE before the server receives it. Nothing in the accepted view may move early.
 const from=await current(context,base),url=`${base}/api/game/runs/${from.runId}/commands`;
 let release;const barrier=new Promise(resolve=>{release=resolve;});
 const handler=async route=>{if(route.request().method()==='POST'&&route.request().postDataJSON()?.command?.placeId==='quiet-meadow')await barrier;await route.continue();};
 await context.route(url,handler);const before=requests.length;
 try{
  const outgoing=page.waitForRequest(r=>r.url()===url&&r.method()==='POST');
  await board.getByRole('button',{name:'Visit: Cicha Łąka',exact:true}).click();await outgoing;
  await game.getByText('Saving / checking',{exact:true}).waitFor();
  assert.equal(await board.locator('[aria-current=location]').getAttribute('data-place-id'),'hive-square');
  assert.equal(await board.locator('.game-board-player').getAttribute('data-current-place'),'hive-square');
  assert.equal(await game.locator('.game-current-characters').innerText().then(s=>s.includes('Mela')),false);
  assert.equal((await current(context,base)).revision,from.revision);
  await shot(page,'.game-board','destination-pending');release();
  const run=await accepted(page,context,base,'quiet-meadow',from.revision+1);
  assert.ok(await board.locator('.game-board-player').evaluate(n=>n.style.transition.includes('200ms')&&getComputedStyle(n).transitionDuration.includes('0.2s')),'accepted adjacent marker did not animate on its stable layout');
  assert.ok(run.view.options.some(o=>o.id==='help-mela'));assert.equal(run.view.presentation.party.length,0);
  assert.ok(run.view.characters.some(c=>c.inCurrentScene&&c.name==='Mela'));
  assert.equal(await game.getByRole('heading',{name:'Current moment',exact:true}).evaluate(n=>n===document.activeElement),true);
  const writes=requests.slice(before).filter(r=>r.url===url);assert.equal(writes.length,1);assert.deepEqual(writes[0].body.command,{type:'MOVE',placeId:'quiet-meadow'});
  const replay=await context.request.post(url,{data:writes[0].body});assert.equal(replay.status(),200);
  const same=await current(context,base);assert.equal(same.revision,run.revision);assert.deepEqual(same.view,run.view);
  await shot(page,'.game-current-characters','accepted-encounter');
 }finally{release();await context.unroute(url,handler);}
 // An offline neighboring move retains the accepted character and marker. Lose its acknowledgement after acceptance, then reload.
 const mela=await current(context,base);const pendingStart=requests.length;
 await context.setOffline(true);await page.evaluate(()=>window.dispatchEvent(new Event('offline')));
 await board.getByRole('button',{name:'Visit: Trzcinowy Brzeg',exact:true}).click();await game.getByText('Waiting for connection',{exact:true}).waitFor();
 assert.equal(await board.locator('[aria-current=location]').getAttribute('data-place-id'),'quiet-meadow');
 let serverAccepted=false,releaseAck;const ack=new Promise(resolve=>{releaseAck=resolve;});
 const holdAck=async route=>{
  if(route.request().method()==='POST'&&route.request().postDataJSON()?.command?.placeId==='reed-bank'){
   await route.fetch();serverAccepted=true;await ack;await route.abort().catch(()=>{});
  }else await route.continue();
 };
 await context.route(url,holdAck);
 try{
  await context.setOffline(false);await page.evaluate(()=>window.dispatchEvent(new Event('online')));
  for(let i=0;i<200&&!serverAccepted;i++)await page.waitForTimeout(25);assert.equal(serverAccepted,true);
  await page.reload();releaseAck();await page.getByRole('button',{name:'Play',exact:true}).click();await page.getByRole('button',{name:/Adventures/}).click();
  await page.getByRole('button',{name:'Continue',exact:true}).click();
  const run=await accepted(page,context,base,'reed-bank',mela.revision+1);
  assert.ok(run.view.options.some(o=>o.id==='help-borys'));assert.equal(run.view.presentation.party.length,0);
  assert.equal(requests.slice(pendingStart).filter(r=>r.url===url).length,1,'reload reposted uncertain board move');
  const stable=await current(context,base);assert.deepEqual(stable.view,run.view);
 }finally{releaseAck();await context.unroute(url,holdAck);}
 // A peer advances the accepted run. This tab must reconcile a stale selection without speculative relocation.
 const beforePeer=await current(context,base);
 const peerTab=await context.newPage();await peerTab.goto(base);
 const peerStatus=await peerTab.evaluate(async({url,revision})=>{const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({id:crypto.randomUUID(),expectedRevision:revision,command:{type:'MOVE',placeId:'hive-square'}})});return response.status;},{url,revision:beforePeer.revision});
 await peerTab.close();assert.ok([200,202].includes(peerStatus));
 let peerRun;
 for(let i=0;i<200;i++){peerRun=await current(context,base);if(peerRun.revision>beforePeer.revision)break;await page.waitForTimeout(25);}
 await board.getByRole('button',{name:'Visit: Cicha Łąka',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('.game-board [aria-current=location]')?.dataset.placeId==='hive-square');
 assert.equal((await current(context,base)).revision,peerRun.revision);assert.equal((await current(context,base)).view.presentation.party.length,0);
 // Recovery uses a changed status; subsequent read-only inspection and mode controls remain usable.
 const names=await board.locator('[data-place-id] > strong').allTextContents();
 await board.getByRole('button',{name:'Show place list',exact:true}).click();assert.deepEqual(await board.locator('[data-place-id] > strong').allTextContents(),names);
 assert.equal(await board.locator('.game-board-player').count(),0);await shot(page,'.game-board','board-list');
 await board.getByRole('button',{name:'Show place list',exact:true}).click();
 await page.emulateMedia({reducedMotion:'reduce'});assert.equal(await board.locator('.game-board-player').evaluate(n=>getComputedStyle(n).transitionDuration),'0s');
 await game.evaluate(n=>n.style.zoom='2');await page.waitForFunction(()=>!document.querySelector('.game-board .game-hex-map'));
 assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth));await shot(page,'.game-board','board-zoom-list');await game.evaluate(n=>n.style.zoom='1');
 await page.emulateMedia({forcedColors:'active'});await shot(page,'.game-board','board-forced-colors');assert.deepEqual(await board.locator('[data-place-id] > strong').allTextContents(),names);
 const art=await page.addStyleTag({content:'.game-board svg{visibility:hidden!important}'});assert.deepEqual(await board.locator('[data-place-id] > strong').allTextContents(),names);await art.evaluate(n=>n.remove());
 await page.emulateMedia({forcedColors:'none',reducedMotion:'no-preference'});await page.setViewportSize({width:1280,height:1000});await shot(page,'.game-board','board-desktop');await page.setViewportSize({width:360,height:800});
 await writeFile(`${evidence}browser-verification.json`,JSON.stringify({schemaVersion:1,packVersion:'1.3.0',mobileWidth:360,places:7,minTerrain:64,minPlayer:32,oneMoveAtomicEntry:true,adjacentAnimationMs:200,pendingRetained:true,offlineUncertainReload:true,replay:true,stalePeerSelection:true,listParity:true,zoom:'200%',reducedMotion:true,forcedColors:true,hiddenArtwork:true,liveProvider:false},null,2)+'\n');
 console.log('PASS: primary seven-place illustrated board, one MOVE/encounter, pending, offline uncertain reload, replay, peer conflict, keyboard, list/zoom and art fallbacks');
}
