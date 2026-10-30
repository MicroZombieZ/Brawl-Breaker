import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.DoubleConsumer;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Standalone particle authoring tool using the game's bounded simulation. */
public class ParticleEditor extends JPanel {
    private ParticleEffect effect=ParticleEffect.preset(ParticleEffect.Pattern.SPARKLE);
    private final ParticleSystem system=new ParticleSystem();
    private final Preview preview=new Preview();
    private final JPanel sliders=new Controls();
    private final JLabel status=new JLabel("Create an effect, then save it or assign it to a game event.");
    private final JLabel imageLabel=new JLabel("Built-in shapes; PNG transparency supported");
    private final JComboBox<ParticleEffect.Pattern> presets=new JComboBox<>(ParticleEffect.Pattern.values());
    private final JComboBox<ParticleSystem.Shape> shapes=new JComboBox<>(ParticleSystem.Shape.values());
    private final JComboBox<GameEffect.Kind> events=new JComboBox<>(GameEffect.Kind.values());
    private final JCheckBox loop=new JCheckBox("Loop emission",true),paused=new JCheckBox("Pause");
    private final java.util.List<Runnable> refreshers=new ArrayList<>();
    private boolean refreshing,dirty;
    private Path currentFile;
    private double elapsed;
    private long previous=System.nanoTime();
    private final javax.swing.Timer timer;

    private static final class Controls extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(350,600);}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 18;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(18,r.height-30);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }

    public ParticleEditor() {
        super(new BorderLayout(12,12));setBorder(BorderFactory.createEmptyBorder(16,18,12,18));
        system.quality(ParticleSystem.Quality.HIGH);
        JPanel header=new JPanel(new BorderLayout());
        JLabel title=new JLabel("PARTICLE STUDIO");title.setFont(new Font("SansSerif",Font.BOLD,24));
        header.add(title,BorderLayout.WEST);
        JPanel files=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        button(files,"New",()->{ if(discard()) { effect=ParticleEffect.preset(ParticleEffect.Pattern.SPARKLE);currentFile=null;dirty=false;refresh();restart(); } });
        button(files,"Open effect",this::open);button(files,"Save",()->save(false));button(files,"Save as",()->save(true));
        header.add(files,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        JPanel workspace=new JPanel(new BorderLayout(0,8));workspace.add(preview,BorderLayout.CENTER);
        JPanel playback=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(playback,"Emit burst",()->{system.emit(effect,0,1,0,0);elapsed=0;});
        button(playback,"Restart",this::restart);playback.add(loop);playback.add(paused);
        button(playback,"Reset view",()->{preview.yaw=.65;preview.pitch=.4;preview.distance=10;preview.repaint();});
        workspace.add(playback,BorderLayout.SOUTH);
        sliders.setLayout(new BoxLayout(sliders,BoxLayout.Y_AXIS));
        section("EMISSION");
        row("Preset",presets);presets.addActionListener(e->{ if(!refreshing) {
            java.awt.image.BufferedImage image=effect.image;
            effect=ParticleEffect.preset((ParticleEffect.Pattern)presets.getSelectedItem());effect.image=image;
            dirty=true;refresh();restart();
        }});
        row("Shape",shapes);shapes.addActionListener(e->{if(!refreshing){effect.shape=(ParticleSystem.Shape)shapes.getSelectedItem();dirty=true;}});
        slider("Aggressiveness",10,300,100,"x",()->effect.aggression,v->effect.aggression=v);
        slider("Particles / burst",1,400,1,"",()->effect.count,v->effect.count=(int)v);
        slider("Repeat interval",10,500,100,"s",()->effect.interval,v->effect.interval=v);
        section("DIRECTION & MOTION");
        slider("Speed",0,200,10,"u/s",()->effect.speed,v->effect.speed=v);
        slider("Spawn range",0,500,100,"u",()->effect.range,v->effect.range=v);
        slider("Cone spread",0,180,1,"deg",()->effect.spread,v->effect.spread=v);
        slider("Heading",-180,180,1,"deg",()->effect.heading,v->effect.heading=v);
        slider("Elevation",-90,90,1,"deg",()->effect.elevation,v->effect.elevation=v);
        slider("Gravity",-100,200,10,"u/s2",()->effect.gravity,v->effect.gravity=v);
        section("APPEARANCE");
        slider("Particle size",2,100,100,"u",()->effect.size,v->effect.size=v);
        slider("Lifetime",10,500,100,"s",()->effect.lifetime,v->effect.lifetime=v);
        slider("Growth",-100,200,100,"u/s",()->effect.growth,v->effect.growth=v);
        slider("Spin",0,200,10,"rad/s",()->effect.spin,v->effect.spin=v);
        slider("Opacity",0,100,100,"",()->effect.opacity,v->effect.opacity=v);
        JPanel imageButtons=new JPanel(new FlowLayout(FlowLayout.LEFT,4,4));
        button(imageButtons,"Import image",this::importImage);
        button(imageButtons,"Remove image",()->{effect.image=null;dirty=true;refresh();});
        button(imageButtons,"Color",()->{Color color=JColorChooser.showDialog(this,"Built-in particle color",effect.color);
            if(color!=null){effect.color=color;dirty=true;} });
        sliders.add(imageButtons);imageLabel.setBorder(BorderFactory.createEmptyBorder(4,10,8,0));sliders.add(imageLabel);
        JLabel hint=new JLabel("<html><div style='width:230px'>Images keep their own colors and aspect ratio.<br>Aggressiveness scales count and launch speed.<br>Range sets the emitter radius. Burst spreads in 3D; Ring / Dust use radial directions and elevation.<br>Changes apply to newly emitted particles.</div></html>");
        hint.setBorder(BorderFactory.createEmptyBorder(8,10,12,8));sliders.add(hint);
        section("USE IN GAME");row("Game event",events);
        JPanel install=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(install,"Assign effect",this::install);button(install,"Restore default",this::restore);sliders.add(install);
        JLabel note=new JLabel("<html>Restart the game to load assignments.<br>Copy particle-effects with the game for friends.</html>");
        note.setBorder(BorderFactory.createEmptyBorder(6,10,12,4));sliders.add(note);
        JScrollPane scroll=new JScrollPane(sliders);scroll.setPreferredSize(new Dimension(370,600));
        scroll.getVerticalScrollBar().setUnitIncrement(18);scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,workspace,scroll);split.setResizeWeight(1);split.setBorder(null);
        add(split,BorderLayout.CENTER);status.setBorder(BorderFactory.createEmptyBorder(6,4,0,0));add(status,BorderLayout.SOUTH);
        refresh();restart();
        timer=new javax.swing.Timer(16,e->tick());timer.start();
    }
    private void section(String title) {
        JLabel label=new JLabel(title);label.setFont(new Font("SansSerif",Font.BOLD,12));
        label.setForeground(new Color(103,204,233));label.setBorder(BorderFactory.createEmptyBorder(18,10,8,4));sliders.add(label);
    }
    private void row(String label,JComponent field) {
        JPanel row=new JPanel(new BorderLayout(8,0));row.setBorder(BorderFactory.createEmptyBorder(4,10,4,10));
        row.add(new JLabel(label),BorderLayout.WEST);row.add(field,BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE,38));sliders.add(row);
    }
    private void slider(String name,int min,int max,double scale,String units,java.util.function.DoubleSupplier get,DoubleConsumer set) {
        JPanel panel=new JPanel(new BorderLayout());panel.setBorder(BorderFactory.createEmptyBorder(3,10,3,10));
        JLabel label=new JLabel();JSlider slider=new JSlider(min,max);
        Runnable refresh=()->{slider.setValue((int)Math.round(get.getAsDouble()*scale));
            label.setText(name+"   "+String.format(Locale.ROOT,"%.2f",get.getAsDouble())+" "+units);};
        slider.addChangeListener(e->{if(!refreshing){set.accept(slider.getValue()/scale);dirty=true;refresh.run();}});
        refreshers.add(refresh);panel.add(label,BorderLayout.NORTH);panel.add(slider,BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE,58));sliders.add(panel);
    }
    private static void button(JPanel panel,String text,Runnable action) {
        JButton button=new JButton(text);button.addActionListener(e->action.run());panel.add(button);
    }
    private void refresh() {
        refreshing=true;presets.setSelectedItem(effect.pattern);shapes.setSelectedItem(effect.shape);
        for(Runnable r:refreshers)r.run();
        imageLabel.setText(effect.image==null?"Built-in shapes; PNG transparency supported":
                "Image: "+effect.image.getWidth()+" x "+effect.image.getHeight()+" (embedded when saved)");
        refreshing=false;
    }
    private void restart() {system.clear();system.emit(effect,0,1,0,0);elapsed=0;preview.repaint();}
    private void tick() {
        long now=System.nanoTime();double dt=Math.min(.05,(now-previous)/1e9);previous=now;
        if(!paused.isSelected()) {
            system.update(dt);elapsed+=dt;
            if(loop.isSelected()&&elapsed>=effect.interval){elapsed%=effect.interval;system.emit(effect,0,1,0,0);}
        }
        preview.repaint();
    }
    private JFileChooser chooser() {
        JFileChooser chooser=new JFileChooser(currentFile==null?Paths.get(".").toFile():currentFile.toAbsolutePath().getParent().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Particle effect (*.pfx)","pfx"));return chooser;
    }
    private boolean discard() {
        if(!dirty)return true;
        int choice=JOptionPane.showConfirmDialog(this,"Save your effect before continuing?","Unsaved effect",JOptionPane.YES_NO_CANCEL_OPTION);
        return choice==JOptionPane.NO_OPTION || choice==JOptionPane.YES_OPTION&&save(false);
    }
    private void open() {
        if(!discard())return;JFileChooser c=chooser();if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{ParticleEffect loaded=ParticleEffect.load(c.getSelectedFile().toPath());effect=loaded;currentFile=c.getSelectedFile().toPath();
            dirty=false;refresh();restart();status.setText("Opened "+currentFile.getFileName());}catch(Exception ex){error(ex);}
    }
    private boolean save(boolean saveAs) {
        Path path=currentFile;
        if(saveAs||path==null){JFileChooser c=chooser();if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return false;
            path=c.getSelectedFile().toPath();if(!path.toString().toLowerCase(Locale.ROOT).endsWith(".pfx"))path=Paths.get(path+".pfx");
            if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace "+path.getFileName()+"?","Save effect",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return false;
        }
        try{effect.save(path);currentFile=path;dirty=false;status.setText("Saved "+path.getFileName()+" with its image.");return true;}
        catch(Exception ex){error(ex);return false;}
    }
    private void importImage() {
        JFileChooser c=new JFileChooser();c.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPEG, GIF first frame)","png","jpg","jpeg","gif"));
        if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{effect.image=ParticleEffect.readImage(c.getSelectedFile());dirty=true;refresh();restart();status.setText("Image imported. Transparent PNGs work best.");}
        catch(Exception ex){error(ex);}
    }
    private Path assignment() {return Paths.get("particle-effects",((GameEffect.Kind)events.getSelectedItem()).name()+".pfx");}
    private void install() {
        try{effect.save(assignment());status.setText("Assigned to "+events.getSelectedItem()+". Restart the game to load it.");}
        catch(Exception ex){error(ex);}
    }
    private void restore() {
        try{Files.deleteIfExists(assignment());status.setText("Restored default "+events.getSelectedItem()+" effect. Restart the game.");}
        catch(Exception ex){error(ex);}
    }
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Particle Studio",JOptionPane.ERROR_MESSAGE);}
    void stop(){timer.stop();}

    private final class Preview extends JPanel {
        double yaw=.65,pitch=.4,distance=10;Point drag;
        Preview(){
            setPreferredSize(new Dimension(700,600));setBackground(new Color(17,23,32));
            MouseAdapter mouse=new MouseAdapter(){
                public void mousePressed(MouseEvent e){drag=e.getPoint();}
                public void mouseDragged(MouseEvent e){if(drag!=null){yaw+=(e.getX()-drag.x)*.01;pitch=Math.max(.05,Math.min(1.35,pitch+(e.getY()-drag.y)*.008));drag=e.getPoint();repaint();}}
                public void mouseReleased(MouseEvent e){drag=null;}
                public void mouseWheelMoved(MouseWheelEvent e){distance=Math.max(3,Math.min(30,distance+e.getPreciseWheelRotation()*.6));repaint();}
            };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);
        }
        double[] project(double x,double y,double z){
            double rx=x*Math.cos(yaw)-z*Math.sin(yaw),forward=x*Math.sin(yaw)+z*Math.cos(yaw);
            double up=(y-1)*Math.cos(pitch)-forward*Math.sin(pitch),depth=distance-forward*Math.cos(pitch)-(y-1)*Math.sin(pitch);
            double scale=Math.min(getWidth(),getHeight())*.95/Math.max(.1,depth);
            return new double[]{getWidth()*.5+rx*scale,getHeight()*.52-up*scale,scale,depth};
        }
        void line(Graphics2D g,double ax,double ay,double az,double bx,double by,double bz){
            double[] a=project(ax,ay,az),b=project(bx,by,bz);if(a[3]>.1&&b[3]>.1)g.draw(new Line2D.Double(a[0],a[1],b[0],b[1]));
        }
        @Override protected void paintComponent(Graphics graphics){
            super.paintComponent(graphics);Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(38,51,65));for(int i=-8;i<=8;i++){line(g,i,0,-8,i,0,8);line(g,-8,0,i,8,0,i);}
            g.setColor(new Color(181,89,101));line(g,0,0,0,2,0,0);
            g.setColor(new Color(98,180,146));line(g,0,0,0,0,2,0);
            g.setColor(new Color(85,157,220));line(g,0,0,0,0,0,-2);
            double heading=Math.toRadians(effect.heading),elevation=Math.toRadians(effect.elevation);
            g.setColor(new Color(255,198,91));line(g,0,1,0,-Math.sin(heading)*Math.cos(elevation)*2,
                    1+Math.sin(elevation)*2,-Math.cos(heading)*Math.cos(elevation)*2);
            ArrayList<ParticleSystem.Particle> sorted=new ArrayList<>(system.particles);
            sorted.sort(Comparator.comparingDouble(p->-project(p.x,p.y,p.z)[3]));
            for(ParticleSystem.Particle p:sorted){
                if(p.delay>0)continue;double[] point=project(p.x,p.y,p.z);if(point[3]<.1)continue;
                float alpha=(float)ParticleSystem.alpha(p);if(alpha<.005)continue;
                double radius=Math.max(.3,p.size*point[2]);
                if(p.image!=null){
                    double[][] corners=new double[4][2];double aspect=(double)p.image.getWidth()/p.image.getHeight();
                    for(int i=0;i<4;i++){double cx=(i==0||i==3?-1:1)*radius*aspect,cy=(i<2?-1:1)*radius;
                        corners[i]=new double[]{point[0]+cx*Math.cos(p.angle)-cy*Math.sin(p.angle),point[1]+cx*Math.sin(p.angle)+cy*Math.cos(p.angle)};}
                    ParticleSprites.draw(g,p.image,corners,alpha*220/255);continue;
                }
                Color color=new Color(p.color.getRed(),p.color.getGreen(),p.color.getBlue(),Math.min(255,(int)(alpha*(p.shape==ParticleSystem.Shape.DUST?90:220))));g.setColor(color);
                if(p.shape==ParticleSystem.Shape.RING){
                    Path2D path=new Path2D.Double();for(int i=0;i<=64;i++){double a=i*Math.PI*2/64;double[] v=project(p.x+Math.cos(a)*p.size,p.y,p.z+Math.sin(a)*p.size);if(i==0)path.moveTo(v[0],v[1]);else path.lineTo(v[0],v[1]);}g.draw(path);
                }else if(p.shape==ParticleSystem.Shape.DUST){
                    g.setPaint(new RadialGradientPaint(new Point2D.Double(point[0],point[1]),(float)radius,new float[]{0,1},new Color[]{color,new Color(color.getRed(),color.getGreen(),color.getBlue(),0)}));
                    g.fill(new Ellipse2D.Double(point[0]-radius,point[1]-radius,radius*2,radius*2));
                }else if(p.shape==ParticleSystem.Shape.SPARK){
                    double[] tail=project(p.x-p.vx*.06,p.y-p.vy*.06-.08,p.z-p.vz*.06);g.setStroke(new BasicStroke((float)Math.max(1,radius*.6),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));g.draw(new Line2D.Double(point[0],point[1],tail[0],tail[1]));g.setStroke(new BasicStroke(1));
                }else if(p.shape==ParticleSystem.Shape.CUBE){
                    double[][] vertices={{-.5,-.5,-.5},{.5,-.5,-.5},{.5,-.5,.5},{-.5,-.5,.5},
                            {-.5,.5,-.5},{.5,.5,-.5},{.5,.5,.5},{-.5,.5,.5}};
                    for(double[] v:vertices){double vx=v[0]*p.size,vz=v[2]*p.size;
                        v[0]=p.x+Math.cos(p.angle)*vx+Math.sin(p.angle)*vz;v[1]=p.y+v[1]*p.size;
                        v[2]=p.z-Math.sin(p.angle)*vx+Math.cos(p.angle)*vz;}
                    Integer[] order={0,1,2,3,4,5};int[][] faces={{0,1,5,4},{1,2,6,5},{2,3,7,6},{3,0,4,7},{4,5,6,7},{0,3,2,1}};
                    Arrays.sort(order,Comparator.comparingDouble(f->{double depth=0;for(int i:faces[f])depth+=project(vertices[i][0],vertices[i][1],vertices[i][2])[3];return -depth;}));
                    for(int f:order){Path2D path=new Path2D.Double();boolean visible=true;
                        for(int i=0;i<4;i++){double[] v=vertices[faces[f][i]],screen=project(v[0],v[1],v[2]);
                            if(screen[3]<.1){visible=false;break;}if(i==0)path.moveTo(screen[0],screen[1]);else path.lineTo(screen[0],screen[1]);}
                        if(visible){path.closePath();double shade=new double[]{.68,.8,.9,.74,1,.5}[f];
                            g.setColor(new Color((int)(color.getRed()*shade),(int)(color.getGreen()*shade),(int)(color.getBlue()*shade),color.getAlpha()));g.fill(path);}}
                }else{
                    int n=p.shape==ParticleSystem.Shape.SHARD?3:4;Path2D path=new Path2D.Double();
                    for(int i=0;i<n;i++){double a=p.angle+i*Math.PI*2/n,px=point[0]+Math.cos(a)*radius,py=point[1]+Math.sin(a)*radius;if(i==0)path.moveTo(px,py);else path.lineTo(px,py);}path.closePath();g.fill(path);
                }
            }
            g.setColor(new Color(162,182,203));g.setFont(new Font("SansSerif",Font.PLAIN,12));
            g.drawString("Drag to orbit  /  Scroll to zoom",18,26);
            g.drawString(system.particles.size()+" / 800 particles  |  "+(paused.isSelected()?"Paused":effect.pattern)+"  |  emitter at Y = 1",18,getHeight()-18);
            g.dispose();
        }
    }
    static void theme(){
        UIManager.put("Panel.background",new Color(29,37,49));UIManager.put("Label.foreground",new Color(222,233,243));
        UIManager.put("CheckBox.background",new Color(29,37,49));UIManager.put("CheckBox.foreground",new Color(222,233,243));
        UIManager.put("Slider.background",new Color(29,37,49));
        UIManager.put("Button.background",new Color(44,61,79));UIManager.put("Button.foreground",new Color(232,242,250));
        Color button=new Color(44,61,79);UIManager.put("Button.gradient",Arrays.asList(0f,0f,button,button,button));
        UIManager.put("ComboBox.background",new Color(44,61,79));UIManager.put("ComboBox.foreground",new Color(232,242,250));
        UIManager.put("ScrollBar.background",new Color(29,37,49));UIManager.put("ScrollBar.thumb",new Color(70,87,104));
        UIManager.put("OptionPane.background",new Color(29,37,49));
        UIManager.put("OptionPane.messageForeground",new Color(222,233,243));
        for(String key:new String[]{"Label.font","Button.font","CheckBox.font","ComboBox.font"})
            UIManager.put(key,new Font("SansSerif",Font.PLAIN,12));
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->{
        theme();
        JFrame frame=new JFrame("Particle Studio");ParticleEditor editor=new ParticleEditor();frame.setContentPane(editor);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(editor.discard()){editor.stop();frame.dispose();}}});
        frame.setMinimumSize(new Dimension(960,660));frame.setSize(1220,820);frame.setLocationRelativeTo(null);frame.setVisible(true);
    });}
}
