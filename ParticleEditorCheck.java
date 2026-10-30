import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;

/** Portable assets, custom emission, game assignment, and editor rendering regression checks. */
public class ParticleEditorCheck {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void near(double actual,double expected,String message){check(Math.abs(actual-expected)<1e-8,message);}
    static void reject(ParticleEffect e,Path path) throws Exception {
        try{e.save(path);throw new AssertionError("Invalid settings accepted");}catch(java.io.IOException expected){}
    }
    static void files(Path directory) throws Exception {
        ParticleEffect e=ParticleEffect.preset(ParticleEffect.Pattern.CONE);
        e.heading=73;e.opacity=.37;e.image=new BufferedImage(12,6,BufferedImage.TYPE_INT_ARGB);
        e.image.setRGB(3,2,0x7f12abcd);e.image.setRGB(0,0,0);Path file=directory.resolve("HIT.pfx");e.save(file);
        ParticleEffect loaded=ParticleEffect.load(file);
        check(loaded.pattern==e.pattern&&loaded.shape==e.shape,"Preset and shape round trip");
        near(loaded.heading,73,"Direction round trip");near(loaded.opacity,.37,"Opacity round trip");
        check(loaded.image.getWidth()==12&&loaded.image.getHeight()==6,"Image aspect ratio round trip");
        check(loaded.image.getRGB(3,2)==0x7f12abcd&&loaded.image.getRGB(0,0)==0,"Embedded image retains colors and transparency");
        ParticleSystem system=new ParticleSystem();system.loadEffects(directory);
        check(system.customEffects.size()==1,"Load named event assignment");
        system.emit(new GameEffect(GameEffect.Kind.HIT,2,4,3,.7,99,0));
        check(system.particles.stream().allMatch(p->p.image!=null&&p.opacity==.37),"Game event uses custom image and opacity");
        system.customEffects.clear();system.clear();system.emit(new GameEffect(GameEffect.Kind.HIT,0,1,0,0,1,0));
        check(system.particles.stream().allMatch(p->p.image==null),"Default effect remains available");
        e.speed=Double.NaN;reject(e,directory.resolve("invalid.pfx"));e.speed=3;e.count=401;reject(e,directory.resolve("invalid.pfx"));
        Path invalid=directory.resolve("bad.pfx");Files.write(invalid,new byte[]{1,2,3});
        try{ParticleEffect.load(invalid);throw new AssertionError("Bad file accepted");}catch(java.io.IOException expected){}
        Files.write(directory.resolve("LAND.pfx"),new byte[]{0});system.loadEffects(directory);
        check(system.customEffects.containsKey(GameEffect.Kind.HIT)&&!system.customEffects.containsKey(GameEffect.Kind.LAND),"Bad assignment does not disable valid events");
    }
    static void physics() {
        for(ParticleEffect.Pattern pattern:ParticleEffect.Pattern.values()) {
            ParticleEffect e=ParticleEffect.preset(pattern);ParticleSystem s=new ParticleSystem();s.quality(ParticleSystem.Quality.HIGH);
            s.emit(e,2,4,3,0);check(s.particles.size()==e.count,"Preset count "+pattern);
            check(s.particles.stream().allMatch(p->Math.hypot(p.x-2,p.z-3)<=e.range+1e-8),"Spawn range "+pattern);
            for(int i=0;i<100;i++)s.update(.1);check(s.particles.isEmpty(),"Preset expires "+pattern);
        }
        ParticleEffect e=ParticleEffect.preset(ParticleEffect.Pattern.CONE);e.spread=0;e.elevation=0;e.heading=90;e.range=0;
        ParticleSystem s=new ParticleSystem();s.quality(ParticleSystem.Quality.HIGH);s.emit(e,0,3,0,0);
        ParticleSystem.Particle p=s.particles.get(0);check(p.vx<0&&Math.abs(p.vz)<1e-8&&Math.abs(p.vy)<1e-8,"Heading steers a zero-spread cone");
        double x=p.x;s.update(.1);check(p.x<x,"Custom particles use shared physics");
        e.aggression=3;for(int i=0;i<100;i++)s.emit(e,0,3,0,0);check(s.particles.size()==800,"Aggressive emission respects game cap");
        s.quality(ParticleSystem.Quality.OFF);s.emit(e,0,3,0,0);check(s.particles.isEmpty(),"Off disables custom emission");
        e=ParticleEffect.preset(ParticleEffect.Pattern.SPARKLE);s.quality(ParticleSystem.Quality.HIGH);s.emit(e,0,3,0,0);
        check(s.particles.stream().anyMatch(v->v.delay>0&&v.twinkle>0),"Sparkle emission staggers and twinkles");
    }
    static void ui(Path screenshot) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            ParticleEditor.theme();
            ParticleEditor editor=new ParticleEditor();
            try {
                editor.setSize(1220,820);layout(editor);
                check(components(editor,JSlider.class).size()==14,"All fourteen parameter sliders exist");
                java.util.List<JComboBox> combos=components(editor,JComboBox.class);
                for(ParticleEffect.Pattern pattern:ParticleEffect.Pattern.values())combos.get(0).setSelectedItem(pattern);
                for(JSlider slider:components(editor,JSlider.class)) {slider.setValue(slider.getMinimum());slider.setValue(slider.getMaximum());}
                combos.get(0).setSelectedItem(ParticleEffect.Pattern.SPARKLE);
                try {
                    java.lang.reflect.Field field=ParticleEditor.class.getDeclaredField("system");field.setAccessible(true);
                    ((ParticleSystem)field.get(editor)).update(.2);
                } catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
                BufferedImage image=new BufferedImage(1220,820,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();editor.paint(g);g.dispose();
                check(image.getRGB(400,400)!=0,"Editor preview paints");
                try{javax.imageio.ImageIO.write(image,"png",screenshot.toFile());}catch(java.io.IOException ex){throw new RuntimeException(ex);}
                // Exercise uploaded sprites in the actual game's depth-sorted rendering path.
                try {
                    CubeMovement game=new CubeMovement();game.setSize(1000,700);
                    java.lang.reflect.Field field=CubeMovement.class.getDeclaredField("particles");field.setAccessible(true);
                    ParticleSystem particles=(ParticleSystem)field.get(game);particles.clear();
                    java.lang.reflect.Method draw=CubeMovement.class.getDeclaredMethod("drawCombatWorld",Graphics2D.class);draw.setAccessible(true);
                    BufferedImage before=new BufferedImage(1000,700,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics=before.createGraphics();draw.invoke(game,graphics);graphics.dispose();
                    ParticleEffect sprite=ParticleEffect.preset(ParticleEffect.Pattern.CONE);sprite.count=1;sprite.range=0;sprite.size=1;
                    sprite.image=new BufferedImage(32,16,BufferedImage.TYPE_INT_ARGB);
                    Graphics2D texture=sprite.image.createGraphics();texture.setColor(Color.MAGENTA);texture.fillRect(0,0,32,16);texture.dispose();
                    particles.emit(sprite,0,2,0,0);
                    BufferedImage after=new BufferedImage(1000,700,BufferedImage.TYPE_INT_ARGB);
                    graphics=after.createGraphics();draw.invoke(game,graphics);graphics.dispose();
                    int changed=0;for(int y=0;y<700;y++)for(int x=0;x<1000;x++)if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;
                    check(changed>100,"Uploaded sprite renders in actual game world");
                }catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}
            } finally {editor.stop();}
        });
    }
    static void layout(Container c){c.doLayout();for(Component child:c.getComponents())if(child instanceof Container)layout((Container)child);}
    static <T> java.util.List<T> components(Container c,Class<T> type){
        java.util.List<T> result=new ArrayList<>();for(Component child:c.getComponents()){
            if(type.isInstance(child))result.add(type.cast(child));if(child instanceof Container)result.addAll(components((Container)child,type));}return result;
    }
    public static void main(String[] args) throws Exception {
        try { run();System.exit(0); }
        catch(Throwable ex){ex.printStackTrace();System.exit(1);}
    }
    private static void run() throws Exception {
        Path directory=Files.createTempDirectory("particle-editor-check-");
        try{files(directory);physics();ui(Paths.get(System.getProperty("java.io.tmpdir"),"camera-particle-editor.png"));}
        finally{try(java.util.stream.Stream<Path> files=Files.walk(directory)){
            for(Path path:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
        System.out.println("Passed: portable settings/image round trip, transparency, validation, event assignments, presets, direction, physics, limits, sliders and editor rendering.");
    }
}
