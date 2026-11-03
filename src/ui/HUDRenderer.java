import java.awt.*;
import java.util.Locale;
import java.util.function.BiConsumer;

/** One live HUD renderer used by the game and the editor preview. */
final class HUDRenderer {
    static final class Data {
        double health=75,maxHealth=115,stamina=60,speed,maxSpeed=7,multiplier=1,streak;
        double healthGhost=Double.NaN;
        boolean exhausted,equipped=true,playing=true,debug,lan;
        int players=2;String className="Gauntlet";
        final double[] cooldowns=new double[4];
    }
    final UISkins skins;final HUDLayout layout;
    HUDRenderer(UISkins skins,HUDLayout layout){this.skins=skins;this.layout=layout;}
    private void layer(Graphics2D parent,UISkins.Slot slot,HUDLayout.Placement group,Double fraction,BiConsumer<Graphics2D,HUDLayout.Placement> content){
        Graphics2D g=layout.layerGraphics(parent,slot);if(g==null)return;
        HUDLayout.Placement child=layout.get(slot),style=new HUDLayout.Placement(slot.piece());
        style.text=group.text&&child.text;style.defaults=group.defaults&&child.defaults;style.fontScale=group.fontScale*child.fontScale;style.color=child.color==null?group.color:child.color;
        if(fraction==null)skins.draw(g,slot,0,0,slot.width,slot.height);else skins.fill(g,slot,0,0,slot.width,slot.height,fraction);
        content.accept(g,style);g.dispose();
    }
    private void art(Graphics2D g,UISkins.Slot slot,HUDLayout.Placement p){layer(g,slot,p,null,(copy,style)->{});}
    void draw(Graphics2D graphics,int width,int height,Data data){
        for(HUDLayout.Piece piece:HUDLayout.Piece.values()){
            if(piece==HUDLayout.Piece.CROSSHAIR&&!data.playing||piece==HUDLayout.Piece.DEBUG&&!data.debug||piece==HUDLayout.Piece.LAN&&!data.lan)continue;
            Graphics2D g=layout.graphics(graphics,piece,width,height);if(g==null)continue;
            HUDLayout.Placement p=layout.get(piece);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            switch(piece){
                case HEALTH:resource(g,true,data.health,data.maxHealth,data.health<=0?"DEFEATED":null,p,data.healthGhost);break;
                case STAMINA:resource(g,false,data.stamina,100,data.exhausted?"RECOVERING":null,p,Double.NaN);break;
                case CLASS:
                    art(g,UISkins.Slot.CLASS_BADGE,p);layer(g,UISkins.Slot.CLASS_LABEL,p,null,(copy,style)->{
                        if(style.text){text(copy,style,20,new Color(230,236,242));String name="Class: "+data.className;copy.drawString(name,Math.max(0,240-copy.getFontMetrics().stringWidth(name)),26);}});break;
                case ABILITIES:
                    layer(g,UISkins.Slot.ABILITY_PANEL,p,null,(copy,style)->{if(!skins.images.containsKey(UISkins.Slot.ABILITY_PANEL)&&style.defaults){copy.setColor(new Color(15,20,28,215));copy.fillRoundRect(0,0,240,142,12,12);}});
                    layer(g,UISkins.Slot.ABILITY_LABELS,p,null,(copy,style)->{if(style.text){for(int i=0;i<4;i++){double remaining=data.cooldowns[i];text(copy,style,16,!data.equipped?new Color(140,149,160):remaining>0?new Color(244,193,108):new Color(125,231,182));
                        copy.drawString(CombatSystem.Move.values()[i].label+(!data.equipped?"  --":remaining>0?String.format(Locale.ROOT,"  %.1fs",remaining):"  Ready"),10,25+i*29);}}});break;
                case CROSSHAIR:
                    layer(g,UISkins.Slot.CROSSHAIR,p,null,(copy,style)->{if(!skins.images.containsKey(UISkins.Slot.CROSSHAIR)&&style.defaults){copy.setColor(style.color==null?new Color(245,245,245,190):style.color);copy.drawLine(7,12,17,12);copy.drawLine(12,7,12,17);}});break;
                case LAN:
                    layer(g,UISkins.Slot.LAN_STATUS,p,null,(copy,style)->{if(style.text){text(copy,style,16,new Color(160,230,192));copy.drawString("LAN match: "+data.players+"/8 players  |  F2: roster / leave",0,15);}});break;
                case DEBUG:
                    layer(g,UISkins.Slot.DEBUG_INFO,p,null,(copy,style)->{if(style.text){text(copy,style,16,new Color(230,236,242));
                        copy.drawString(String.format(Locale.ROOT,"Speed %.1f u/s  |  Max %.1f  |  Defense 0%%",data.speed,data.maxSpeed),0,16);
                        copy.drawString(String.format(Locale.ROOT,"Damage modifier %.2fx%s",data.multiplier,data.streak>0?String.format(Locale.ROOT,"  (%.1fs)",data.streak):""),0,40);}});break;
            }g.dispose();
        }
    }
    private static void text(Graphics2D g,HUDLayout.Placement p,int size,Color fallback){g.setFont(new Font("SansSerif",Font.BOLD,Math.max(1,(int)Math.round(size*p.fontScale))));g.setColor(p.color==null?fallback:p.color);}
    void resource(Graphics2D g,boolean health,double value,double maximum,String status,HUDLayout.Placement p,double ghost){
        UISkins.Slot frame=health?UISkins.Slot.HEALTH_FRAME:UISkins.Slot.STAMINA_FRAME,fill=health?UISkins.Slot.HEALTH_FILL:UISkins.Slot.STAMINA_FILL;
        UISkins.Slot background=health?UISkins.Slot.HEALTH_BACKGROUND:UISkins.Slot.STAMINA_BACKGROUND,label=health?UISkins.Slot.HEALTH_LABEL:UISkins.Slot.STAMINA_LABEL;
        // Custom bar art owns its transparent interior: never put the old background behind it.
        layer(g,background,p,null,(copy,style)->{if(!skins.images.containsKey(background)&&style.defaults&&!skins.images.containsKey(frame)&&!skins.images.containsKey(fill)){
            copy.setColor(new Color(12,17,24,235));copy.fillRoundRect(0,0,250,18,8,8);}});
        double fraction=Math.max(0,Math.min(1,value/Math.max(1,maximum)));
        if(health&&Double.isFinite(ghost)&&ghost>fraction)healthGhost(g,p,fraction,Math.min(1,ghost));
        layer(g,fill,p,fraction,(copy,style)->{if(!skins.images.containsKey(fill)&&style.defaults&&fraction>0){
            copy.setColor(health?new Color(226,87,91):status!=null?new Color(245,158,66):new Color(87,213,151));
            copy.fillRoundRect(2,2,Math.max(0,(int)Math.round(fraction*250)-4),14,6,6);}});
        layer(g,frame,p,null,(copy,style)->{if(!skins.images.containsKey(frame)&&style.defaults){copy.setColor(new Color(180,194,208));copy.drawRoundRect(0,0,250,18,8,8);}});
        layer(g,label,p,null,(copy,style)->{if(style.text){text(copy,style,12,new Color(230,236,242));copy.drawString((health?"HEALTH":"STAMINA")+"  "+(int)Math.ceil(Math.max(0,value))+" / "+(int)maximum+(status==null?"":"  "+status),0,20);}});
    }
    private void healthGhost(Graphics2D parent,HUDLayout.Placement group,double current,double ghost){
        UISkins.Slot slot=UISkins.Slot.HEALTH_FILL;Graphics2D g=layout.layerGraphics(parent,slot);if(g==null)return;
        float opacity=((AlphaComposite)g.getComposite()).getAlpha();g.setComposite(AlphaComposite.SrcOver.derive((float)(opacity*HealthFillGhost.OPACITY)));
        int start=(int)Math.round(current*slot.width),end=(int)Math.round(ghost*slot.width);g.clipRect(start,0,Math.max(0,end-start),slot.height);
        if(!skins.fill(g,slot,0,0,slot.width,slot.height,ghost)&&group.defaults&&layout.get(slot).defaults){
            g.setColor(new Color(226,87,91));g.fillRoundRect(2,2,Math.max(0,end-4),14,6,6);}
        g.dispose();
    }
}
