package cz.mendelu.pjj.anotherStrategy.game.mechanics;

import cz.mendelu.pjj.anotherStrategy.game.map.Druh;
import cz.mendelu.pjj.anotherStrategy.greenfoot.EventActor;
import cz.mendelu.pjj.anotherStrategy.greenfoot.NewGame;
import cz.mendelu.pjj.anotherStrategy.greenfoot.PoleActor;
import greenfoot.Greenfoot;

/**
 * This is an example code from the first project I have ever done in Java.
 * Its name was Yet another strategy, which was a strategic game I made as a school project in Java Applications course.
 * The project was using greenfoot library to draw 2d map and work with actors.
 *
 *
 * Overall, the most obvious mistake is mixing czech and english language in variable and method names.
 *
 * @author David Dvořák
 * 2019
 */

// This class serves as a builder for events occurring during the game.
public class EventGenerator{

    /*
    2D coordinates for the map.
    - These should have been in its own structure, preferably new class, which would unify the variable structure across entire code.
     */
    private int x;
    private int y;

    /*
    Constructor.
    - Some logic in constructor would be ok, but it would be better to move to a separate method, in case there were some changes or other modes.
     */
    public EventGenerator(){
        randomS(NewGame.WWIDTH-1, NewGame.WHEIGHT-1);
        MyEvent mE = MyEvent.randomEvent(); // short and meaningless variable name, should've been 'myEvent'

        // Switch. Could've used an array, or even better, a hashmap.
        switch (mE){
            case boure:
                eventBoure();
                break;
            case sopka:
                eventSopka();
                break;
            case sucho:
                eventSucho();
                break;
            case potopa:
                eventPotopa();
                break;
            case pozar:
                eventPozar();
                break;
            default: break;
        }
    };

    /*
    - It would be better to have this generated in a separate class - utils, or even CoordinateUtils.
    - The name of this method is too short, should be randomCoordinates
    - Arguments should be named so that it's obvious it's the limit of generated values. LimitX for example.
    */
    private void randomS(int sx, int sy){
        this.x = Greenfoot.getRandomNumber(sx);
        this.y = Greenfoot.getRandomNumber(sy);
    }

    // Following methods are for specific events.
    // There are many ways to do this, the most efficient one would be to use classes and polymorphism.
    private void eventBoure(){
        System.out.println("BOUŘE!"); // Should've used a logger.
        new EventActor(MyEvent.boure);
    }

    private void eventSopka(){
        System.out.println("VYBUCHLA SOPKA!");
        while(NewGame.getWorld().getObjectsAt(x, y, PoleActor.class).get(0).getDruh() != Druh.hory){
            randomS(NewGame.WWIDTH-1, NewGame.WHEIGHT-1);
        }
        NewGame.getWorld().getObjectsAt(x, y, PoleActor.class).get(0).setDruh(Druh.sopka, 0);
        Greenfoot.playSound("Volcano.mp3");
    }

    private void eventSucho(){
        System.out.println("SUCHO!");
        int s = 0; int c = 0; // Variable names are confusing.
        while(s < 3){ // spacing
            if(NewGame.getWorld().getObjectsAt(x, y, PoleActor.class).get(0).getDruh() != Druh.louka){ // spacing
                randomS(NewGame.WWIDTH-1, NewGame.WHEIGHT-1);
                c+=1;
                // Shortened
                // s = c==20 ? 3;
                if (c == 20){
                    s=3;
                }
            }else{ // spacing
                NewGame.getWorld().getObjectsAt(x, y, PoleActor.class).get(0).setDruh(Druh.poust,0); // spacing
                s+=1;
            }
        }
    }

    // Repeating the same mistakes.
    private void eventPotopa(){
        System.out.println("POTOPA!");
        new EventActor(MyEvent.potopa);
        int i = 0; int c = 0;
        while (i<4) {
            PoleActor p = NewGame.getWorld().getObjectsAt(x, y, PoleActor.class).get(0);
            if (p.getDruh() != Druh.louka && p.getDruh() != Druh.poust && p.getDruh() != Druh.les){
                randomS(NewGame.WWIDTH-1, NewGame.WHEIGHT-1);
                c+=1;
                if (c == 20){i=4;}
            }else{
                p.setDruh(Druh.jezero,Greenfoot.getRandomNumber(10)+1);
                i+=1;
            }
        }
        Greenfoot.playSound("Flood.mp3");
    }

    // Repeating the same mistakes.
    private void eventPozar(){
        System.out.println("POŽÁR!");
        int i = 0; int c = 0;
        while (i<4) {
            PoleActor p = NewGame.getWorld().getObjectsAt(x, y, PoleActor.class).get(0);
            if (p.getDruh() != Druh.les){
                randomS(NewGame.WWIDTH-1, NewGame.WHEIGHT-1);
                c+=1;
                if (c == 20){i=4;}
            }else{
                p.setDruh(Druh.ohen,0);
                i+=1;
            }
        }
    }
}
