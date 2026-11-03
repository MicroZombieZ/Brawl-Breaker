/** Cosmetic delayed health fill, independent of repaint frequency and gameplay damage. */
final class HealthFillGhost {
    static final double HOLD_SECONDS=.3, CATCH_SECONDS=.45, OPACITY=.4;
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
            elapsed=Math.min(CATCH_SECONDS,elapsed+dt);
            // Normalized exponential ease-in: increasing speed, with an exact endpoint.
            double progress=Math.expm1(3*elapsed/CATCH_SECONDS)/Math.expm1(3);
            ghost=elapsed==CATCH_SECONDS?current:catchFrom+(current-catchFrom)*progress;
        }
        ghost=Math.max(current,ghost);
    }
    double fraction(){return initialized?ghost:Double.NaN;}
    private static double fraction(double health,double maximum){return Math.max(0,Math.min(1,health/Math.max(1,maximum)));}
}
