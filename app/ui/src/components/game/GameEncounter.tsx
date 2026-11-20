import type {ReactNode,RefObject} from 'react';
import type {GameView} from '../../game/gameApi';
import {gameCompanions} from '../../game/gameCompanions';
import {GameBackdrop, GameCharacter} from './art/GameArt';
import './gameEncounter.css';

export const encounterCopy={en:{text:'Text mode'},pl:{text:'Tryb tekstowy'}} as const;
/** Artwork comes only from the accepted public view; children retain their identity across modes. */
export function GameEncounter({view,children,stage=false,caption,textMode=false,encounterRef}:{
 view:GameView;children:ReactNode;stage?:boolean;caption?:ReactNode;textMode?:boolean;encounterRef?:RefObject<HTMLDivElement|null>;
}) {
 const presentation=view.presentation;
 const backdrop=presentation&&!textMode;
 const cast=stage&&backdrop?gameCompanions(view)?.current:undefined;
 return <div ref={encounterRef} className={presentation?'game-art game-encounter':'game-encounter'}>
  {backdrop||caption?<div className={backdrop?'game-encounter-backdrop':undefined} aria-hidden={caption?undefined:true}>
   {backdrop?<GameBackdrop assetId={presentation.backdropAssetId} cover={!!caption} decorative/>:null}
   {cast?.length ? <div className="game-encounter-stage" aria-hidden="true">{cast.map(character=>
    <GameCharacter key={character.id} assetId={character.assetId} expression={character.expression} decorative/>)}</div> : null}
   {caption}
  </div>:null}
  <div className="game-encounter-content">{children}</div>
 </div>;
}
