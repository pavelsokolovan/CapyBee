import {afterEach,beforeEach,it,expect,vi} from 'vitest';
import {restoreGameSession} from './restoreGameSession';
import * as session from '../sessionPersistence';
beforeEach(()=>{vi.spyOn(session,'getSessionToken').mockReturnValue('existing-token');vi.spyOn(session,'storeSessionToken').mockImplementation(()=>{});vi.spyOn(session,'clearSessionToken').mockImplementation(()=>{});});
afterEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals();});
it('uses only the existing auth restore boundary and rotates its token for the same owner',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({authenticated:true,id:'owner',sessionToken:'rotated'}))));
 await restoreGameSession('owner');expect(fetch).toHaveBeenCalledWith('/api/session/restore',expect.objectContaining({body:JSON.stringify({token:'existing-token'}),credentials:'include'}));expect(session.storeSessionToken).toHaveBeenCalledWith('rotated');
});
it('rejects restoration into a different owner',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response(JSON.stringify({authenticated:true,id:'other',sessionToken:'rotated'}))));
 await expect(restoreGameSession('owner')).rejects.toThrow('GAME_OWNER_CHANGED');expect(session.storeSessionToken).not.toHaveBeenCalled();
});
it('clears expired tokens without inventing another authentication mechanism',async()=>{
 vi.stubGlobal('fetch',vi.fn().mockResolvedValue(new Response('{}',{status:401})));
 await expect(restoreGameSession('owner')).rejects.toThrow('UNAUTHENTICATED');expect(session.clearSessionToken).toHaveBeenCalled();
});
