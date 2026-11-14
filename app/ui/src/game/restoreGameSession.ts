import {getSessionToken,clearSessionToken,storeSessionToken} from '../sessionPersistence';
/** Reuses the app's existing restore token; never creates a separate game token. */
export async function restoreGameSession(owner:string):Promise<void> {
 const token=getSessionToken();if(!token)throw new Error('UNAUTHENTICATED');
 const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),25000);
 try {
  const response=await fetch('/api/session/restore',{method:'POST',credentials:'include',cache:'no-store',
   headers:{'Content-Type':'application/json'},body:JSON.stringify({token}),signal:abort.signal});
  if(!response.ok){clearSessionToken();throw new Error('UNAUTHENTICATED');}
  const data=await response.json();
  if(!data.authenticated || data.id!==owner)throw new Error('GAME_OWNER_CHANGED');
  if(typeof data.sessionToken==='string')storeSessionToken(data.sessionToken);
 } finally {clearTimeout(timer);}
}
