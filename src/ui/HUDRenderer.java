import java.awt.*;
import java.util.Locale;

/** One live HUD renderer used by the game and the editor preview. */
final class HUDRenderer {
    static final class Data {
        double health=75,maxHealth=115,stamina=60,speed,maxSpeed=7,multiplier=1,streak;
        boolean exhausted,equipped=true,playing=true,debug,lan;
        int players=2;String className="Gauntlet";
        final double[] cooldowns=new double[4];
    }
    final UISkins skins;final HUDLayout layout;
    HUDRenderer(UISkins skins,HUDLayout layout){this.skins=skins;this.layout=layout;}
    void draw(Graphics2D graphics,int width,int height,Data data){
        for(HUDLayout.Piece piece:HUDLayout.Piece.values()){
            if(piece==HUDLayout.Piece.CROSSHAIR&&!data.playing||piece==HUDLayout.Piece.DEBUG&&!data.debug||piece==HUDLayout.Piece.LAN&&!data.lan)continue;
            Graphics2D g=layout.graphics(graphics,piece,width,height);if(g==null)continue;
            HUDLayout.Placement p=layout.get(piece);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            switch(piece){
                case HEALTH:resource(g,true,data.health,data.maxHealth,data.health<=0?"DEFEATED":null,p);break;
                case STAMINA:resource(g,false,data.stamina,100,data.exhausted?"RECOVERING":null,p);break;
                case CLASS:
                    skins.draw(g,UISkins.Slot.CLASS_BADGE,0,0,240,38);skins.draw(g,UISkins.Slot.CLASS_LABEL,0,0,240,38);
                    if(p.text){text(g,p,20,new Color(230,236,242));String name="Class: "+data.className;g.drawString(name,Math.max(0,240-g.getFontMetrics().stringWidth(name)),26);}break;
                case ABILITIES:
                    if(!skins.draw(g,UISkins.Slot.ABILITY_PANEL,0,0,240,142)&&p.defaults){g.setColor(new Color(15,20,28,215));g.fillRoundRect(0,0,240,142,12,12);}
                    skins.draw(g,UISkins.Slot.ABILITY_LABELS,0,0,240,142);
                    if(p.text){for(int i=0;i<4;i++){double remaining=data.cooldowns[i];text(g,p,16,!data.equipped?new Color(140,149,160):remaining>0?new Color(244,193,108):new Color(125,231,182));
                        g.drawString(CombatSystem.Move.values()[i].label+(!data.equipped?"  --":remaining>0?String.format(Locale.ROOT,"  %.1fs",remaining):"  Ready"),10,25+i*29);}}break;
                case CROSSHAIR:
                    if(!skins.draw(g,UISkins.Slot.CROSSHAIR,0,0,24,24)&&p.defaults){g.setColor(p.color==null?new Color(245,245,245,190):p.color);g.drawLine(7,12,17,12);g.drawLine(12,7,12,17);}break;
                case LAN:
                    skins.draw(g,UISkins.Slot.LAN_STATUS,0,0,600,22);if(p.text){text(g,p,16,new Color(160,230,192));g.drawString("LAN match: "+data.players+"/8 players  |  F2: roster / leave",0,15);}break;
                case DEBUG:
                    skins.draw(g,UISkins.Slot.DEBUG_INFO,0,0,650,48);if(p.text){text(g,p,16,new Color(230,236,242));
                        g.drawString(String.format(Locale.ROOT,"Speed %.1f u/s  |  Max %.1f  |  Defense 0%%",data.speed,data.maxSpeed),0,16);
                        g.drawString(String.format(Locale.ROOT,"Damage modifier %.2fx%s",data.multiplier,data.streak>0?String.format(Locale.ROOT,"  (%.1fs)",data.streak):""),0,40);}break;
            }g.dispose();
        }
    }
    private static void text(Graphics2D g,HUDLayout.Placement p,int size,Color fallback){g.setFont(new Font("SansSerif",Font.BOLD,Math.max(1,(int)Math.round(size*p.fontScale))));g.setColor(p.color==null?fallback:p.color);}
    void resource(Graphics2D g,boolean health,double value,double maximum,String status,HUDLayout.Placement p){
        UISkins.Slot frame=health?UISkins.Slot.HEALTH_FRAME:UISkins.Slot.STAMINA_FRAME,fill=health?UISkins.Slot.HEALTH_FILL:UISkins.Slot.STAMINA_FILL;
        UISkins.Slot background=health?UISkins.Slot.HEALTH_BACKGROUND:UISkins.Slot.STAMINA_BACKGROUND,label=health?UISkins.Slot.HEALTH_LABEL:UISkins.Slot.STAMINA_LABEL;
        skins.draw(g,label,0,-25,250,25);
        if(p.text){text(g,p,12,new Color(230,236,242));g.drawString((health?"HEALTH":"STAMINA")+"  "+(int)Math.ceil(Math.max(0,value))+" / "+(int)maximum+(status==null?"":"  "+status),0,-5);}
        // Custom bar art owns its transparent interior: never put the old background behind it.
        if(!skins.draw(g,background,0,0,250,18)&&p.defaults&&!skins.images.containsKey(frame)&&!skins.images.containsKey(fill)){
            g.setColor(new Color(12,17,24,235));g.fillRoundRect(0,0,250,18,8,8);}
        double fraction=Math.max(0,Math.min(1,value/Math.max(1,maximum)));
        if(!skins.fill(g,fill,0,0,250,18,fraction)&&p.defaults&&fraction>0){
            g.setColor(health?new Color(226,87,91):status!=null?new Color(245,158,66):new Color(87,213,151));
            g.fillRoundRect(2,2,Math.max(0,(int)Math.round(fraction*250)-4),14,6,6);}
        if(!skins.draw(g,frame,0,0,250,18)&&p.defaults){g.setColor(new Color(180,194,208));g.drawRoundRect(0,0,250,18,8,8);}
    }
}
