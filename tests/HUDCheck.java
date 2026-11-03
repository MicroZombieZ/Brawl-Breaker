import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** HUD layout persistence, transformed dynamic rendering, and placement editor checks. */
public class HUDCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static BufferedImage solid(Color color,int w,int h){BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();g.setColor(color);g.fillRect(0,0,w,h);g.dispose();return image;}
    static BufferedImage render(HUDRenderer renderer,HUDRenderer.Data data){BufferedImage image=new BufferedImage(1000,700,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();renderer.draw(g,1000,700,data);g.dispose();return image;}
    static HUDLayout onlyHealth(){HUDLayout layout=new HUDLayout();for(HUDLayout.Placement p:layout.placements.values())p.visible=false;layout.get(HUDLayout.Piece.HEALTH).visible=true;return layout;}
    static void ghost(){
        HealthFillGhost trail=new HealthFillGhost();trail.reset(100,100);trail.update(0,50,100);
        trail.update(.3,50,100);check(trail.fraction()==1,"Damage ghost holds previous HP for 0.3 seconds");
        trail.update(.00001,50,100);double initialSpeed=(1-trail.fraction())/.5/.00001;
        double oldStartSpeed=3/(.45*Math.expm1(3));
        check(Math.abs(initialSpeed-oldStartSpeed/3)<.00001,"Starting speed is one third of the previous speed");
        trail.update(.05,50,100);double first=trail.fraction();
        trail.update(.05,50,100);double second=trail.fraction();
        check(first<1&&second<first&&(first-second)>(1-first),"Ghost accelerates over time");
        trail.reset(100,100);trail.update(0,50,100);trail.update(.3,50,100);
        double previousStep=0;
        for(int i=0;i<6;i++){
            double before=trail.fraction();trail.update(.5,50,100);double step=before-trail.fraction();
            check(step>previousStep*1.2,"Speed visibly grows through every half-second of the three-second ramp");
            previousStep=step;
        }
        double capped=trail.fraction();
        trail.update(.1,50,100);double capStep=capped-trail.fraction();
        check(Math.abs(capStep/.5/.1-oldStartSpeed/3*3.2)<1e-9,"Speed caps at 3.2 times starting speed");
        capped=trail.fraction();trail.update(.1,50,100);
        check(Math.abs(capped-trail.fraction()-capStep)<1e-9,"Speed stays capped after acceleration");
        trail.update(3,50,100);check(trail.fraction()==.5,"Ghost reaches current HP without overshoot");
        trail.reset(100,100);trail.update(0,50,100);trail.update(.5,50,100);double interrupted=trail.fraction();
        trail.update(0,25,100);trail.update(0,25,100);check(trail.fraction()==interrupted,"More damage preserves current ghost and paused time does not advance");
        trail.update(.3,25,100);check(trail.fraction()==interrupted,"Further damage restarts full hold");
        trail.update(4,25,100);check(trail.fraction()==.25,"Ghost reaches current HP exactly");
        trail.update(0,10,100);trail.update(0,60,100);check(trail.fraction()==.6,"Healing clears damage ghost");
        trail.update(0,60,200);check(trail.fraction()==.3,"Class/max HP change resets ghost");
        trail.reset(0,100);trail.update(0,100,100);check(trail.fraction()==1,"Respawn starts clean");
        HealthFillGhost fine=new HealthFillGhost(),coarse=new HealthFillGhost();fine.reset(100,100);coarse.reset(100,100);fine.update(0,20,100);coarse.update(0,20,100);
        for(int i=0;i<55;i++)fine.update(.01,20,100);coarse.update(.55,20,100);check(Math.abs(fine.fraction()-coarse.fraction())<1e-8,"Ghost timing independent of update frequency during catch-up");
        HUDLayout layout=onlyHealth();HUDLayout.Placement p=layout.get(HUDLayout.Piece.HEALTH);p.text=false;p.defaults=false;p.positioned=true;p.x=100;p.y=200;p.rotation=20;
        HUDLayout.Placement child=layout.get(UISkins.Slot.HEALTH_FILL);child.x=10;child.scaleX=.8;child.rotation=15;
        UISkins skins=new UISkins();BufferedImage texture=solid(Color.GREEN,250,18);texture.setRGB(190,9,0);skins.images.put(UISkins.Slot.HEALTH_FILL,texture);
        HUDRenderer.Data data=new HUDRenderer.Data();data.maxHealth=100;data.health=50;data.healthGhost=1;
        BufferedImage image=render(new HUDRenderer(skins,layout),data);AffineTransform transform=layout.transform(UISkins.Slot.HEALTH_FILL,1000,700);
        Point2D live=transform.transform(new Point2D.Double(50,9),null),ghost=transform.transform(new Point2D.Double(170,9),null);
        check((image.getRGB((int)live.getX(),(int)live.getY())>>>24)==255,"Live fill keeps full opacity");
        check(Math.abs((image.getRGB((int)ghost.getX(),(int)ghost.getY())>>>24)-102)<=1,"Ghost uses custom fill and both HUD transforms at 40% opacity");
        p.rotation=0;child.rotation=0;child.scaleX=1;image=render(new HUDRenderer(skins,layout),data);
        check(image.getRGB(300,209)==0,"Transparent areas in custom fill remain transparent in ghost");
        child.visible=false;image=render(new HUDRenderer(skins,layout),data);check(image.getRGB(280,209)==0,"Hiding health fill hides its ghost");
        HUDLayout stamina=new HUDLayout();for(HUDLayout.Placement place:stamina.placements.values())place.visible=false;
        stamina.get(HUDLayout.Piece.STAMINA).visible=true;data.stamina=50;
        BufferedImage with=render(new HUDRenderer(skins,stamina),data);data.healthGhost=Double.NaN;BufferedImage without=render(new HUDRenderer(skins,stamina),data);
        check(Arrays.equals(with.getRGB(0,0,1000,700,null,0,1000),without.getRGB(0,0,1000,700,null,0,1000)),"Stamina has no ghost effect");
    }
    static void layoutAndRender(Path dir) throws Exception {
        HUDLayout layout=onlyHealth();HUDLayout.Placement p=layout.get(HUDLayout.Piece.HEALTH);p.positioned=true;p.x=120;p.y=170;p.scaleX=1.4;p.scaleY=2;p.rotation=37;p.opacity=.65;p.fontScale=1.2;p.text=false;p.defaults=false;p.color=Color.CYAN;
        Path path=dir.resolve("hud-layout.properties");layout.save(path);HUDLayout loaded=HUDLayout.load(path);HUDLayout.Placement q=loaded.get(HUDLayout.Piece.HEALTH);
        check(q.x==120&&q.y==170&&q.scaleX==1.4&&q.scaleY==2&&q.rotation==37&&q.opacity==.65&&q.fontScale==1.2&&!q.text&&!q.defaults&&q.color.equals(Color.CYAN),"Placement/style round trip");
        HUDLayout.Placement child=loaded.get(UISkins.Slot.HEALTH_FILL);child.x=12;child.y=-4;child.scaleX=.7;child.rotation=18;child.opacity=.4;child.visible=false;
        HUDLayout roundTrip=HUDLayout.decode(loaded.encode());HUDLayout.Placement saved=roundTrip.get(UISkins.Slot.HEALTH_FILL);
        check(saved.x==12&&saved.y==-4&&saved.scaleX==.7&&saved.rotation==18&&saved.opacity==.4&&!saved.visible,"Independent layer settings round trip");
        Properties legacy=new Properties();legacy.load(new java.io.ByteArrayInputStream(loaded.encode()));legacy.setProperty("version","1");java.io.ByteArrayOutputStream legacyBytes=new java.io.ByteArrayOutputStream();legacy.store(legacyBytes,"");
        HUDLayout old=HUDLayout.decode(legacyBytes.toByteArray());check(old.get(HUDLayout.Piece.HEALTH).x==120&&old.get(UISkins.Slot.HEALTH_FILL).x==0&&old.get(UISkins.Slot.HEALTH_FILL).visible,"Version 1 layouts keep whole-piece placement with identity layers");
        Point2D reference=loaded.transform(HUDLayout.Piece.HEALTH,1000,700).transform(new Point2D.Double(125,9),null);
        Point2D resized=loaded.transform(HUDLayout.Piece.HEALTH,2000,1400).transform(new Point2D.Double(125,9),null);
        check(Math.abs(resized.getX()-reference.getX()-120)<1e-7&&Math.abs(resized.getY()-reference.getY()-170)<1e-7,"Custom positions adapt to resolution");
        Properties properties=new Properties();properties.load(new java.io.ByteArrayInputStream(layout.encode()));properties.setProperty("HEALTH.scaleX","NaN");
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();properties.store(out,"");
        try{HUDLayout.decode(out.toByteArray());throw new AssertionError("Non-finite scale accepted");}catch(java.io.IOException expected){}
        UISkins skins=new UISkins();HUDRenderer.Data data=new HUDRenderer.Data();data.health=0;
        HUDLayout original=onlyHealth();original.get(HUDLayout.Piece.HEALTH).text=false;
        skins.images.put(UISkins.Slot.HEALTH_FRAME,new BufferedImage(250,18,BufferedImage.TYPE_INT_ARGB));
        BufferedImage image=render(new HUDRenderer(skins,original),data);
        check(image.getRGB(100,605)==0,"No old translucent background behind custom transparent frame");
        skins.images.clear();image=render(new HUDRenderer(skins,original),data);check((image.getRGB(100,605)>>>24)>0,"Built-in HUD remains when no custom art assigned");
        skins.images.put(UISkins.Slot.HEALTH_FILL,solid(Color.GREEN,250,18));original.get(HUDLayout.Piece.HEALTH).defaults=false;data.health=50;data.maxHealth=100;
        image=render(new HUDRenderer(skins,original),data);check(image.getRGB(100,605)==Color.GREEN.getRGB()&&image.getRGB(180,605)==0,"Live fill clips and empty area stays transparent");
        original.get(HUDLayout.Piece.HEALTH).visible=false;image=render(new HUDRenderer(skins,original),data);check(image.getRGB(100,605)==0,"Hidden HUD piece does not draw");
        p.opacity=1;image=render(new HUDRenderer(skins,layout),data);
        AffineTransform transform=layout.transform(HUDLayout.Piece.HEALTH,1000,700);Point2D inside=transform.transform(new Point2D.Double(50,9),null),outside=transform.transform(new Point2D.Double(200,9),null);
        check((image.getRGB((int)inside.getX(),(int)inside.getY())>>>24)>0&&image.getRGB((int)outside.getX(),(int)outside.getY())==0,"Clipping rotates and scales with the health piece");
        HUDLayout separate=onlyHealth();HUDLayout.Placement group=separate.get(HUDLayout.Piece.HEALTH);group.text=false;group.defaults=false;group.positioned=true;group.x=100;group.y=200;
        BufferedImage frame=new BufferedImage(250,18,BufferedImage.TYPE_INT_ARGB);Graphics2D frameGraphics=frame.createGraphics();frameGraphics.setColor(Color.BLUE);frameGraphics.drawRect(0,0,249,17);frameGraphics.dispose();
        skins.images.put(UISkins.Slot.HEALTH_FRAME,frame);separate.get(UISkins.Slot.HEALTH_FRAME).y=40;
        image=render(new HUDRenderer(skins,separate),data);check(image.getRGB(110,205)==Color.GREEN.getRGB()&&image.getRGB(110,240)==Color.BLUE.getRGB(),"Moving frame does not move fill");
        separate.get(UISkins.Slot.HEALTH_FRAME).visible=false;
        HUDLayout.Placement fill=separate.get(UISkins.Slot.HEALTH_FILL);fill.x=17;fill.y=10;fill.scaleX=.6;fill.scaleY=.8;fill.rotation=15;fill.opacity=.5;
        group.scaleX=1.4;group.scaleY=1.2;group.rotation=20;group.opacity=.5;
        image=render(new HUDRenderer(skins,separate),data);AffineTransform combined=separate.transform(UISkins.Slot.HEALTH_FILL,1000,700);
        inside=combined.transform(new Point2D.Double(50,9),null);outside=combined.transform(new Point2D.Double(200,9),null);
        check(Math.abs((image.getRGB((int)inside.getX(),(int)inside.getY())>>>24)-64)<=1&&image.getRGB((int)outside.getX(),(int)outside.getY())==0,"Independent fill clipping and opacity compose with parent transform");
        for(UISkins.Slot slot:UISkins.Slot.values())check(slot.piece()!=null,"Every artwork slot links to a HUD use");
    }
    static double method(JComponent preview,String name){try{java.lang.reflect.Method m=preview.getClass().getDeclaredMethod(name);m.setAccessible(true);return (double)m.invoke(preview);}catch(Exception ex){throw new RuntimeException(ex);}}
    static Point point(JComponent preview,double x,double y){double scale=method(preview,"scale");return new Point((int)Math.round(method(preview,"ox")+x*scale),(int)Math.round(method(preview,"oy")+y*scale));}
    static void ui() throws Exception {SwingUtilities.invokeAndWait(()->{
        ParticleEditor.theme();HUDEditor editor=new HUDEditor(false);editor.setSize(1300,860);ParticleEditorCheck.layout(editor);
        JComponent preview=ParticleEditorCheck.components(editor,JComponent.class).stream().filter(c->c.getClass().getName().equals("HUDEditor$Preview")).findFirst().get();
        Point a=point(preview,100,605),b=point(preview,150,565);
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_PRESSED,0,0,a.x,a.y,1,false,MouseEvent.BUTTON1));
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON1_DOWN_MASK,b.x,b.y,0,false,MouseEvent.NOBUTTON));
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_RELEASED,0,0,b.x,b.y,1,false,MouseEvent.BUTTON1));
        HUDLayout.Placement p=editor.layout.get(HUDLayout.Piece.HEALTH);check(p.positioned&&Math.abs(p.x-72)<2&&Math.abs(p.y-558)<2,"Drag moves linked health bar");
        UIEditorCheck.button(editor,"Undo").doClick();check(!editor.layout.get(HUDLayout.Piece.HEALTH).positioned,"Undo HUD drag");
        UIEditorCheck.button(editor,"Redo").doClick();check(editor.layout.get(HUDLayout.Piece.HEALTH).positioned,"Redo HUD drag");
        java.util.List<JSpinner> spinners=ParticleEditorCheck.components(editor,JSpinner.class);spinners.get(2).setValue(1.5);
        check(editor.layout.get(HUDLayout.Piece.HEALTH).scaleX==1.5&&editor.layout.get(HUDLayout.Piece.HEALTH).scaleY==1.5,"Linked scale controls");
        spinners.get(4).setValue(25.0);check(editor.layout.get(HUDLayout.Piece.HEALTH).rotation==25,"HUD rotation control");
        java.util.List<JComboBox> combos=ParticleEditorCheck.components(editor,JComboBox.class);JComboBox scope=combos.get(2),layers=combos.get(1);
        scope.setSelectedItem(HUDEditor.Scope.SELECTED_LAYER);spinners.get(0).setValue(18.0);spinners.get(2).setValue(.8);spinners.get(4).setValue(-15.0);
        check(editor.layout.get(UISkins.Slot.HEALTH_FRAME).x==18&&editor.layout.get(UISkins.Slot.HEALTH_FRAME).scaleX==.8&&editor.layout.get(UISkins.Slot.HEALTH_FRAME).rotation==-15&&editor.layout.get(HUDLayout.Piece.HEALTH).scaleX==1.5&&editor.layout.get(HUDLayout.Piece.HEALTH).rotation==25,"Layer inspector leaves parent transform unchanged");
        layers.setSelectedItem(UISkins.Slot.HEALTH_FILL);spinners.get(0).setValue(8.0);
        Point2D world=editor.layout.transform(UISkins.Slot.HEALTH_FILL,1000,700).transform(new Point2D.Double(70,8),null);
        Point2D delta=editor.layout.transform(HUDLayout.Piece.HEALTH,1000,700).deltaTransform(new Point2D.Double(20,12),null);
        a=point(preview,world.getX(),world.getY());b=point(preview,world.getX()+delta.getX(),world.getY()+delta.getY());
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_PRESSED,0,0,a.x,a.y,1,false,MouseEvent.BUTTON1));
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON1_DOWN_MASK,b.x,b.y,0,false,MouseEvent.NOBUTTON));
        preview.dispatchEvent(new MouseEvent(preview,MouseEvent.MOUSE_RELEASED,0,0,b.x,b.y,1,false,MouseEvent.BUTTON1));
        check(Math.abs(editor.layout.get(UISkins.Slot.HEALTH_FILL).x-28)<2&&Math.abs(editor.layout.get(UISkins.Slot.HEALTH_FILL).y-12)<2&&editor.layout.get(UISkins.Slot.HEALTH_FRAME).x==18,"Layer drag follows inverse parent rotation/scale and leaves frame alone");
        UIEditorCheck.button(editor,"Undo").doClick();check(editor.layout.get(UISkins.Slot.HEALTH_FILL).x==8,"Undo layer drag");UIEditorCheck.button(editor,"Redo").doClick();
        double childX=editor.layout.get(UISkins.Slot.HEALTH_FILL).x;scope.setSelectedItem(HUDEditor.Scope.WHOLE_PIECE);spinners.get(0).setValue(100.0);
        check(editor.layout.get(HUDLayout.Piece.HEALTH).x==100&&editor.layout.get(UISkins.Slot.HEALTH_FILL).x==childX,"Whole-piece edits preserve layer offsets");
        JComboBox pieces=ParticleEditorCheck.components(editor,JComboBox.class).get(0);pieces.setSelectedItem(HUDLayout.Piece.STAMINA);
        JComboBox slots=ParticleEditorCheck.components(editor,JComboBox.class).get(1);for(int i=0;i<slots.getItemCount();i++)check(((UISkins.Slot)slots.getItemAt(i)).piece()==HUDLayout.Piece.STAMINA,"Artwork upload stays linked to selected piece");
        slots.setSelectedItem(UISkins.Slot.STAMINA_FILL);check(scope.getSelectedItem()==HUDEditor.Scope.SELECTED_LAYER,"Choosing artwork switches to individual editing");
        spinners.get(0).setValue(15.0);UIEditorCheck.button(editor,"Reset selection").doClick();
        check(editor.layout.get(UISkins.Slot.STAMINA_FILL).x==0&&editor.layout.get(HUDLayout.Piece.STAMINA).x==22,"Reset selection affects only chosen layer");
        BufferedImage screenshot=new BufferedImage(1300,860,BufferedImage.TYPE_INT_ARGB);Graphics2D g=screenshot.createGraphics();editor.paint(g);g.dispose();
        try{javax.imageio.ImageIO.write(screenshot,"png",Paths.get(System.getProperty("java.io.tmpdir"),"camera-hud-editor.png").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
    });}
    public static void main(String[] args){try{Path dir=Files.createTempDirectory("hud-check-");try{ghost();layoutAndRender(dir);ui();}finally{try(java.util.stream.Stream<Path> files=Files.walk(dir)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
        System.out.println("Passed: HUD/layer persistence and legacy layouts, transforms, opacity, independent frame/fill edits, live clipping, whole/layer selection, parent-aware dragging, undo/redo, slot linkage and editor rendering.");System.exit(0);
    }catch(Throwable ex){ex.printStackTrace();System.exit(1);}}
}
