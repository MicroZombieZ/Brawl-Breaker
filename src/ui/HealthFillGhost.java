/** Cosmetic delayed health fill, independent of repaint frequency and gameplay damage. */
final class HealthFillGhost {
    static final double HOLD_SECONDS=.15, OPACITY=.4;
    // Progress per second, relative to the gap when catch-up starts.
    static final double START_SPEED=.9, MAX_SPEED_MULTIPLIER=3.2;
    // Accelerate through most of the sweep, then finish briefly at the speed cap.
    static final double ACCELERATION_SECONDS=.5, GROWTH_RATE=Math.log(MAX_SPEED_MULTIPLIER)/ACCELERATION_SECONDS;
    private double previousHealth,previousMaximum,ghost,hold,elapsed,catchFrom;
    private boolean initialized;
    void reset(double health,double maximum){
        previousHealth=health;previousMaximum=maximum;ghost=fraction(health,maximum);
        hold=elapsed=0;catchFrom=ghost;initialized=true;
    }
    void update(double dt,double health,double maximum){
        if(!Double.isFinite(health)||!Double.isFinite(maximum)||maximum<=0)return;
        double current=fraction(health,maximum);
        if(!initialized||maximum!=previousMaximum||health>previousHealth){reset(health,maximum);return;}
        if(health<previousHealth){
            ghost=Math.max(ghost,fraction(previousHealth,maximum));hold=HOLD_SECONDS;elapsed=0;catchFrom=ghost;
            previousHealth=health;return;
        }
        if(!Double.isFinite(dt)||dt<=0)return;
        double waiting=Math.min(hold,dt);hold-=waiting;dt-=waiting;
        if(hold==0&&dt>0){
            elapsed+=dt;
            // Integrate exponential speed until capped, then continue at the cap.
            double capTime=Math.log(MAX_SPEED_MULTIPLIER)/GROWTH_RATE;
            double accelerating=Math.min(elapsed,capTime);
            double progress=START_SPEED*Math.expm1(GROWTH_RATE*accelerating)/GROWTH_RATE
                    +START_SPEED*MAX_SPEED_MULTIPLIER*Math.max(0,elapsed-capTime);
            ghost=progress>=1?current:catchFrom+(current-catchFrom)*progress;
        }
        ghost=Math.max(current,ghost);
    }
    double fraction(){return initialized?ghost:Double.NaN;}
    private static double fraction(double health,double maximum){return Math.max(0,Math.min(1,health/Math.max(1,maximum)));}
}
