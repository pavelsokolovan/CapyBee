import assert from 'node:assert/strict';
import {mkdir, writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';

const evidence = fileURLToPath(new URL('../../../docs/evidence/30.4/', import.meta.url));
export async function assertCompanionUI({page, game, run}) {
 const cast = new Map(run.view.characters.map(c=>[c.id,c]));
 const known = run.view.presentation.characters.filter(c=>cast.has(c.id));
 const currentIds = known.filter(c=>cast.get(c.id).inCurrentScene).map(c=>c.id);
 const partyIds = run.view.presentation.party.filter(id=>known.some(c=>c.id===id&&c.recruited));
 await page.waitForFunction(({currentIds,partyIds})=>{
  const ids=selector=>[...document.querySelectorAll(selector)].map(n=>n.dataset.characterId);
  return JSON.stringify(ids('.game-current-characters .game-character-card'))===JSON.stringify(currentIds)
   &&JSON.stringify(ids('.game-party-members li'))===JSON.stringify(partyIds);
 },{currentIds,partyIds});
 for(const meta of known.filter(c=>currentIds.includes(c.id))){
  const card=game.locator(`.game-current-characters [data-character-id=${JSON.stringify(meta.id)}]`);
  const content=await card.textContent();
  assert.ok(content.includes(cast.get(meta.id).name));
  if(meta.trait)assert.ok(content.includes(meta.trait));
  if(meta.need)assert.ok(content.includes(meta.need));
 }
 assert.ok(await game.locator('.game-current-characters svg,.game-party svg').evaluateAll(nodes=>nodes.every(n=>n.getAttribute('aria-hidden')==='true'&&n.getAttribute('focusable')==='false')));
 assert.equal(await game.locator('.game-party button,.game-current-characters button').count(),0,'portrait surfaces added a mutation control');
 return {currentIds,partyIds};
}

export async function holdCompanionChoice({page,context,base,game,run,requests,label}) {
 const url=`${base}/api/game/runs/${run.runId}/commands`, option=run.view.options.find(o=>o.label===label);
 assert.ok(option?.enabled);
 let release;const barrier=new Promise(resolve=>{release=resolve;});
 const handler=async route=>{if(route.request().method()==='POST'&&route.request().postDataJSON()?.command?.optionId===option.id)await barrier;await route.continue();};
 await context.route(url,handler);
 const before=requests.length;
 try {
  const outgoing=page.waitForRequest(r=>r.url()===url&&r.method()==='POST'&&r.postDataJSON()?.command?.optionId===option.id);
  await game.getByRole('button',{name:label,exact:true}).click();await outgoing;
  await game.getByText('Saving / checking',{exact:true}).waitFor();
  await assertCompanionUI({page,game,run});
  const unchanged=await (await context.request.get(`${base}/api/game/runs/${run.runId}`)).json();
  assert.equal(unchanged.revision,run.revision);assert.deepEqual(unchanged.view.presentation.party,run.view.presentation.party);
  assert.equal(await game.locator('fieldset').evaluate(n=>n.disabled),true);
  release();await page.waitForFunction(()=>document.querySelector('.game-run [role=status]')?.textContent==='Saved');
  assert.equal(requests.slice(before).filter(r=>r.url===url).length,1,'help submitted more than the existing choice');
 }finally{release();await context.unroute(url,handler);}
}

export async function captureCompanionEvidence({page,game,run,label,full=false}) {
 await mkdir(evidence,{recursive:true});await assertCompanionUI({page,game,run});
 // Center the surface in the scrolling adventure so sticky navigation does not cover it.
 const shot=async(selector,name)=>{
  const surface=game.locator(selector);
  const wasClosed=await surface.evaluate(node=>node instanceof HTMLDetailsElement&&!node.open);
  if (wasClosed) await surface.locator('summary').click();
  try {
   await surface.evaluate(n=>n.scrollIntoView({block:'center'}));
   await surface.screenshot({path:`${evidence}${name}.png`});
   if (selector==='.game-current-characters') {
    assert.ok(await surface.locator('.game-character-portrait').evaluateAll(nodes=>nodes.every(node=>{
     const width=node.getBoundingClientRect().width;
     return width===0||width>=64;
    })));
   }
  } finally {
   if (wasClosed) await surface.locator('summary').click();
  }
 };
 await shot('.game-current-characters',`${label}-characters`);
 if (run.view.presentation.party.length) await shot('.game-party',`${label}-party`);
 if(!full)return;
 const viewport=page.viewportSize(),zoom=await game.evaluate(n=>n.style.zoom);
 const reduced=await page.evaluate(()=>matchMedia('(prefers-reduced-motion: reduce)').matches);
 let hidden;
 try{
  await page.setViewportSize({width:360,height:800});await page.emulateMedia({reducedMotion:'reduce'});
  await shot('.game-party',`party-mobile`);
  await game.evaluate(n=>n.style.zoom='2');
  assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=document.documentElement.clientWidth),'companion layout overflow at 360px/200%');
  await shot('.game-party',`party-zoom`);
  await game.evaluate(n=>n.style.zoom='1');
  const names=await game.locator('.game-party-members strong').allTextContents();
  await page.emulateMedia({forcedColors:'active'});
  assert.deepEqual(await game.locator('.game-party-members strong').allTextContents(),names);
  await shot('.game-party',`party-forced-colors`);
  const summary=game.locator('.game-character-history summary');await summary.focus();await page.keyboard.press('Enter');
  assert.equal(await game.locator('.game-character-history').evaluate(n=>n.open),true);
  assert.notEqual(await summary.evaluate(n=>getComputedStyle(n).outlineStyle),'none');
  await page.keyboard.press('Enter');
  await page.emulateMedia({forcedColors:'none'});
  const before=await game.locator('.game-party').textContent();
  hidden=await page.addStyleTag({content:'.game-party svg,.game-current-characters svg,.game-character-history svg{display:none!important}'});
  assert.equal(await game.locator('.game-party').textContent(),before);
  await shot('.game-current-characters',`characters-without-art`);
  await hidden.evaluate(n=>n.remove());hidden=null;
  await page.setViewportSize({width:1280,height:900});
  await shot('.game-current-characters',`characters-desktop`);
  await shot('.game-party',`party-desktop`);
  assert.ok(await game.locator('.game-party-portrait').evaluateAll(nodes=>nodes.every(n=>n.getBoundingClientRect().width>=40)));
  assert.ok(await game.locator('.game-party,.game-current-characters').evaluateAll(nodes=>nodes.every(n=>getComputedStyle(n).animationName==='none')));
  await writeFile(`${evidence}browser-verification.json`,JSON.stringify({schemaVersion:1,packVersion:run.packVersion,
   acceptedPartyMembers:names.length,mobileWidth:360,zoom:'200%',keyboardHistory:true,forcedColors:true,hiddenArtTextParity:true,
   reducedMotion:true,desktopWidth:1280,liveProviders:false},null,2)+'\n');
 }finally{
  if(hidden)await hidden.evaluate(n=>n.remove());
  await game.evaluate((n,value)=>n.style.zoom=value,zoom);
  await page.setViewportSize(viewport);await page.emulateMedia({forcedColors:'none',reducedMotion:reduced?'reduce':'no-preference'});
 }
}
