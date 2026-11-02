import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Layer persistence, editing history, canvas tools, PNG output, and dynamic HUD skins. */
public class UIEditorCheck {
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static void document(Path directory) throws Exception {
        UIDrawing d=new UIDrawing(64,32);d.active().name="Base";d.active().image.setRGB(4,5,Color.RED.getRGB());
        d.addLayer("Overlay");d.active().image.setRGB(4,5,Color.BLUE.getRGB());
        check(d.flatten().getRGB(4,5)==Color.BLUE.getRGB(),"Top layer compositing");
        d.checkpoint();d.active().visible=false;check(d.flatten().getRGB(4,5)==Color.RED.getRGB(),"Hidden layer excluded");
        d.undo();check(d.active().visible&&d.flatten().getRGB(4,5)==Color.BLUE.getRGB(),"Undo restores visibility and pixels");
        d.redo();check(!d.active().visible,"Redo restores visibility edit");
        d.checkpoint();d.active().visible=true;d.active().opacity=.5f;
        int pixel=d.flatten().getRGB(4,5);check((pixel&255)>100&&((pixel>>16)&255)>100,"Layer opacity blends colors");
        d.checkpoint();d.active().x=7;d.active().y=2;
        check((d.flatten().getRGB(11,7)>>>24)>0,"Layer translation");
        Path file=directory.resolve("art.uidraw");d.save(file);UIDrawing loaded=UIDrawing.load(file);
        check(loaded.layers.size()==2&&loaded.selected==1&&loaded.active().x==7&&loaded.active().y==2,"Layer settings round trip");
        check(loaded.active().opacity==.5f&&loaded.active().name.equals("Overlay"),"Name and opacity round trip");
        check(loaded.flatten().getRGB(11,7)==d.flatten().getRGB(11,7),"RGBA round trip");
        d.duplicate();check(d.layers.size()==3,"Duplicate layer");d.active().image.setRGB(4,5,Color.GREEN.getRGB());
        check(d.layers.get(1).image.getRGB(4,5)==Color.BLUE.getRGB(),"Duplicate pixels independent");
        d.undo();check(d.layers.size()==2,"Undo duplicate");d.addLayer("New branch");check(!d.canRedo(),"New edit clears redo");
        d.exportPNG(directory.resolve("CLASS_BADGE.png"));
        check(javax.imageio.ImageIO.read(directory.resolve("CLASS_BADGE.png").toFile()).getRGB(11,7)==d.flatten().getRGB(11,7),"PNG matches visible layers");
        UIDrawing imported=new UIDrawing(32,32);BufferedImage wide=new BufferedImage(80,40,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=wide.createGraphics();g.setColor(Color.CYAN);g.fillRect(0,0,80,40);g.dispose();imported.importImage(wide);
        check((imported.flatten().getRGB(16,0)>>>24)==0&&imported.flatten().getRGB(16,16)==Color.CYAN.getRGB(),"Import fits aspect ratio and preserves transparency");
        imported.undo();check(imported.layers.size()==1,"Undo image import");
        try{UIDrawing.decode(new byte[]{1,2,3});throw new AssertionError("Bad project accepted");}catch(java.io.IOException expected){}
        UISkins skins=new UISkins();skins.load(directory);check(skins.images.containsKey(UISkins.Slot.CLASS_BADGE),"Load HUD assignment");
        BufferedImage fill=new BufferedImage(100,18,BufferedImage.TYPE_INT_ARGB);g=fill.createGraphics();g.setColor(Color.GREEN);g.fillRect(0,0,100,18);g.dispose();
        skins.images.put(UISkins.Slot.HEALTH_FILL,fill);BufferedImage target=new BufferedImage(120,30,BufferedImage.TYPE_INT_ARGB);g=target.createGraphics();
        skins.fill(g,UISkins.Slot.HEALTH_FILL,0,0,100,18,.4);g.dispose();
        check(target.getRGB(39,5)==Color.GREEN.getRGB()&&target.getRGB(40,5)==0,"HUD fill clips to current resource value");
        check(!skins.draw(target.createGraphics(),UISkins.Slot.CROSSHAIR,0,0,24,24),"Missing HUD artwork falls back");
    }
    static JButton button(Container root,String text){return ParticleEditorCheck.components(root,JButton.class).stream().filter(b->text.equals(b.getText())).findFirst().orElseThrow(()->new AssertionError("Missing "+text));}
    static void gesture(JComponent canvas,int x,int y,int ex,int ey,int modifiers){
        Point a=viewPoint(canvas,x,y),b=viewPoint(canvas,ex,ey);x=a.x;y=a.y;ex=b.x;ey=b.y;
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,modifiers,x,y,1,false,MouseEvent.BUTTON1));
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,0,modifiers,ex,ey,0,false,MouseEvent.NOBUTTON));
        canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,0,modifiers,ex,ey,1,false,MouseEvent.BUTTON1));
    }
    static double property(JComponent canvas,String name){try{java.lang.reflect.Method method=canvas.getClass().getDeclaredMethod(name);method.setAccessible(true);return (double)method.invoke(canvas);}
        catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}}
    static Point viewPoint(JComponent canvas,double x,double y){double scale=property(canvas,"scale");return new Point((int)Math.round(property(canvas,"originX")+x*scale),(int)Math.round(property(canvas,"originY")+y*scale));}
    static void wheel(JComponent canvas,int x,int y,int rotation){canvas.dispatchEvent(new MouseWheelEvent(canvas,MouseEvent.MOUSE_WHEEL,0,0,x,y,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,rotation));}
    static void ui() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            ParticleEditor.theme();UIEditor editor=new UIEditor();editor.setSize(1250,820);ParticleEditorCheck.layout(editor);
            JComponent canvas=ParticleEditorCheck.components(editor,JComponent.class).stream().filter(c->c.getClass().getName().equals("UIEditor$Canvas")).findFirst().get();
            double ox=property(canvas,"originX"),oy=property(canvas,"originY");
            check(ox>=0&&oy>=0,"Canvas starts centered in visible workspace");
            canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_PRESSED,0,InputEvent.BUTTON2_DOWN_MASK,100,100,1,false,MouseEvent.BUTTON2));
            canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_DRAGGED,0,InputEvent.BUTTON2_DOWN_MASK,180,145,0,false,MouseEvent.NOBUTTON));
            canvas.dispatchEvent(new MouseEvent(canvas,MouseEvent.MOUSE_RELEASED,0,0,180,145,1,false,MouseEvent.BUTTON2));
            check(Math.abs(property(canvas,"originX")-ox-80)<1e-8&&Math.abs(property(canvas,"originY")-oy-45)<1e-8,"Middle drag pans view by pointer movement");
            check(!editor.drawing.canUndo()&&editor.drawing.active().x==0&&editor.drawing.active().y==0,"Panning does not edit artwork or history");
            double scale=property(canvas,"scale"),ix=(240-property(canvas,"originX"))/scale,iy=(190-property(canvas,"originY"))/scale;
            wheel(canvas,240,190,-1);check(property(canvas,"scale")>scale,"Wheel up zooms in");
            check(Math.abs((240-property(canvas,"originX"))/property(canvas,"scale")-ix)<1e-8&&Math.abs((190-property(canvas,"originY"))/property(canvas,"scale")-iy)<1e-8,"Wheel zoom preserves image point under cursor");
            wheel(canvas,240,190,1);check(property(canvas,"scale")<1.12,"Wheel down zooms out");
            wheel(canvas,240,190,-100);check(property(canvas,"scale")==4,"Maximum zoom bound");
            wheel(canvas,240,190,100);check(property(canvas,"scale")==.25,"Minimum zoom bound");
            button(editor,"Reset view").doClick();check(property(canvas,"originX")>=0&&property(canvas,"originY")>=0,"Reset view fits and centers canvas");
            JComboBox tools=ParticleEditorCheck.components(editor,JComboBox.class).get(0);
            gesture(canvas,20,20,50,20,0);check(editor.drawing.flatten().getRGB(30,20)!=0,"Brush paints selected layer");
            button(editor,"Undo").doClick();check(editor.drawing.flatten().getRGB(30,20)==0,"Undo one stroke");
            button(editor,"Redo").doClick();check(editor.drawing.flatten().getRGB(30,20)!=0,"Redo stroke");
            tools.setSelectedItem(UIEditor.Tool.ERASER);gesture(canvas,30,20,35,20,0);check(editor.drawing.flatten().getRGB(32,20)==0,"Eraser clears alpha");
            button(editor,"Add").doClick();tools.setSelectedItem(UIEditor.Tool.RECTANGLE);gesture(canvas,60,50,100,80,0);
            check(editor.drawing.active().image.getRGB(70,60)!=0,"Rectangle tool");
            tools.setSelectedItem(UIEditor.Tool.ELLIPSE);gesture(canvas,120,50,160,80,0);check(editor.drawing.active().image.getRGB(140,65)!=0,"Ellipse tool");
            tools.setSelectedItem(UIEditor.Tool.LINE);gesture(canvas,170,50,210,50,0);check(editor.drawing.active().image.getRGB(190,50)!=0,"Line tool");
            tools.setSelectedItem(UIEditor.Tool.RECTANGLE);gesture(canvas,230,50,240,75,InputEvent.SHIFT_DOWN_MASK);
            check(editor.drawing.active().image.getRGB(250,60)!=0,"Shift constrains square dimensions");
            tools.setSelectedItem(UIEditor.Tool.MOVE_LAYER);gesture(canvas,100,100,110,105,0);check(editor.drawing.active().x==10&&editor.drawing.active().y==5,"Move layer tool");
            button(editor,"Copy").doClick();check(editor.drawing.layers.size()==3,"Layer copy button");button(editor,"Down").doClick();check(editor.drawing.selected==1,"Layer reorder button");
            button(editor,"Delete").doClick();check(editor.drawing.layers.size()==2,"Layer delete button");
            try{
                CubeMovement game=new CubeMovement();java.lang.reflect.Field field=CubeMovement.class.getDeclaredField("uiSkins");field.setAccessible(true);
                UISkins skins=(UISkins)field.get(game);skins.images.clear();BufferedImage fill=new BufferedImage(100,18,BufferedImage.TYPE_INT_ARGB);
                Graphics2D paint=fill.createGraphics();paint.setColor(Color.GREEN);paint.fillRect(0,0,100,18);paint.dispose();skins.images.put(UISkins.Slot.HEALTH_FILL,fill);
                java.lang.reflect.Method resource=CubeMovement.class.getDeclaredMethod("drawResourceBar",Graphics2D.class,String.class,double.class,double.class,int.class,int.class,int.class,Color.class,String.class);resource.setAccessible(true);
                BufferedImage hud=new BufferedImage(150,60,BufferedImage.TYPE_INT_ARGB);paint=hud.createGraphics();resource.invoke(game,paint,"HEALTH",40.0,100.0,10,20,100,Color.RED,null);paint.dispose();
                check(hud.getRGB(49,25)==Color.GREEN.getRGB()&&hud.getRGB(50,25)!=Color.GREEN.getRGB(),"Game HUD actually uses custom clipped fill");
            }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
            BufferedImage screenshot=new BufferedImage(1250,820,BufferedImage.TYPE_INT_ARGB);Graphics2D g=screenshot.createGraphics();editor.paint(g);g.dispose();
            try{javax.imageio.ImageIO.write(screenshot,"png",Paths.get(System.getProperty("java.io.tmpdir"),"camera-ui-editor.png").toFile());}
            catch(java.io.IOException ex){throw new RuntimeException(ex);}
        });
    }
    public static void main(String[] args){try{
        Path directory=Files.createTempDirectory("ui-editor-check-");try{document(directory);ui();}
        finally{try(java.util.stream.Stream<Path> files=Files.walk(directory)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}
        System.out.println("Passed: layered RGBA persistence, import/export, visibility/opacity, undo/redo, independent copies, painting/shapes/eraser/movement, HUD assignments and live fill clipping.");System.exit(0);
    }catch(Throwable ex){ex.printStackTrace();System.exit(1);}}
}
