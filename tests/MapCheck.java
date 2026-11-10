import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Authored map persistence, physics, random pool, LAN transfer, and editor smoke checks. */
public class MapCheck {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void near(double a,double b,String message){check(Math.abs(a-b)<1e-6,message+": "+a+" != "+b);}
    static MapDefinition custom(){MapDefinition m=MapEditor.newMap();m.name="Custom arena";m.width=40;m.depth=30;m.plane=MapDefinition.Plane.ELLIPSE;m.color=0x713f8c;
        m.spawns.clear();m.spawns.add(new MapDefinition.Spawn(-8,0,0,1));m.spawns.add(new MapDefinition.Spawn(8,0,0,2));
        for(MapDefinition.Shape shape:MapDefinition.Shape.values()){MapDefinition.Part p=new MapDefinition.Part();p.shape=shape;p.name="Part "+shape;p.x=shape.ordinal()*3;p.z=-5;p.y=shape.ordinal();p.yaw=20;p.color=0x29aa66;m.objects.add(p);}
        MapDefinition.Part loose=m.objects.get(3);loose.anchored=false;loose.collision=false;loose.vx=2;m.targets.add(new MapDefinition.Spawn(3,0,4,1));return m;
    }
    static void persistence(Path dir)throws Exception{
        MapDefinition m=custom();Path file=dir.resolve("Custom.map");m.save(file);MapDefinition read=MapDefinition.read(file);
        check(read.objects.size()==4&&read.spawns.size()==2&&read.targets.size()==1,"All map entities round trip");near(read.width,40,"Plane width");check(read.plane==MapDefinition.Plane.ELLIPSE&&read.color==m.color,"Plane appearance");
        MapDefinition.Part p=read.objects.get(3);check(!p.anchored&&!p.collision&&p.shape==MapDefinition.Shape.WEDGE&&p.color==0x29aa66,"Object flags and appearance");near(p.yaw,20,"Yaw");near(p.vx,2,"Velocity");
        read.objects.get(0).x=99;near(m.objects.get(0).x,0,"Loaded map independent of authored data");
        Random last=new Random(){@Override public int nextInt(int bound){return bound-1;}};
        check(MapStorage.choose(dir,last).name.equals(m.name),"Saved map automatically joins random pool");
        Files.write(dir.resolve("Broken.map"),new byte[]{0,1,2});check(MapStorage.choose(dir,last).name.equals(m.name),"Invalid file cannot break match selection");
        MapDefinition invalid=custom();invalid.spawns.remove(0);try{invalid.save(file);throw new AssertionError("Missing Team 1 accepted");}catch(java.io.IOException expected){}
        check(MapDefinition.read(file).name.equals(m.name),"Failed save preserves prior file");
        invalid=custom();invalid.width=Double.NaN;try{invalid.encode();throw new AssertionError("NaN accepted");}catch(java.io.IOException expected){}
        MapDefinition asset=MapEditor.newMap();MapDefinition.Part part=m.objects.get(2).copy();part.x=3;part.z=3;asset.objects.add(part);Path assetFile=dir.resolve("Shape.asset");asset.save(assetFile);check(MapDefinition.read(assetFile).objects.get(0).shape==MapDefinition.Shape.CYLINDER,"Reusable asset round trip");
    }
    static void physics()throws Exception{
        MapDefinition m=MapEditor.newMap();MapDefinition.Part wall=new MapDefinition.Part();wall.x=3;wall.w=.2;wall.h=3;wall.d=6;m.objects.add(wall);
        double[] hit=MapPhysics.move(m,0,0,0,8,0,0,.5,1);near(hit[0],2.4,"Swept collision blocks thin wall");
        wall.collision=false;near(MapPhysics.move(m,0,0,0,8,0,0,.5,1)[0],8,"No-collision object permits passage");wall.collision=true;
        near(MapPhysics.move(m,0,4,0,8,4,0,.5,1)[0],8,"Jump above object");
        double[] land=MapPhysics.move(m,3,5,0,3,1,0,.5,1);near(land[1],3,"Land on object top");
        near(MapPhysics.move(m,3,3,0,3,2.99,0,.5,1)[1],3,"Stay on platform");
        LobbyWorld world=new LobbyWorld();MapDefinition elevated=MapEditor.newMap();elevated.spawns.get(0).y=4;world.mapChoice=()->elevated;
        LobbyWorld.Player player=world.add("Host",true);world.action(player.id,4,"");near(player.y,4,"Authored spawn height");near(player.body.lift,4,"LAN hurtbox uses spawn height");
        world.map=m;world.arena.map=m;player.combat.map=m;player.x=player.body.x=0;player.y=player.body.lift=0;player.z=player.body.z=0;world.input(player.id,1,0,0,false);
        for(int i=0;i<100;i++)world.tick(.01);near(player.x,2.4,"LAN movement respects scenery collision");
        m.plane=MapDefinition.Plane.ELLIPSE;double[] b=MapPhysics.boundary(m,99,99,.5);near(Math.hypot(b[0]/9.5,b[1]/9.5),1,"Ellipse boundary");
        MapDefinition.Part loose=new MapDefinition.Part();loose.x=-4;loose.y=5;loose.anchored=false;m.objects.add(loose);for(int i=0;i<200;i++)MapPhysics.tick(m,.02);check(loose.y<.02&&loose.y>=0,"Loose object falls and settles");near(wall.y,0,"Anchored object remains fixed");
        for(MapDefinition.Shape shape:MapDefinition.Shape.values()){loose.shape=shape;check(!MapMeshes.faces(loose).isEmpty(),"Shape mesh exists");}
    }
    static void network()throws Exception{
        try(LobbyNetwork.Server server=new LobbyNetwork.Server(0)){
            server.world.mapChoice=MapCheck::custom;
            try(LobbyNetwork.Client host=new LobbyNetwork.Client("127.0.0.1",server.port(),"Host",server.hostKey)){
                host.action(4,"");long until=System.currentTimeMillis()+5000;while((host.snapshot==null||!host.snapshot.started)&&System.currentTimeMillis()<until)Thread.sleep(20);
                check(host.snapshot!=null&&host.snapshot.started,"Custom LAN match starts");check(host.snapshot.map.name.equals("Custom arena")&&host.snapshot.map.objects.size()==4,"Host transfers complete map without guest files");
                near(host.snapshot.player(host.id).x,-8,"Host uses Team 1 spawn");
                try(LobbyNetwork.Client guest=new LobbyNetwork.Client("127.0.0.1",server.port(),"Guest","")){
                    until=System.currentTimeMillis()+5000;while((guest.snapshot==null||!guest.snapshot.started)&&System.currentTimeMillis()<until)Thread.sleep(20);
                    check(guest.snapshot!=null&&guest.snapshot.map!=null,"Late join receives map");near(guest.snapshot.player(guest.id).x,-8,"Guest also uses Team 1, not Team 2");
                    double oldY=guest.snapshot.map.objects.get(3).y;Thread.sleep(150);check(guest.snapshot.map.objects.get(3).y<oldY,"Host synchronizes loose-object motion");
                }
            }
        }
    }
    static void editor()throws Exception{SwingUtilities.invokeAndWait(()->{
        MapEditor editor=new MapEditor();editor.setSize(1280,800);editor.doLayout();editor.map=custom();editor.refreshList(editor.map.objects.get(0));editor.view.setSize(740,700);
        BufferedImage image=new BufferedImage(740,700,BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();editor.view.paint(g);g.dispose();check(editor.view.painted.size()>30,"Editor renders 3D shapes and spawn markers");
        editor.duplicate();check(editor.map.objects.size()==5,"Duplicate object");editor.delete();check(editor.map.objects.size()==4,"Delete object");
        MapDefinition.Part selected=editor.map.objects.get(0);editor.refreshList(selected);
        editor.getActionMap().get("control C").actionPerformed(null);editor.getActionMap().get("control V").actionPerformed(null);
        MapDefinition.Part pasted=(MapDefinition.Part)editor.selection.getSelectedValue();check(pasted!=selected&&pasted.color==selected.color,"Clipboard preserves independent object data");pasted.w=17;check(selected.w!=17,"Paste does not alias original");
        editor.getActionMap().get("DELETE").actionPerformed(null);check(editor.map.objects.size()==4,"Delete shortcut removes selection");editor.refreshList(selected);
        check(MapEditor.editingText(new JTextField())&&MapEditor.editingText(new JSpinner()),"Typing controls protected from shortcuts");
        for(int i=1;i<=5;i++){editor.getActionMap().get(""+i).actionPerformed(null);check(editor.tool.ordinal()==i-1,"Number selects tool");}
        editor.setTool(MapEditor.Tool.MOVE);Graphics2D handles=image.createGraphics();editor.gizmo.paint(handles);check(editor.gizmo.handles.size()==3,"Three move axes rendered");
        MapGizmo.Handle axis=editor.gizmo.handles.get(1);editor.gizmo.active=axis;editor.gizmo.begin(selected);double originalY=selected.y;
        editor.gizmo.drag(axis.dx,axis.dy,false);near(selected.y,originalY+1,"Projected axis drag moves one world unit");editor.gizmo.cancel();near(selected.y,originalY,"Escape cancellation restores transform");
        editor.setTool(MapEditor.Tool.RESIZE);editor.gizmo.paint(handles);axis=editor.gizmo.handles.get(0);editor.gizmo.active=axis;editor.gizmo.begin(selected);double originalWidth=selected.w;
        editor.gizmo.drag(axis.dx*2,axis.dy*2,false);near(selected.w,originalWidth+2,"Dimensions handle changes local width");editor.gizmo.cancel();
        editor.setTool(MapEditor.Tool.ROTATE);editor.gizmo.paint(handles);editor.gizmo.active=editor.gizmo.handles.get(0);editor.gizmo.begin(selected);double angle=selected.yaw;editor.gizmo.drag(30,0,false);near(selected.yaw,angle+30,"Rotation handle changes yaw");editor.gizmo.cancel();
        editor.setTool(MapEditor.Tool.SCALE);editor.gizmo.paint(handles);editor.gizmo.active=editor.gizmo.handles.get(0);editor.gizmo.begin(selected);double ratio=selected.w/selected.h;editor.gizmo.drag(50,0,false);check(selected.w>originalWidth,"Uniform scale grows model");near(selected.w/selected.h,ratio,"Uniform scale preserves proportions");editor.gizmo.cancel();
        editor.getActionMap().get("6").actionPerformed(null);near(editor.view.tx,selected.x,"Focus centers on selection");handles.dispose();
        try{
            CubeMovement game=new CubeMovement();MovementCheck.field("activeMap").set(game,custom());MovementCheck.field("mapLoaded").setBoolean(game,true);game.setSize(1000,700);game.doLayout();BufferedImage arena=new BufferedImage(1000,700,BufferedImage.TYPE_INT_RGB);Graphics2D ag=arena.createGraphics();game.paint(ag);ag.dispose();ImageIO.write(arena,"png",Paths.get("build","custom-map-game-preview.png").toFile());
            MapDefinition collision=MapEditor.newMap();MapDefinition.Part wall=new MapDefinition.Part();wall.x=3;wall.w=.2;wall.h=3;wall.d=6;collision.objects.add(wall);
            MovementCheck.field("activeMap").set(game,collision);((CombatSystem)MovementCheck.field("combat").get(game)).map=collision;MovementCheck.field("paused").setBoolean(game,false);
            MovementCheck.key(game,java.awt.event.KeyEvent.VK_D,false,false);java.lang.reflect.Method move=CubeMovement.class.getDeclaredMethod("move",double.class);move.setAccessible(true);for(int i=0;i<100;i++)move.invoke(game,.01);near(MovementCheck.field("x").getDouble(game),2.4,"Solo movement respects same scenery collision as LAN");
        }catch(Exception ex){throw new RuntimeException(ex);}
        editor.setTool(MapEditor.Tool.MOVE);Graphics2D clean=image.createGraphics();editor.view.paint(clean);clean.dispose();
        try{ImageIO.write(image,"png",Paths.get("build","map-studio-preview.png").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
    });}
    public static void main(String[] args){try{Path dir=Files.createTempDirectory("map-check-");try{persistence(dir);physics();network();editor();}finally{try(java.util.stream.Stream<Path> files=Files.walk(dir)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}System.out.println("Passed: map/asset round trips, validation, random pool, collision/platforms, loose physics, LAN map/physics transfer, Team 1 spawns, editor rendering.");System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
