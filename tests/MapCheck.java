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
        java.util.List<MapStorage.Choice> choices=MapStorage.choices(dir);
        check(choices.size()==4&&choices.get(0).random&&choices.get(1).file==null,"Picker includes Random, built-in, and every map file");
        MapStorage.Choice selected=choices.stream().filter(c->c.label.equals("Custom.map")).findFirst().get();
        check(selected.load().name.equals(m.name),"Explicit map choice loads intended file");
        try{choices.stream().filter(c->c.label.equals("Broken.map")).findFirst().get().load();throw new AssertionError("Invalid explicit choice accepted");}catch(java.io.IOException expected){}
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
    static void surfaces()throws Exception{
        MapDefinition map=MapEditor.newMap();map.width=map.depth=100;
        for(MapDefinition.Shape shape:MapDefinition.Shape.values())for(double yaw:new double[]{0,37,127}){
            MapDefinition.Part part=new MapDefinition.Part();part.shape=shape;part.y=3;part.w=part.h=part.d=2;part.yaw=yaw;map.objects.clear();map.objects.add(part);
            for(double[][] face:MapMeshes.faces(part)){
                double[] normal=null,mid=new double[3];for(double[] v:face)for(int j=0;j<3;j++)mid[j]+=v[j]/face.length;
                for(int i=1;i+1<face.length;i++){double[] a={face[i][0]-face[0][0],face[i][1]-face[0][1],face[i][2]-face[0][2]},b={face[i+1][0]-face[0][0],face[i+1][1]-face[0][1],face[i+1][2]-face[0][2]};double[] n={a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};double len=Math.sqrt(n[0]*n[0]+n[1]*n[1]+n[2]*n[2]);if(len>1e-8){for(int j=0;j<3;j++)n[j]/=len;normal=n;break;}}
                if(normal==null)continue;
                if(normal[0]*mid[0]+normal[1]*(mid[1]-4)+normal[2]*mid[2]<0)for(int j=0;j<3;j++)normal[j]*=-1;
                double[] start={mid[0]+normal[0]*2,mid[1]+normal[1]*2-.05,mid[2]+normal[2]*2};
                double[] result=MapPhysics.move(map,start[0],start[1],start[2],mid[0]-normal[0]*.2,mid[1]-normal[1]*.2-.05,mid[2]-normal[2]*.2,.05,.1);
                double distance=(result[0]-mid[0])*normal[0]+(result[1]+.05-mid[1])*normal[1]+(result[2]-mid[2])*normal[2];
                check(distance>=-1e-6,"Every visible face blocks entry: "+shape+" yaw "+yaw+" mid "+Arrays.toString(mid)+" normal "+Arrays.toString(normal)+" result "+Arrays.toString(result)+" distance "+distance);
                double[] stable=MapPhysics.move(map,result[0],result[1],result[2],result[0],result[1],result[2],.05,.1);
                for(int j=0;j<3;j++)near(stable[j],result[j],"Face sweep leaves actor outside solid");
            }
        }
        MapDefinition.Part box=new MapDefinition.Part();box.w=box.h=box.d=2;map.objects.clear();map.objects.add(box);
        double[] inside=MapPhysics.move(map,.95,.5,0,.95,.5,0,.5,1);check(inside[0]>=1.5-1e-6,"Recover initial overlap instead of passing through");
        double[] diagonal=MapPhysics.move(map,-4,4,0,4,-2,0,.5,1);check(diagonal[0]<1.5||diagonal[1]>=2-1e-6,"Simultaneous horizontal/vertical sweep cannot tunnel");
        double[] top=MapPhysics.move(map,-2,2,0,2,2,0,.5,1);near(top[0],2,"Walk tangentially across top face");
        box.shape=MapDefinition.Shape.WEDGE;box.h=1;box.d=4;
        double[] slope=MapPhysics.move(map,0,0,3,0,-.01,0,.5,1);check(slope[1]>.2&&slope[2]<1,"Wedge slope supports upward sliding");
        box.collision=false;double[] pass=MapPhysics.move(map,-4,.5,0,4,.5,0,.5,1);near(pass[0],4,"Disabled collision remains passable");
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
    static void rendering(){
        MapEditor e=new MapEditor();e.view.setSize(800,600);e.view.yaw=e.view.pitch=0;e.view.distance=10;
        Object redOwner=new Object(),blueOwner=new Object();
        MapEditor.Face clipped=e.view.face(new double[][]{{-.1,-.2,9},{1,-.2,11},{1,.2,11},{-.1,.2,9}},0xff0000,redOwner);
        check(clipped!=null&&clipped.screen.length>=3,"Near-plane crossing preserves visible part of face");
        check(e.view.face(new double[][]{{-1,-1,11},{1,-1,11},{1,1,11}},0xffffff,redOwner)==null,"Fully behind-camera face is discarded");
        MapEditor.Face red=e.view.face(new double[][]{{-.4,-.4,8},{2,-2,0},{2,2,0},{-.4,.4,8}},0xff0000,redOwner);
        MapEditor.Face blue=e.view.face(new double[][]{{-1,-1,5},{1,-1,5},{1,1,5},{-1,1,5}},0x0000ff,blueOwner);
        java.util.List<MapEditor.Face> faces=new ArrayList<>(Arrays.asList(red,blue));MapDepthRenderer renderer=new MapDepthRenderer();renderer.render(800,600,faces,Collections.emptySet());
        check((renderer.image.getRGB(400,300)&0xffffff)==0xff0000,"Per-pixel depth fixes slanted-face average-sort failure");
        int[] first=renderer.pixels.clone();Collections.reverse(faces);renderer.render(800,600,faces,Collections.emptySet());check(Arrays.equals(first,renderer.pixels),"Visibility is independent of draw order");
        e.view.painted.clear();e.view.painted.addAll(faces);check(e.view.pickObject(new Point(400,300))==redOwner,"Click selects actual front surface");
        MapEditor.Face hidden=e.view.face(new double[][]{{-.2,-.2,3},{.2,-.2,3},{.2,.2,3},{-.2,.2,3}},0x00ff00,redOwner);
        renderer.render(800,600,Arrays.asList(hidden,blue),Collections.singleton(redOwner));
        for(int y=280;y<320;y++)for(int x=380;x<420;x++)check(renderer.image.getRGB(x,y)!=Color.YELLOW.getRGB(),"Hidden selected edges do not bleed through foreground");
        renderer.render(800,600,Arrays.asList(clipped),Collections.singleton(redOwner));check(Arrays.stream(renderer.depth).anyMatch(d->d>0),"Clipped face rasterizes at close zoom");
        e.view.distance=4;e.view.pitch=.7;e.view.yaw=.8;MapDefinition.Part box=new MapDefinition.Part();box.w=box.h=box.d=8;
        for(double angle:new double[]{0,.4,1,2,3,4,5}){e.view.yaw=angle;faces.clear();for(double[][] mesh:MapMeshes.faces(box)){MapEditor.Face f=e.view.face(mesh,0x779db4,redOwner);if(f!=null)faces.add(f);}renderer.render(800,600,faces,Collections.singleton(redOwner));}
    }
    static void editor()throws Exception{SwingUtilities.invokeAndWait(()->{
        rendering();
        cameraNavigation();
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
        editor.view.distance=60;editor.view.tx=editor.view.ty=editor.view.tz=0;editor.setTool(MapEditor.Tool.SELECT);Graphics2D boxPaint=image.createGraphics();editor.view.paint(boxPaint);boxPaint.dispose();
        java.util.List<Object> all=new ArrayList<>();all.addAll(editor.map.objects);all.addAll(editor.map.spawns);all.addAll(editor.map.targets);
        // Reverse-direction drag across the viewport selects every projected item, not the ground.
        editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_PRESSED,0,0,739,699,1,false,1));
        editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_DRAGGED,0,java.awt.event.InputEvent.BUTTON1_DOWN_MASK,0,0,0,false,0));
        editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_RELEASED,0,0,0,0,1,false,1));
        check(editor.selectedItems().containsAll(all)&&editor.selectedItems().size()==all.size(),"Reverse box selects all objects and markers, excludes ground");
        editor.view.boxStart=new Point(0,0);editor.view.boxEnd=new Point(5,5);editor.view.boxAdd=false;editor.view.finishBox();check(editor.selectedItems().isEmpty(),"Empty box clears selection");
        editor.selectItems(Arrays.asList(editor.map.objects.get(0)));editor.view.boxOriginal.clear();editor.view.boxOriginal.addAll(editor.selectedItems());editor.view.boxStart=new Point(0,0);editor.view.boxEnd=new Point(5,5);editor.view.boxAdd=true;editor.view.finishBox();check(editor.selectedItems().size()==1,"Shift box preserves prior selection");
        for(MapEditor.Tool tool:MapEditor.Tool.values()){
            editor.setTool(tool);editor.selectItems(Arrays.asList(editor.map.objects.get(0)));editor.view.painted.clear();
            java.awt.geom.Path2D hit=new java.awt.geom.Path2D.Double();hit.append(new Rectangle(20,20,40,40),false);
            editor.view.painted.add(new MapEditor.Face(hit,1,Color.WHITE,editor.map.objects.get(1)));
            for(int click=0;click<2;click++){
                editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_PRESSED,0,java.awt.event.InputEvent.SHIFT_DOWN_MASK,30,30,1,false,1));
                editor.view.dispatchEvent(new java.awt.event.MouseEvent(editor.view,java.awt.event.MouseEvent.MOUSE_RELEASED,0,java.awt.event.InputEvent.SHIFT_DOWN_MASK,30,30,1,false,1));
                check(editor.selectedItems().size()==(click==0?2:1),"Shift-click adds/toggles in "+tool);
                check(editor.selectedItems().contains(editor.map.objects.get(0)),"Shift-click keeps original selection");
                check(editor.gizmo.target==null,"Shift selection does not start transform");
            }
        }
        editor.selectItems(Arrays.asList(editor.map.objects.get(0),editor.map.objects.get(1)));editor.setTool(MapEditor.Tool.MOVE);
        Graphics2D groupPaint=image.createGraphics();editor.gizmo.paint(groupPaint);groupPaint.dispose();editor.gizmo.active=editor.gizmo.handles.get(1);editor.gizmo.begin(editor.selection.getSelectedValue());
        double y0=editor.map.objects.get(0).y,y1=editor.map.objects.get(1).y;MapGizmo.Handle yAxis=editor.gizmo.active;editor.gizmo.drag(yAxis.dx,yAxis.dy,false);
        near(editor.map.objects.get(0).y,y0+1,"Group moves first object");near(editor.map.objects.get(1).y,y1+1,"Group moves second object");editor.gizmo.cancel();near(editor.map.objects.get(0).y,y0,"Cancel restores first");near(editor.map.objects.get(1).y,y1,"Cancel restores second");
        int count=editor.map.objects.size();editor.copySelection();editor.pasteSelection();check(editor.map.objects.size()==count+2&&editor.selectedItems().size()==2,"Group paste selects independent copies");editor.delete();check(editor.map.objects.size()==count,"Delete removes whole selection");
        editor.refreshList(selected);editor.history.reset();int initialCount=editor.map.objects.size();editor.copySelection();editor.pasteSelection();editor.getActionMap().get("control Z").actionPerformed(null);check(editor.map.objects.size()==initialCount,"Undo paste");editor.getActionMap().get("control Y").actionPerformed(null);check(editor.map.objects.size()==initialCount+1,"Redo paste");
        editor.delete();editor.history.travel(false);check(editor.map.objects.size()==initialCount+1,"Undo group delete");editor.history.travel(true);check(editor.map.objects.size()==initialCount,"Redo group delete");
        editor.selectItems(Arrays.asList(editor.map.objects.get(0),editor.map.objects.get(1)));editor.setTool(MapEditor.Tool.MOVE);editor.history.reset();
        Graphics2D historyPaint=image.createGraphics();editor.gizmo.paint(historyPaint);historyPaint.dispose();MapGizmo.Handle handle=editor.gizmo.handles.get(1);editor.gizmo.active=handle;editor.gizmo.begin(editor.selection.getSelectedValue());double original0=editor.map.objects.get(0).y,original1=editor.map.objects.get(1).y;
        for(int i=0;i<5;i++)editor.gizmo.drag(handle.dx*.2,handle.dy*.2,false);editor.gizmo.finish();check(editor.history.undo.size()==1,"Whole group drag is one undo step");editor.history.travel(false);near(editor.map.objects.get(0).y,original0,"Undo first group transform");near(editor.map.objects.get(1).y,original1,"Undo second group transform");editor.history.travel(true);near(editor.map.objects.get(1).y,original1+1,"Redo group transform");
        editor.history.travel(false);editor.map.name="New edit";editor.changed();check(editor.history.redo.isEmpty(),"New edit clears redo");editor.map.spawns.clear();editor.changed();editor.history.travel(false);check(!editor.map.spawns.isEmpty(),"Undo supports temporarily invalid maps");
        editor.refreshList(editor.map.objects.get(0));editor.setTool(MapEditor.Tool.MOVE);Graphics2D clean=image.createGraphics();editor.view.paint(clean);clean.dispose();
        try{ImageIO.write(image,"png",Paths.get("build","map-studio-preview.png").toFile());}catch(Exception ex){throw new RuntimeException(ex);}
    });}
    static void cameraNavigation(){
        MapEditor e=new MapEditor();e.view.setSize(800,600);
        for(double angle:new double[]{0,Math.PI/2,Math.PI,4})for(double elevation:new double[]{0,.7,Math.PI/2}){
            e.view.yaw=angle;e.view.pitch=elevation;e.view.distance=30;e.view.tx=e.view.ty=e.view.tz=0;
            double[] before=e.gizmo.screen(new double[]{0,0,0});e.view.pan(60,-25);double[] after=e.gizmo.screen(new double[]{0,0,0});near(after[0]-before[0],60,"Pan follows screen horizontal at every angle");near(after[1]-before[1],-25,"Pan follows screen vertical at every angle");
            Point cursor=new Point(520,230);double[] offset=e.view.screenDelta(cursor.x-400,300-cursor.y);double[] anchor={e.view.tx+offset[0],e.view.ty+offset[1],e.view.tz+offset[2]};e.view.zoom(cursor,-2);double[] zoomed=e.gizmo.screen(anchor);near(zoomed[0],cursor.x,"Cursor zoom anchors horizontal position");near(zoomed[1],cursor.y,"Cursor zoom anchors vertical position");
            double tx=e.view.tx,ty=e.view.ty,tz=e.view.tz;e.view.orbit(250,900);near(e.view.tx,tx,"Orbit keeps pivot X");near(e.view.ty,ty,"Orbit keeps pivot height");near(e.view.tz,tz,"Orbit keeps pivot Z");check(Math.abs(e.view.pitch)<Math.PI/2,"Orbit cannot flip upside down");
        }
        e.map=custom();e.view.preset(.6,.7);e.view.frameAll();
        for(MapDefinition.Part p:e.map.objects)for(double[][] face:MapMeshes.faces(p))for(double[] vertex:face){double[] screen=e.gizmo.screen(vertex);check(screen!=null&&screen[0]>=0&&screen[0]<=800&&screen[1]>=0&&screen[1]<=600,"Frame all fits map objects");}
        e.view.preset(0,0);double[] d=e.view.screenDelta(0,20);check(Arrays.stream(d).allMatch(Double::isFinite),"Front-view dragging has no division by zero");
        e.view.zoom(new Point(400,300),1000);near(e.view.distance,1500,"Far zoom is bounded");e.view.zoom(new Point(400,300),-1000);near(e.view.distance,.5,"Close zoom is bounded");
        check(!e.dirty&&e.history.undo.isEmpty(),"Camera navigation does not change map/history");
    }
    public static void main(String[] args){try{Path dir=Files.createTempDirectory("map-check-");try{persistence(dir);physics();surfaces();network();editor();}finally{try(java.util.stream.Stream<Path> files=Files.walk(dir)){for(Path p:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.delete(p);}}System.out.println("Passed: map/asset round trips, validation, random pool, collision/platforms, loose physics, LAN map/physics transfer, Team 1 spawns, editor rendering and camera navigation.");System.exit(0);}catch(Throwable e){e.printStackTrace();System.exit(1);}}
}
