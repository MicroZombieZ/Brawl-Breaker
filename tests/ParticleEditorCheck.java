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
        check(loaded.color.equals(e.color),"Edited color round trip");
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
    static void advanced(Path directory) throws Exception {
        ParticleEffect e=ParticleEffect.preset(ParticleEffect.Pattern.CONE);
        e.speedVariation=0;e.sizeVariation=0;e.lifetimeVariation=0;e.drag=2;
        e.windX=3;e.windY=1;e.windZ=-2;e.emissionDuration=.5;e.fadeIn=.2;e.fadeOut=.3;
        e.bounce=.8;e.groundCollision=false;e.originX=1.2;e.originY=-.4;e.originZ=2.3;
        Path path=directory.resolve("advanced.pfx");e.save(path);ParticleEffect loaded=ParticleEffect.load(path);
        check(Arrays.equals(e.extraValues(),loaded.extraValues())&&!loaded.groundCollision,"Advanced settings round trip");
        e.emissionDuration=0;e.gravity=0;e.count=1;e.lifetime=4;e.spread=0;
        ParticleSystem a=new ParticleSystem(),b=new ParticleSystem();a.emit(e,0,10,0,0);b.emit(e,0,10,0,0);
        ParticleSystem.Particle p=a.particles.get(0);
        near(p.size,e.size,"Zero size randomness");near(p.duration,e.lifetime,"Zero lifetime randomness");
        near(Math.sqrt(p.vx*p.vx+p.vy*p.vy+p.vz*p.vz),e.speed,"Zero speed randomness");
        near(ParticleSystem.alpha(p),0,"Fade begins transparent");
        a.update(.4);for(int i=0;i<40;i++)b.update(.01);
        near(p.x,b.particles.get(0).x,"Wind/drag independent of timestep X");
        near(p.y,b.particles.get(0).y,"Wind/drag independent of timestep Y");
        near(p.z,b.particles.get(0).z,"Wind/drag independent of timestep Z");
        near(ParticleSystem.alpha(p),.5,"Fade in follows lifetime fraction");
        p.life=.6;near(ParticleSystem.alpha(p),.5,"Fade out follows lifetime fraction");
        p.y=-1;a.update(.01);check(p.y<0,"Disabled ground collision allows passage");
        e.emissionDuration=2;e.count=100;a.clear();a.emit(e,0,10,0,0);
        check(a.particles.stream().allMatch(v->v.delay>=0&&v.delay<=2)&&a.particles.stream().anyMatch(v->v.delay>1),"Emission duration spreads births");
        e.drag=Double.NaN;reject(e,path);e.drag=2;e.fadeIn=1.1;reject(e,path);
        // Strip optional properties to simulate an effect saved by the original editor.
        e=new ParticleEffect();e.save(path);
        java.util.Properties props=new java.util.Properties();
        try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(path.toFile())){props.load(zip.getInputStream(zip.getEntry("effect.properties")));}
        for(String key:new String[]{"speedVariation","sizeVariation","lifetimeVariation","drag","windX","windY","windZ","emissionDuration","fadeIn","fadeOut","bounce","groundCollision","originX","originY","originZ"})props.remove(key);
        try(java.util.zip.ZipOutputStream out=new java.util.zip.ZipOutputStream(Files.newOutputStream(path))){out.putNextEntry(new java.util.zip.ZipEntry("effect.properties"));props.store(out,"");out.closeEntry();}
        loaded=ParticleEffect.load(path);
        check(Arrays.equals(e.extraValues(),loaded.extraValues())&&loaded.groundCollision,"Legacy files preserve defaults");
    }
    static void meshes(Path directory) throws Exception {
        for(ParticleMesh.Primitive primitive:ParticleMesh.Primitive.values()){
            ParticleMesh m=ParticleMesh.primitive(primitive);check(m.triangles.length<=256,"Primitive within mesh budget: "+primitive);
            java.util.Map<String,Integer> edges=new java.util.HashMap<>();
            for(int[] f:m.triangles){double[] a=m.vertices[f[0]],b=m.vertices[f[1]],c=m.vertices[f[2]];
                double ax=b[0]-a[0],ay=b[1]-a[1],az=b[2]-a[2],bx=c[0]-a[0],by=c[1]-a[1],bz=c[2]-a[2];
                check(Math.pow(ay*bz-az*by,2)+Math.pow(az*bx-ax*bz,2)+Math.pow(ax*by-ay*bx,2)>1e-12,"Nondegenerate primitive triangles: "+primitive);
                for(int i=0;i<3;i++){int u=f[i],v=f[(i+1)%3];String key=Math.min(u,v)+":"+Math.max(u,v);edges.merge(key,1,Integer::sum);}
            }
            check(edges.values().stream().allMatch(n->n==2),"Closed primitive: "+primitive);
            ParticleMesh parsed=ParticleMesh.read(new java.io.ByteArrayInputStream(m.obj().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            check(parsed.obj().equals(m.obj()),"Primitive OBJ round trip: "+primitive);
        }
        ParticleMesh cube=ParticleMesh.cube();
        Path obj=directory.resolve("cube.obj");Files.writeString(obj,cube.obj());
        ParticleMesh loaded=ParticleMesh.read(obj);
        check(loaded.vertices.length==8&&loaded.triangles.length==12,"OBJ geometry round trip");
        ParticleMesh changed=loaded.transform(2,3,4,0,90,0);
        near(changed.vertices[0][0],-2,"Mesh Y rotation");near(changed.vertices[0][1],-1.5,"Mesh axis scale");
        ParticleMesh normalized=changed.normalize();double max=0;for(double[] v:normalized.vertices)for(double n:v)max=Math.max(max,Math.abs(n));near(max,.5,"Imported size normalization");
        String negative="v 0 0 0\nv 1 0 0\nv 0 1 0\nf -3/1/2 -2/2/2 -1/3/2\n";
        loaded=ParticleMesh.read(new java.io.ByteArrayInputStream(negative.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        check(loaded.triangles[0][0]==0,"OBJ relative indices and UV/normal syntax");
        for(String invalid:new String[]{negative.replace("-3/1/2","0"),negative.replace("1 0 0","NaN 0 0"),negative.replace("f -3/1/2 -2/2/2 -1/3/2","f 1 2 3 1")}){
            try{ParticleMesh.read(new java.io.ByteArrayInputStream(invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8)));throw new AssertionError("Invalid OBJ accepted");}catch(java.io.IOException expected){}
        }
        ParticleEffect effect=new ParticleEffect();effect.mesh=changed;Path file=directory.resolve("mesh.pfx");effect.save(file);effect=ParticleEffect.load(file);
        check(effect.mesh.obj().equals(changed.obj()),"Edited mesh embedded in effect archive");
        ParticleSystem system=new ParticleSystem();system.emit(effect,0,2,0,0);
        check(system.particles.stream().allMatch(p->p.mesh!=null&&p.mesh.obj().equals(changed.obj())),"Particle instances retain shape geometry");
        system.quality(ParticleSystem.Quality.HIGH);for(int i=0;i<30;i++)system.emit(effect,0,2,0,0);
        check(system.particles.stream().mapToInt(p->p.mesh.triangles.length).sum()<=8192,"Custom mesh triangle budget");
        final ParticleMesh[] selected={cube};
        SwingUtilities.invokeAndWait(()->{
            ParticleShapeEditor editor=new ParticleShapeEditor(()->selected[0],v->selected[0]=v,()->Color.ORANGE);
            editor.model.setValueAt("0.75",0,1);near(selected[0].vertices[0][0],.75,"Vertex cell edits shape");
            editor.travel(editor.undo,editor.redo);near(selected[0].vertices[0][0],-.5,"Shape undo");
            editor.travel(editor.redo,editor.undo);near(selected[0].vertices[0][0],.75,"Shape redo");
            editor.setSize(1100,700);layout(editor);BufferedImage image=new BufferedImage(1100,700,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();editor.paint(g);g.dispose();
            check(editor.view.renderer.image!=null,"Shape tab uses shared depth renderer");
            editor.edit(cube);editor.view.setSize(650,550);editor.view.frame();
            int vertex=-1,hidden=0;for(int i=0;i<cube.vertices.length;i++){double[] p=editor.view.screen(cube.vertices[i]);Point point=new Point((int)Math.round(p[0]),(int)Math.round(p[1]));
                if(editor.view.pick(point)==i)vertex=i;else hidden++;}
            check(vertex>=0&&hidden>0,"Visible handles pickable; rear vertices occluded");
            double[] pos=editor.view.screen(cube.vertices[vertex]);Point start=new Point((int)Math.round(pos[0]),(int)Math.round(pos[1]));
            double yaw=editor.view.yaw,dist=editor.view.distance;int history=editor.undo.size();
            editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_PRESSED,0,java.awt.event.InputEvent.BUTTON1_DOWN_MASK,start.x,start.y,1,false,java.awt.event.MouseEvent.BUTTON1));
            editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_DRAGGED,0,java.awt.event.InputEvent.BUTTON1_DOWN_MASK,start.x+20,start.y+10,0,false,java.awt.event.MouseEvent.NOBUTTON));
            editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_DRAGGED,0,java.awt.event.InputEvent.BUTTON1_DOWN_MASK,start.x+40,start.y+20,0,false,java.awt.event.MouseEvent.NOBUTTON));
            editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_RELEASED,0,0,start.x+40,start.y+20,1,false,java.awt.event.MouseEvent.BUTTON1));
            check(editor.undo.size()==history+1,"One undo step per vertex drag");near(editor.view.yaw,yaw,"Vertex drag does not orbit");near(editor.view.distance,dist,"Vertex drag does not reframe");
            check(editor.table.getSelectedRow()==vertex,"Viewport selection synchronizes vertex table");
            double[] moved=editor.view.screen(selected[0].vertices[vertex]);near(moved[0],pos[0]+40,"Vertex tracks cursor X");near(moved[1],pos[1]+20,"Vertex tracks cursor Y");
            for(int i=0;i<cube.vertices.length;i++)if(i!=vertex)check(Arrays.equals(cube.vertices[i],selected[0].vertices[i]),"Only grabbed vertex moves");
            editor.travel(editor.undo,editor.redo);check(selected[0].obj().equals(cube.obj()),"Undo restores whole drag");
            editor.view.original=selected[0];editor.view.grabbed=vertex;editor.view.start=start;editor.view.moveVertex(new Point(start.x+10,start.y));editor.view.cancelVertex();check(selected[0].obj().equals(cube.obj()),"Escape restores vertex drag");
            editor.view.distance=.3;g=image.createGraphics();editor.view.paint(g);g.dispose();
            int pixels=0;for(int argb:editor.view.renderer.pixels)if((argb>>>24)!=0)pixels++;check(pixels>0,"Near-plane intersecting mesh remains visible");
            editor.edit(null);check(selected[0]==null,"Remove custom shape");editor.travel(editor.undo,editor.redo);check(selected[0]!=null,"Undo removal");
        });
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
        e.originX=2;e.originY=3;e.originZ=4;e.range=0;e.count=1;e.aggression=1;
        s.quality(ParticleSystem.Quality.HIGH);s.emit(e,10,10,10,Math.PI/2);
        p=s.particles.get(0);near(p.x,14,"Origin rotates with emitter X");near(p.y,13,"Origin vertical offset");near(p.z,8,"Origin rotates with emitter Z");
        s.clear();e.originY=-5;e.groundCollision=false;s.emit(e,0,1,0,0);near(s.particles.get(0).y,-4,"Disabled ground permits negative origin");
        s.clear();e.groundCollision=true;s.emit(e,0,1,0,0);near(s.particles.get(0).y,.03,"Ground collision clamps below-ground origin");s.clear();
        e=ParticleEffect.preset(ParticleEffect.Pattern.SPARKLE);s.quality(ParticleSystem.Quality.HIGH);s.emit(e,0,3,0,0);
        check(s.particles.stream().anyMatch(v->v.delay>0&&v.twinkle>0),"Sparkle emission staggers and twinkles");
    }
    static void ui(Path screenshot,Path shapeDirectory) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            ParticleEditor.theme();
            ParticleEditor editor=new ParticleEditor(shapeDirectory);
            try {
                final Color[] selected={Color.WHITE};
                ParticleColorEditor colors=new ParticleColorEditor(Color.WHITE,c->selected[0]=c);
                colors.hex.setText("#12abEF");check(selected[0].equals(new Color(0x12abef)),"Hex input updates live color");
                colors.hex.setText("not a color");check(selected[0].equals(new Color(0x12abef)),"Invalid hex retains previous color");
                colors.chooser.setColor(new Color(240,80,30));check(colors.hex.getText().equals("#F0501E"),"RGB chooser synchronizes hex");
                check(ParticleColorEditor.parse("#12345")==null&&ParticleColorEditor.parse("#GG0000")==null,"Reject malformed hex colors");
                components(colors,JButton.class).stream().filter(b->"#FFC65B".equals(b.getToolTipText())).findFirst().get().doClick();
                check(selected[0].equals(new Color(255,198,91)),"Palette swatch changes color");
                ParticleColorEditor.remember(new Color(24,96,180));
                ParticleColorEditor reopened=new ParticleColorEditor(selected[0],c->{});
                check(components(reopened,JButton.class).stream().anyMatch(b->"#1860B4".equals(b.getToolTipText())),"Applied colors available in session history");
                colors.setSize(720,480);layout(colors);
                BufferedImage colorImage=new BufferedImage(720,480,BufferedImage.TYPE_INT_ARGB);
                Graphics2D colorGraphics=colorImage.createGraphics();colors.paint(colorGraphics);colorGraphics.dispose();
                try{javax.imageio.ImageIO.write(colorImage,"png",Paths.get(System.getProperty("java.io.tmpdir"),"camera-particle-colors.png").toFile());}
                catch(java.io.IOException ex){throw new RuntimeException(ex);}
                try{
                    Path cubePreset=editor.storeShapePreset("My Cube",ParticleMesh.cube(),false);
                    Path conePreset=editor.storeShapePreset("My Cone",ParticleMesh.primitive(ParticleMesh.Primitive.CONE),false);
                    editor.syncShapeChoices();editor.meshPresets.setSelectedItem("My Cone.obj");
                    java.lang.reflect.Field effectField=ParticleEditor.class.getDeclaredField("effect");effectField.setAccessible(true);
                    ParticleEffect active=(ParticleEffect)effectField.get(editor);
                    check(active.mesh.obj().equals(ParticleMesh.read(conePreset).obj()),"Main particle tab selects saved custom geometry");
                    double speed=active.speed;editor.useShapePreset(cubePreset);near(active.speed,speed,"Shape preset preserves emission parameters");
                    ParticleMesh before=active.mesh;Path bad=shapeDirectory.resolve("broken.obj");Files.writeString(bad,"invalid");
                    try{editor.useShapePreset(bad);throw new AssertionError("Bad preset accepted");}catch(java.io.IOException expected){}
                    check(active.mesh==before,"Bad preset leaves active geometry intact");
                    try{editor.storeShapePreset("../escape",before,false);throw new AssertionError("Unsafe name accepted");}catch(java.io.IOException expected){}
                    try{editor.storeShapePreset("My Cube",before,false);throw new AssertionError("Preset silently overwritten");}catch(java.nio.file.FileAlreadyExistsException expected){}
                    ParticleEditor reopenedEditor=new ParticleEditor(shapeDirectory);try{check(reopenedEditor.meshPresets.getItemCount()==4,"Preset library persists across editor sessions");}finally{reopenedEditor.stop();}
                    java.lang.reflect.Field shapesField=ParticleEditor.class.getDeclaredField("shapes");shapesField.setAccessible(true);JComboBox shapes=(JComboBox)shapesField.get(editor);
                    check("Custom mesh".equals(shapes.getSelectedItem()),"Main shape selector reflects custom mesh");shapes.setSelectedItem(ParticleSystem.Shape.CUBE);check(active.mesh==null,"Built-in selection disables custom mesh");
                    editor.useShapePreset(conePreset);Path saved=shapeDirectory.resolve("selected.pfx");active.save(saved);check(ParticleEffect.load(saved).mesh.obj().equals(active.mesh.obj()),"Selected preset embedded in effect");
                }catch(Exception ex){throw new RuntimeException(ex);}
                editor.setSize(1220,820);layout(editor);
                check(components(editor,JTabbedPane.class).stream().anyMatch(t->t.getTabCount()==2&&t.getTitleAt(1).equals("Custom shape")),"Shape workspace is a secondary particle editor tab");
                check(components(editor,JSlider.class).size()==28,"All twenty-eight parameter sliders exist");
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
                JCheckBox dummy=components(editor,JCheckBox.class).stream().filter(c->c.getText().equals("Reference dummy")).findFirst().get();
                check(dummy.isSelected(),"Reference dummy defaults visible");dummy.doClick();
                BufferedImage hidden=new BufferedImage(1220,820,BufferedImage.TYPE_INT_ARGB);Graphics2D hg=hidden.createGraphics();editor.paint(hg);hg.dispose();
                int difference=0;for(int yy=120;yy<650;yy++)for(int xx=100;xx<700;xx++)if(image.getRGB(xx,yy)!=hidden.getRGB(xx,yy))difference++;
                check(difference>20,"Dummy toggle changes reference viewport");dummy.doClick();
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
                    particles.clear();sprite.mesh=ParticleMesh.cube();particles.emit(sprite,0,2,0,0);
                    graphics=after.createGraphics();draw.invoke(game,graphics);graphics.dispose();
                    changed=0;for(int y=0;y<700;y++)for(int x=0;x<1000;x++)if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;
                    check(changed>50,"Custom mesh renders in actual game world with sprite also assigned");
                    java.lang.reflect.Field effectField=ParticleEditor.class.getDeclaredField("effect");effectField.setAccessible(true);effectField.set(editor,sprite);
                    java.lang.reflect.Method restart=ParticleEditor.class.getDeclaredMethod("restart");restart.setAccessible(true);restart.invoke(editor);
                    Graphics2D previewGraphics=image.createGraphics();editor.paint(previewGraphics);previewGraphics.dispose();
                    java.lang.reflect.Field previewField=ParticleEditor.class.getDeclaredField("preview");previewField.setAccessible(true);Object preview=previewField.get(editor);
                    java.lang.reflect.Field rendererField=preview.getClass().getDeclaredField("meshRenderer");rendererField.setAccessible(true);
                    check(((SceneDepthRenderer)rendererField.get(preview)).image!=null,"Animated custom particles use shared depth renderer");
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
        try{files(directory);advanced(directory);meshes(directory);physics();ui(Paths.get(System.getProperty("java.io.tmpdir"),"camera-particle-editor.png"),directory.resolve("shapes"));}
        finally{try(java.util.stream.Stream<Path> files=Files.walk(directory)){
            for(Path path:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(path);}}
        System.out.println("Passed: portable settings/image round trip, transparency, validation, event assignments, presets, direction, physics, limits, sliders, live color/hex/palette editing and editor rendering.");
    }
}
