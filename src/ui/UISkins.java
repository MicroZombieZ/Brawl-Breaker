import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Local cosmetic HUD replacements. Text and gameplay values stay dynamic. */
final class UISkins {
    enum Slot {
        HEALTH_FRAME(250,18), HEALTH_FILL(250,18), STAMINA_FRAME(250,18), STAMINA_FILL(250,18),
        CLASS_BADGE(240,38), ABILITY_PANEL(240,142), CROSSHAIR(24,24);
        final int width,height;
        Slot(int width,int height){this.width=width;this.height=height;}
    }
    final Map<Slot,BufferedImage> images=new EnumMap<>(Slot.class);
    void load(Path directory){
        images.clear();
        for(Slot slot:Slot.values()){
            Path path=directory.resolve(slot+".png");if(!Files.exists(path))continue;
            try{images.put(slot,ParticleEffect.readImage(path.toFile()));}
            catch(IOException ex){System.err.println("Skipping "+path+": "+ex.getMessage());}
        }
    }
    boolean draw(Graphics2D g,Slot slot,int x,int y,int width,int height){
        BufferedImage image=images.get(slot);if(image==null)return false;
        Graphics2D copy=(Graphics2D)g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        copy.drawImage(image,x,y,width,height,null);copy.dispose();return true;
    }
    boolean fill(Graphics2D g,Slot slot,int x,int y,int width,int height,double fraction){
        if(!images.containsKey(slot))return false;
        Graphics2D copy=(Graphics2D)g.create();
        copy.clipRect(x,y,(int)Math.round(width*Math.max(0,Math.min(1,fraction))),height);
        draw(copy,slot,x,y,width,height);copy.dispose();return true;
    }
}
