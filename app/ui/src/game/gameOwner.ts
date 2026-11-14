let owner:string|null|undefined;
const listeners=new Set<()=>void>();
export const activeGameOwner=()=>owner;
export const ownerAllowed=(expected:string)=>owner===undefined||owner===expected;
export function activateGameOwner(next:string|null){owner=next;if(next)bus();listeners.forEach(listener=>listener());}
export function watchGameOwner(listener:()=>void){listeners.add(listener);return()=>{listeners.delete(listener);};}
let channel:BroadcastChannel|undefined;
function bus(){
 if(!channel&&typeof BroadcastChannel!=='undefined'){
  channel=new BroadcastChannel('capybee-game-revisions');
  channel.onmessage=event=>{const value=event.data;if(value?.kind==='logout'&&value.owner===owner){activateGameOwner(null);window.dispatchEvent(new Event('game-session-changed'));return;}if(value&&typeof value.owner==='string'&&typeof value.runId==='string'&&Number.isSafeInteger(value.revision))window.dispatchEvent(new CustomEvent('game-revision',{detail:value}));};
 }
 return channel;
}
export function notifyGameRevision(owner:string,runId:string,revision:number){
 const detail={owner,runId,revision};bus()?.postMessage(detail);window.dispatchEvent(new CustomEvent('game-revision',{detail}));
}

export function notifyGameLogout(owner:string){bus()?.postMessage({kind:'logout',owner});}
