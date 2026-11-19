import type {ReactNode} from 'react';
import type {GameView} from '../../game/gameApi';
import {gameCompanions} from '../../game/gameCompanions';
import {GameBackdrop, GameCharacter} from './art/GameArt';
import './gameEncounter.css';

export const encounterCopy={en:{text:'Text mode'},pl:{text:'Tryb tekstowy'}} as const;
/** Artwork comes only from the accepted public view; children retain their identity across modes. */
export function GameEncounter({view,children,stage=false}:{view:GameView;children:ReactNode;stage?:boolean}) {
 const presentation='presentation' in view?view.presentation:null;
 return <div className={presentation?'game-art game-encounter':'game-encounter'}>
  {presentation?<div className="game-encounter-backdrop" aria-hidden="true"><GameBackdrop assetId={presentation.backdropAssetId} decorative/>
   {stage ? <div className="game-encounter-stage">{gameCompanions(view)?.current.map(character=>
    <GameCharacter key={character.id} assetId={character.assetId} expression={character.expression} decorative/>)}</div> : null}
  </div>:null}
  <div className="game-encounter-content">{children}</div>
 </div>;
}
