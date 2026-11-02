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
    static void layoutAndRender(Path dir) throws Exception {
        HUDLayout layout=onlyHealth();HUDLayout.Placement p=layout.get(HUDLayout.Piece.HEALTH);p.positioned=true;p.x=120;p.y=170;p.scaleX=1.4;p.scaleY=2;p.rotation=37;p.opacity=.65;p.fontScale=1.2;p.text=false;p.defaults=false;p.color=Color.CYAN;
        Path path=dir.resolve("hud-layout.properties");layout.save(path);HUDLayout loaded=HUDLayout.load(path);HUDLayout.Placement q=loaded.get(HUDLayout.Piece.HEALTH);
        check(q.x==120&&q.y==170&&q.scaleX==1.4&&q.scaleY==2&&q.rotation==37&&q.opacity==.65&&q.fontScale==1.2&&!q.text&&!q.defaults&&q.color.equals(Color.CYAN),"Placement/style round trip");
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
        for(UISkins.Slot slot:UISkins.Slot.values())check(slot.piece()!=null,"Every artwork slot links to a HUD use");
    }
    static double method(JComponent preview,String name){try{java.lang.reflect.Method m=preview.getClass().getDeclaredMethod(name);m.setAccessible(true);return (double)m.invoke(preview);}catch(Exception ex){throw new RuntimeException(ex);}}
    static Point point(JComponent preview,double x,double y){double scale=method(preview,"scale");return new Point((int)Math.round(method(preview,"ox")+x*scale),(int)Math.round(method(preview,"oy")+y*scale));}
    static void ui() throws Exception {SwingUtilities.invokeAndWait(()->{
        ParticleEditor.theme();HUDEditor editor=new HUDEditor();editor.setSize(1300,860);ParticleEditorCheck.layout(editor);
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
        JComboBox pieces=ParticleEditorCheck.components(editor,JComboBox.class).get(0);pieces.setSelectedItem(HUDLayout.Piece.STAMINA);
        JComboBox slots=ParticleEditorCheck.components(editor,JComboBox.class).get(1);for(int i=0;i<slots.getItemCount();i++)check(((UISkins.Slot)slots.getItemAt(i)).piece()==HUDLayout.Piece.STAMINA,"Artwork upload stays linked to selected piece");
        BufferedImage screenshot=new BufferedImage(1300,860,BufferedImage.TYPE_INT_ARGB);Graphics2D g=screenshot.createGraphics();editor.paint(g);g.dispose();
        try{javax.imageio.ImageIO.write(screenshot,"png",Paths.get(System.getProperty("java.io.tmpdir"),"camera-hud-editor.png").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
    });}
    public static void main(String[] args){try{Path dir=Files.createTempDirectory("hud-check-");try{layoutAndRender(dir);ui();}finally{try(java.util.stream.Stream<Path> files=Files.walk(dir)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
        System.out.println("Passed: HUD placement/style persistence, invalid values, resolution anchoring, transparent custom bars, transformed live fill clipping, visibility, drag/scale/rotate, undo/redo, slot linkage and editor rendering.");System.exit(0);
    }catch(Throwable ex){ex.printStackTrace();System.exit(1);}}
}
