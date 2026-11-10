import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Desktop 3D map authoring, using the same meshes and physics as the game. */
public final class MapEditor extends JPanel {
    MapDefinition map=MapStorage.practice(),simulation;
    final DefaultListModel<Object> items=new DefaultListModel<>();
    final JList<Object> selection=new JList<>(items);
    final View view=new View();
    final JPanel properties=new JPanel();
    final JLabel status=new JLabel("Middle/right drag: orbit | Shift + middle/right: pan | Wheel: zoom | F: focus | Home: frame all");
    boolean dirty,refreshing;
    Path current;
    enum Tool { SELECT, MOVE, ROTATE, RESIZE, SCALE }
    Tool tool=Tool.MOVE;
    final MapGizmo gizmo=new MapGizmo(this);
    final MapHistory history=new MapHistory(this);
    final java.util.List<Object> clipboard=new ArrayList<>();
    final java.util.Set<Object> clipboardTargets=Collections.newSetFromMap(new IdentityHashMap<>());
    final JToggleButton[] toolButtons=new JToggleButton[5];
    final javax.swing.Timer timer=new javax.swing.Timer(16,e->{if(simulation!=null){MapPhysics.tick(simulation,.016);view.repaint();}});
    public MapEditor(){
        super(new BorderLayout(8,8));setPreferredSize(new Dimension(1280,800));
        JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(bar,"New",()->replace(newMap(),null));button(bar,"Built-in Practice",()->replace(MapStorage.practice(),null));button(bar,"Open map",this::open);button(bar,"Save",()->save(false));button(bar,"Save As",()->save(true));
        button(bar,"Save asset",this::saveAsset);button(bar,"Insert asset",this::insertAsset);
        button(bar,"Test physics",()->{try{simulation=map.copy();status.setText("Preview running — Stop restores authored positions. Editing stops preview.");}catch(RuntimeException ex){error(ex);}});
        button(bar,"Stop",()->{simulation=null;view.repaint();});button(bar,"Frame all (Home)",()->view.frameAll());
        JPanel top=new JPanel(new GridLayout(0,1));top.add(bar);JPanel tools=new JPanel(new FlowLayout(FlowLayout.LEFT));ButtonGroup group=new ButtonGroup();
        for(Tool t:Tool.values()){JToggleButton b=new JToggleButton((t.ordinal()+1)+" "+t);toolButtons[t.ordinal()]=b;group.add(b);tools.add(b);b.addActionListener(e->setTool(t));}
        button(tools,"6 Focus",this::focusSelection);button(tools,"Copy (Ctrl+C)",this::copySelection);button(tools,"Paste (Ctrl+V)",this::pasteSelection);button(tools,"Undo",()->history.travel(false));button(tools,"Redo",()->history.travel(true));top.add(tools);JPanel cameras=new JPanel(new FlowLayout(FlowLayout.LEFT));button(cameras,"Perspective",()->view.preset(.6,.7));button(cameras,"Top",()->view.preset(0,Math.PI/2));button(cameras,"Front",()->view.preset(0,0));button(cameras,"Right",()->view.preset(Math.PI/2,0));cameras.add(new JLabel("Orbit: middle/right drag | Pan: Shift + middle/right | Zoom: wheel | Focus: F | Frame all: Home"));top.add(cameras);add(top,BorderLayout.NORTH);
        JPanel left=new JPanel(new BorderLayout(4,4));left.setPreferredSize(new Dimension(230,600));
        JPanel creators=new JPanel(new GridLayout(0,2,4,4));
        for(MapDefinition.Shape shape:MapDefinition.Shape.values())button(creators,"+ "+shape,()->{if(map.objects.size()>=128){status.setText("128 object limit reached.");return;}MapDefinition.Part p=new MapDefinition.Part();p.shape=shape;p.name=shape.toString();p.x=3;map.objects.add(p);changed();refreshList(p);});
        button(creators,"+ Spawn",()->{if(map.spawns.size()>=64)return;MapDefinition.Spawn s=new MapDefinition.Spawn(0,0,0,1);map.spawns.add(s);changed();refreshList(s);});
        button(creators,"+ Target",()->{if(map.targets.size()>=64)return;MapDefinition.Spawn s=new MapDefinition.Spawn(0,0,-3,1);map.targets.add(s);changed();refreshList(s);});
        button(creators,"Duplicate",this::duplicate);button(creators,"Delete",this::delete);
        left.add(creators,BorderLayout.NORTH);selection.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        selection.setCellRenderer(new DefaultListCellRenderer(){@Override public Component getListCellRendererComponent(JList<?> list,Object value,int index,boolean selected,boolean focus){return super.getListCellRendererComponent(list,map.targets.contains(value)?"Target "+(map.targets.indexOf(value)+1):value,index,selected,focus);}});
        left.add(new JScrollPane(selection),BorderLayout.CENTER);
        selection.addListSelectionListener(e->{if(!e.getValueIsAdjusting())inspect();});add(left,BorderLayout.WEST);
        properties.setLayout(new BoxLayout(properties,BoxLayout.Y_AXIS));JScrollPane inspector=new JScrollPane(properties);inspector.setPreferredSize(new Dimension(285,600));add(inspector,BorderLayout.EAST);add(view,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);refreshList(null);setTool(Tool.MOVE);
        for(Tool t:Tool.values())shortcut(""+(t.ordinal()+1),()->setTool(t));shortcut("6",this::focusSelection);shortcut("F",this::focusSelection);
        shortcut("DELETE",this::delete);shortcut("control C",this::copySelection);shortcut("control V",this::pasteSelection);shortcut("control X",()->{copySelection();delete();});shortcut("control D",this::duplicate);
        shortcut("control S",()->save(false));shortcut("control shift S",()->save(true));shortcut("control O",this::open);shortcut("ESCAPE",()->gizmo.cancel());
        shortcut("HOME",()->view.frameAll());shortcut("control Z",()->history.travel(false));shortcut("control Y",()->history.travel(true));shortcut("control shift Z",()->history.travel(true));history.reset();
    }
    static boolean editingText(Component focus){return focus instanceof javax.swing.text.JTextComponent||focus instanceof JComboBox||focus instanceof JSpinner||focus!=null&&SwingUtilities.getAncestorOfClass(JSpinner.class,focus)!=null;}
    void shortcut(String key,Runnable action){Action handler=new AbstractAction(){public void actionPerformed(ActionEvent e){if(key.equals("control Z")||key.equals("control Y")||key.equals("control shift Z")||!editingText(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner()))action.run();}};getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);getActionMap().put(key,handler);for(JComponent c:new JComponent[]{view,selection}){c.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),key);c.getActionMap().put(key,handler);}}
    void setTool(Tool next){gizmo.cancel();tool=next;toolButtons[next.ordinal()].setSelected(true);simulation=null;status.setText("1 Select | 2 Move | 3 Rotate (Y) | 4 Dimensions | 5 Scale | 6/F Focus | Shift: precision | Esc: cancel drag");view.repaint();}
    void focusSelection(){if(selectedItems().isEmpty()){view.frameAll();return;}view.frame(selectedItems(),false);}
    java.util.List<Object> selectedItems(){java.util.List<Object> result=new ArrayList<>();for(Object o:selection.getSelectedValuesList())if(o instanceof MapDefinition.Part||o instanceof MapDefinition.Spawn)result.add(o);return result;}
    void selectItems(Collection<?> values){selection.setValueIsAdjusting(true);selection.clearSelection();for(int i=1;i<items.size();i++)if(values.contains(items.get(i)))selection.addSelectionInterval(i,i);selection.setValueIsAdjusting(false);view.repaint();}
    void copySelection(){clipboard.clear();clipboardTargets.clear();for(Object o:selectedItems()){Object copy;if(o instanceof MapDefinition.Part)copy=((MapDefinition.Part)o).copy();else{MapDefinition.Spawn s=(MapDefinition.Spawn)o;copy=new MapDefinition.Spawn(s.x,s.y,s.z,s.team);if(map.targets.contains(o))clipboardTargets.add(copy);}clipboard.add(copy);}status.setText(clipboard.size()+" items copied. Ctrl+V pastes independent copies.");}
    void pasteSelection(){int parts=0,spawns=0,targets=0;for(Object o:clipboard)if(o instanceof MapDefinition.Part)parts++;else if(clipboardTargets.contains(o))targets++;else spawns++;
        if(map.objects.size()+parts>128||map.spawns.size()+spawns>64||map.targets.size()+targets>64){status.setText("Cannot paste group: map object/spawn limit reached.");return;}
        java.util.List<Object> added=new ArrayList<>();for(Object o:clipboard){if(o instanceof MapDefinition.Part){MapDefinition.Part p=((MapDefinition.Part)o).copy();p.x=Math.min(200,p.x+2);map.objects.add(p);added.add(p);}else{MapDefinition.Spawn s=(MapDefinition.Spawn)o;MapDefinition.Spawn p=new MapDefinition.Spawn(Math.min(200,s.x+2),s.y,s.z,s.team);(clipboardTargets.contains(o)?map.targets:map.spawns).add(p);added.add(p);}}
        if(!added.isEmpty()){changed();refreshList(null);selectItems(added);}
    }
    static MapDefinition newMap(){MapDefinition m=new MapDefinition();m.spawns.add(new MapDefinition.Spawn(0,0,0,1));return m;}
    @Override public void addNotify(){super.addNotify();timer.start();}
    @Override public void removeNotify(){timer.stop();super.removeNotify();}
    void changed(){dirty=true;simulation=null;history.record();view.repaint();selection.repaint();}
    boolean discard(){return !dirty||JOptionPane.showConfirmDialog(this,"Discard unsaved changes?","Map Studio",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION;}
    void replace(MapDefinition next,Path path){if(!discard())return;gizmo.cancel();map=next;current=path;dirty=false;simulation=null;refreshList(null);history.reset();view.frameAll();}
    void refreshList(Object selected){refreshing=true;items.clear();items.addElement("Map / ground");for(MapDefinition.Part p:map.objects)items.addElement(p);for(MapDefinition.Spawn s:map.spawns)items.addElement(s);for(MapDefinition.Spawn t:map.targets)items.addElement(t);selection.setSelectedValue(selected==null?items.get(0):selected,true);refreshing=false;inspect();view.repaint();}
    void inspect(){if(refreshing)return;properties.removeAll();Object o=selection.getSelectedValue();
        if(selectedItems().size()>1)label(selectedItems().size()+" selected ? inspector edits first item");
        if(o instanceof MapDefinition.Part){MapDefinition.Part p=(MapDefinition.Part)o;label("Object (bottom-center position)");text("Name",p.name,v->p.name=v);combo("Shape",MapDefinition.Shape.values(),p.shape,v->p.shape=v);
            number("X",p.x,-200,200,v->p.x=v);number("Y",p.y,0,200,v->p.y=v);number("Z",p.z,-200,200,v->p.z=v);
            number("Width",p.w,.1,100,v->p.w=v);number("Height",p.h,.1,100,v->p.h=v);number("Depth",p.d,.1,100,v->p.d=v);number("Yaw (degrees)",p.yaw,-360,360,v->p.yaw=v);
            check("Anchored",p.anchored,v->p.anchored=v);check("Player collision",p.collision,v->p.collision=v);color("Color",p.color,v->p.color=v);
            label("Loose-object starting velocity");number("Velocity X",p.vx,-50,50,v->p.vx=v);number("Velocity Y",p.vy,-50,50,v->p.vy=v);number("Velocity Z",p.vz,-50,50,v->p.vz=v);
            label("Collision: conservative bounding box.");
        }else if(o instanceof MapDefinition.Spawn){MapDefinition.Spawn s=(MapDefinition.Spawn)o;boolean target=map.targets.contains(s);label(target?"Training target":"Team spawn");number("X",s.x,-200,200,v->s.x=v);number("Y",s.y,0,200,v->s.y=v);number("Z",s.z,-200,200,v->s.z=v);if(!target)number("Team (1–8)",s.team,1,8,v->s.team=(int)Math.round(v));label("Players currently use Team 1 only.");
        }else{label("Map settings");text("Map name",map.name,v->map.name=v);combo("Ground shape",MapDefinition.Plane.values(),map.plane,v->map.plane=v);number("Ground width",map.width,4,200,v->map.width=v);number("Ground depth",map.depth,4,200,v->map.depth=v);color("Ground color",map.color,v->map.color=v);label("Saved maps enter random selection.");label("One Team 1 spawn is required.");label("Up to 128 objects / 64 spawns.");}
        properties.revalidate();properties.repaint();view.repaint();
    }
    void label(String s){properties.add(new JLabel(s));properties.add(Box.createVerticalStrut(8));}
    void row(String name,JComponent c){JPanel r=new JPanel(new BorderLayout(5,0));r.add(new JLabel(name),BorderLayout.WEST);r.add(c,BorderLayout.CENTER);r.setMaximumSize(new Dimension(1000,32));properties.add(r);properties.add(Box.createVerticalStrut(6));}
    void number(String name,double value,double min,double max,java.util.function.DoubleConsumer setter){JSpinner s=new JSpinner(new SpinnerNumberModel(value,min,max,name.startsWith("Team")?1:.1));s.addChangeListener(e->{setter.accept(((Number)s.getValue()).doubleValue());changed();});row(name,s);}
    void text(String name,String value,java.util.function.Consumer<String> setter){JTextField t=new JTextField(value);t.getDocument().addDocumentListener(new javax.swing.event.DocumentListener(){void update(){setter.accept(t.getText());changed();}public void insertUpdate(javax.swing.event.DocumentEvent e){update();}public void removeUpdate(javax.swing.event.DocumentEvent e){update();}public void changedUpdate(javax.swing.event.DocumentEvent e){update();}});row(name,t);}
    <T> void combo(String name,T[] values,T selected,java.util.function.Consumer<T> setter){JComboBox<T> c=new JComboBox<>(values);c.setSelectedItem(selected);c.addActionListener(e->{setter.accept(c.getItemAt(c.getSelectedIndex()));changed();});row(name,c);}
    void check(String name,boolean value,java.util.function.Consumer<Boolean> setter){JCheckBox c=new JCheckBox(name,value);c.addActionListener(e->{setter.accept(c.isSelected());changed();});properties.add(c);}
    void color(String name,int rgb,java.util.function.IntConsumer setter){JButton b=new JButton(name);b.setBackground(new Color(rgb));b.addActionListener(e->{Color c=JColorChooser.showDialog(this,name,b.getBackground());if(c!=null){setter.accept(c.getRGB()&0xffffff);b.setBackground(c);changed();}});properties.add(b);}
    static void button(JPanel p,String name,Runnable action){JButton b=new JButton(name);b.addActionListener(e->action.run());p.add(b);}
    void duplicate(){java.util.List<Object> saved=new ArrayList<>(clipboard);java.util.Set<Object> targets=new HashSet<>(clipboardTargets);copySelection();pasteSelection();clipboard.clear();clipboard.addAll(saved);clipboardTargets.clear();clipboardTargets.addAll(targets);}
    void delete(){java.util.List<Object> selected=selectedItems();if(selected.isEmpty())return;map.objects.removeAll(selected);map.spawns.removeAll(selected);map.targets.removeAll(selected);changed();refreshList(null);}
    Path choose(Path dir,String extension,boolean save){try{Files.createDirectories(dir);}catch(IOException ex){error(ex);return null;}JFileChooser c=new JFileChooser(dir.toFile());c.setFileFilter(new FileNameExtensionFilter(extension+" files",extension));if((save?c.showSaveDialog(this):c.showOpenDialog(this))!=JFileChooser.APPROVE_OPTION)return null;Path path=c.getSelectedFile().toPath();if(save&&!path.toString().toLowerCase(Locale.ROOT).endsWith("."+extension))path=Paths.get(path+"."+extension);return path;}
    void open(){Path path=choose(MapStorage.DIRECTORY,"map",false);if(path!=null)try{replace(MapDefinition.read(path),path);}catch(IOException e){error(e);}}
    void save(boolean as){try{Path path=current;if(as||path==null||!path.toAbsolutePath().getParent().equals(MapStorage.DIRECTORY.toAbsolutePath())){String name=JOptionPane.showInputDialog(this,"Filename in server-storage/maps (automatically joins random pool):",map.name);if(name==null)return;if(!name.matches("[A-Za-z0-9 _-]{1,80}"))throw new IOException("Use letters, numbers, spaces, _ or - in the filename.");path=MapStorage.DIRECTORY.resolve(name+".map");if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace "+path.getFileName()+"?","Save map",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;}map.save(path);current=path;dirty=false;status.setText("Saved "+path+" — included in random selection on the next match.");}catch(IOException e){error(e);}}
    void saveAsset(){Object o=selection.getSelectedValue();if(!(o instanceof MapDefinition.Part)){status.setText("Select an object to save as a reusable asset.");return;}Path path=choose(MapStorage.ASSETS,"asset",true);if(path==null)return;if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace asset?","Save asset",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;try{MapDefinition a=newMap();MapDefinition.Part p=((MapDefinition.Part)o).copy();p.x=p.z=0;p.y=0;a.objects.add(p);a.spawns.get(0).y=200;a.save(path);status.setText("Asset saved: "+path.getFileName());}catch(IOException e){error(e);}}
    void insertAsset(){Path path=choose(MapStorage.ASSETS,"asset",false);if(path==null)return;try{MapDefinition a=MapDefinition.read(path);if(map.objects.size()+a.objects.size()>128)throw new IOException("128 object limit.");for(MapDefinition.Part p:a.objects){p.x+=3;map.objects.add(p);}changed();refreshList(a.objects.isEmpty()?null:a.objects.get(0));}catch(IOException e){error(e);}}
    void error(Exception e){JOptionPane.showMessageDialog(this,e.getMessage(),"Map Studio",JOptionPane.ERROR_MESSAGE);}
    final class View extends JPanel {
        double yaw=.6,pitch=.7,distance=35,tx,ty,tz;Point last;int drag;boolean panGesture;Object moving;
        Point boxStart,boxEnd;boolean boxAdd;
        final java.util.List<Object> boxOriginal=new ArrayList<>();
        void cancelBox(){boxStart=boxEnd=null;repaint();}
        void finishBox(){if(boxStart==null)return;java.util.Set<Object> chosen=new LinkedHashSet<>();if(boxAdd)chosen.addAll(boxOriginal);if(boxStart.distance(boxEnd)<4){Object o=pickObject(boxEnd);if(o!=null){if(boxAdd&&chosen.contains(o))chosen.remove(o);else chosen.add(o);}}else{Rectangle rectangle=boxBounds();for(Face face:painted)if(face.owner!=null&&face.path.intersects(rectangle))chosen.add(face.owner);}selectItems(chosen);cancelBox();}
        Rectangle boxBounds(){return new Rectangle(Math.min(boxStart.x,boxEnd.x),Math.min(boxStart.y,boxEnd.y),Math.abs(boxEnd.x-boxStart.x),Math.abs(boxEnd.y-boxStart.y));}
        final java.util.List<Face> painted=new ArrayList<>();
        View(){setFocusable(true);setBackground(new Color(25,32,44));MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){requestFocusInWindow();gizmo.finish();last=e.getPoint();drag=e.getButton();panGesture=e.isShiftDown();moving=null;if(drag==MouseEvent.BUTTON1){if(tool==Tool.SELECT){boxStart=boxEnd=last;boxAdd=e.isShiftDown();boxOriginal.clear();boxOriginal.addAll(selectedItems());repaint();return;}if(e.isShiftDown()){java.util.Set<Object> chosen=new LinkedHashSet<>(selectedItems());Object object=pickObject(last);if(object!=null){if(!chosen.add(object))chosen.remove(object);}selectItems(chosen);last=null;return;}if(gizmo.pick(last))return;Object object=pickObject(last);if(!selectedItems().contains(object))selection.setSelectedValue(object==null?items.get(0):object,true);if(tool==Tool.MOVE){moving=object;if(object!=null)gizmo.begin(object);}repaint();}}
            public void mouseDragged(MouseEvent e){if(last==null)return;if(boxStart!=null){boxEnd=e.getPoint();repaint();return;}double dx=e.getX()-last.x,dy=e.getY()-last.y;if(gizmo.active!=null)gizmo.drag(dx,dy,e.isShiftDown());else if(drag==MouseEvent.BUTTON3||drag==MouseEvent.BUTTON2){if(panGesture)pan(dx,dy);else orbit(dx,dy);}else if(moving!=null){if(e.isShiftDown()){dx*=.15;dy*=.15;}double[] delta=screenDelta(dx,-dy);gizmo.translateXZ(delta[0],delta[2]);}last=e.getPoint();repaint();}
            public void mouseReleased(MouseEvent e){if(boxStart!=null){boxEnd=e.getPoint();finishBox();}last=null;if(moving!=null||gizmo.target!=null)inspect();moving=null;gizmo.finish();}
            public void mouseWheelMoved(MouseWheelEvent e){zoom(e.getPoint(),e.getPreciseWheelRotation());}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        double focal(){return Math.max(1,Math.min(getWidth(),getHeight())*.9);}
        double[] screenDelta(double dx,double dy){double k=distance/focal(),c=Math.cos(yaw),s=Math.sin(yaw),sp=Math.sin(pitch),cp=Math.cos(pitch);return new double[]{k*(dx*c-dy*sp*s),k*dy*cp,k*(-dx*s-dy*sp*c)};}
        void pan(double dx,double dy){double[] v=screenDelta(-dx,dy);tx+=v[0];ty+=v[1];tz+=v[2];repaint();}
        void orbit(double dx,double dy){yaw=Math.IEEEremainder(yaw-dx*.006,Math.PI*2);pitch=Math.max(-Math.PI/2+.01,Math.min(Math.PI/2-.01,pitch+dy*.006));repaint();}
        void zoom(Point cursor,double notches){double old=distance,next=Math.max(.5,Math.min(1500,old*Math.pow(1.12,notches)));double[] offset=screenDelta(cursor.x-getWidth()/2.0,getHeight()/2.0-cursor.y);double blend=1-next/old;tx+=offset[0]*blend;ty+=offset[1]*blend;tz+=offset[2]*blend;distance=next;repaint();}
        void preset(double angle,double elevation){yaw=angle;pitch=elevation;repaint();}
        void frameAll(){java.util.List<Object> all=new ArrayList<>();all.addAll(map.objects);all.addAll(map.spawns);all.addAll(map.targets);frame(all,true);}
        void frame(Collection<?> objects,boolean ground){double[] low={Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY,Double.POSITIVE_INFINITY},high={Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY};
            if(ground){include(low,high,new double[]{-map.width/2,0,-map.depth/2});include(low,high,new double[]{map.width/2,0,map.depth/2});}
            for(Object o:objects)if(o instanceof MapDefinition.Part){double[] b=((MapDefinition.Part)o).bounds();include(low,high,new double[]{b[0],b[1],b[2]});include(low,high,new double[]{b[3],b[4],b[5]});}else if(o instanceof MapDefinition.Spawn){MapDefinition.Spawn p=(MapDefinition.Spawn)o;include(low,high,new double[]{p.x-.5,p.y,p.z-.5});include(low,high,new double[]{p.x+.5,p.y+1.5,p.z+.5});}
            if(!Double.isFinite(low[0]))return;tx=(low[0]+high[0])/2;ty=(low[1]+high[1])/2;tz=(low[2]+high[2])/2;double radius=Math.sqrt(Math.pow(high[0]-low[0],2)+Math.pow(high[1]-low[1],2)+Math.pow(high[2]-low[2],2))/2;distance=Math.max(3,Math.min(1500,radius*2.5));repaint();
        }
        void include(double[] low,double[] high,double[] point){for(int i=0;i<3;i++){low[i]=Math.min(low[i],point[i]);high[i]=Math.max(high[i],point[i]);}}
        double[] camera(double[] v){double x=v[0]-tx,z=v[2]-tz,y=v[1]-ty,rx=Math.cos(yaw)*x-Math.sin(yaw)*z,rz=Math.sin(yaw)*x+Math.cos(yaw)*z;return new double[]{rx,Math.cos(pitch)*y-Math.sin(pitch)*rz,distance-Math.sin(pitch)*y-Math.cos(pitch)*rz};}
        final MapDepthRenderer renderer=new MapDepthRenderer();
        Object pickObject(Point point){Object owner=null;double closest=0;for(Face face:painted){double q=MapDepthRenderer.at(face,point.x+.5,point.y+.5);if(q>closest+1e-12){closest=q;owner=face.owner;}}return owner;}
        Face face(double[][] vertices,int color,Object owner){
            java.util.List<double[]> clipped=new ArrayList<>();double[] a=camera(vertices[vertices.length-1]);
            for(double[] vertex:vertices){double[] b=camera(vertex);if((a[2]>=.1)!=(b[2]>=.1)){double t=(.1-a[2])/(b[2]-a[2]);clipped.add(new double[]{a[0]+t*(b[0]-a[0]),a[1]+t*(b[1]-a[1]),.1});}if(b[2]>=.1)clipped.add(b);a=b;}
            if(clipped.size()<3)return null;Path2D path=new Path2D.Double();double[][] screen=new double[clipped.size()][3];double depth=0,k=Math.min(getWidth(),getHeight())*.9;
            for(int i=0;i<screen.length;i++){double[] c=clipped.get(i);double x=getWidth()/2.0+c[0]*k/c[2],y=getHeight()/2.0-c[1]*k/c[2];screen[i]=new double[]{x,y,1/c[2]};if(i==0)path.moveTo(x,y);else path.lineTo(x,y);depth+=c[2];}path.closePath();return new Face(path,depth/screen.length,new Color(color),owner,screen);
        }
        @Override protected void paintComponent(Graphics graphics){super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);painted.clear();MapDefinition m=simulation==null?map:simulation;add(face(MapMeshes.plane(m),m.color,null));
            for(int n=0;n<m.objects.size();n++){MapDefinition.Part p=m.objects.get(n);int i=0;for(double[][] f:MapMeshes.faces(p)){Color c=new Color(p.color);float shade=.7f+(i++%4)*.08f;add(face(f,new Color((int)(c.getRed()*shade),(int)(c.getGreen()*shade),(int)(c.getBlue()*shade)).getRGB(),map.objects.get(n)));}}
            for(MapDefinition.Spawn s:map.spawns)marker(s,false);for(MapDefinition.Spawn s:map.targets)marker(s,true);
            renderer.render(getWidth(),getHeight(),painted,new HashSet<>(selectedItems()));g.drawImage(renderer.image,0,0,null);
            for(MapDefinition.Spawn s:map.spawns){double[] c=camera(new double[]{s.x,s.y+.8,s.z});if(c[2]>.1){double scale=Math.min(getWidth(),getHeight())*.9/c[2];g.setColor(Color.WHITE);g.drawString("Team "+s.team,(int)(getWidth()/2.0+c[0]*scale),(int)(getHeight()/2.0-c[1]*scale));}}
            gizmo.paint(g);if(boxStart!=null){Rectangle box=boxBounds();g.setColor(new Color(90,180,255,45));g.fill(box);g.setColor(new Color(120,205,255));g.setStroke(new BasicStroke(1));g.draw(box);}g.setColor(Color.WHITE);g.drawString(m.name+(simulation==null?" — EDIT":" — PHYSICS PREVIEW"),14,22);g.dispose();}
        void add(Face f){if(f!=null)painted.add(f);}
        void marker(MapDefinition.Spawn s,boolean target){MapDefinition.Part p=new MapDefinition.Part();p.x=s.x;p.y=s.y+.02;p.z=s.z;p.w=p.d=.8;p.h=target?1.5:.15;int rgb=target?0xd9a66b:s.team==1?0x52db9b:0xb589ed;for(double[][] f:MapMeshes.faces(p))add(face(f,rgb,s));}
    }
    static final class Face {
        final Path2D path;final double depth;final Color color;final Object owner;final double[][] screen;
        Face(Path2D p,double d,Color c,Object o,double[][] vertices){path=p;depth=d;color=c;owner=o;screen=vertices;}
        Face(Path2D p,double d,Color c,Object o){this(p,d,c,o,points(p,d));}
        static double[][] points(Path2D path,double depth){java.util.List<double[]> result=new ArrayList<>();double[] v=new double[6];for(PathIterator i=path.getPathIterator(null);!i.isDone();i.next()){int type=i.currentSegment(v);if(type==PathIterator.SEG_MOVETO||type==PathIterator.SEG_LINETO)result.add(new double[]{v[0],v[1],1/depth});}return result.toArray(new double[0][]);}
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->{JFrame f=new JFrame("Map Studio");MapEditor editor=new MapEditor();f.setContentPane(editor);f.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);f.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(editor.discard())f.dispose();}});f.pack();f.setLocationRelativeTo(null);f.setVisible(true);});}
}
