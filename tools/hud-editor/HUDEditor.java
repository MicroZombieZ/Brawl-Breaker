import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Live HUD placement tool. Artwork stays linked to health, stamina and other game data. */
public class HUDEditor extends JPanel {
    enum Scope { WHOLE_PIECE, SELECTED_LAYER;
        @Override public String toString(){return this==WHOLE_PIECE?"Whole HUD piece":"Selected artwork layer";}}
    HUDLayout layout=new HUDLayout();final UISkins skins=new UISkins();
    private final Preview preview=new Preview();
    private final JComboBox<HUDLayout.Piece> pieces=new JComboBox<>(HUDLayout.Piece.values());
    private final JComboBox<UISkins.Slot> slots=new JComboBox<>();
    private final JComboBox<Scope> scope=new JComboBox<>(Scope.values());
    private final JLabel xTitle=new JLabel("X position"),yTitle=new JLabel("Y position");
    private final JSpinner x=number(22,-2000,3000,1),y=number(598,-1400,2100,1),
            sx=number(1,.1,5,.05),sy=number(1,.1,5,.05),rotation=number(0,-180,180,1),
            opacity=number(1,0,1,.05),font=number(1,.5,3,.1);
    private final JCheckBox visible=new JCheckBox("Show this HUD piece",true),text=new JCheckBox("Show live text",true),
            defaults=new JCheckBox("Built-in art for empty slots",true),locked=new JCheckBox("Lock width/height scale",true),
            showDebug=new JCheckBox("Preview debug HUD"),showLan=new JCheckBox("Preview LAN status",true);
    private final JSlider health=new JSlider(0,100,75),stamina=new JSlider(0,100,60);
    private final HealthFillGhost healthFillGhost=new HealthFillGhost();
    private long previewPrevious;
    private final javax.swing.Timer ghostTimer=new javax.swing.Timer(16,e->{long now=System.nanoTime();double dt=(now-previewPrevious)/1e9;previewPrevious=now;
        healthFillGhost.update(dt,health.getValue(),100);preview.repaint();});
    private final JLabel status=new JLabel("Select a linked HUD piece; import artwork, drag to move, and adjust scale/rotation. Save & Apply, then restart the game.");
    private final JLabel artwork=new JLabel();
    private final JButton undo=new JButton("Undo"),redo=new JButton("Redo");
    private final EnumSet<UISkins.Slot> pending=EnumSet.noneOf(UISkins.Slot.class);
    private final Deque<State> history=new ArrayDeque<>(),future=new ArrayDeque<>();
    private boolean refreshing,dirty;
    private static final Path DIRECTORY=Paths.get("ui-assets"),LAYOUT=DIRECTORY.resolve("hud-layout.properties");
    private static final class State {
        byte[] layout;Map<UISkins.Slot,BufferedImage> images;EnumSet<UISkins.Slot> pending;
    }
    private static final class Inspector extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(310,700);}
        public int getScrollableUnitIncrement(Rectangle r,int o,int d){return 18;}public int getScrollableBlockIncrement(Rectangle r,int o,int d){return 150;}
        public boolean getScrollableTracksViewportWidth(){return true;}public boolean getScrollableTracksViewportHeight(){return false;}
    }
    public HUDEditor(){this(true);}
    HUDEditor(boolean loadFromGame){
        super(new BorderLayout(10,10));setBorder(BorderFactory.createEmptyBorder(14,14,12,14));
        if(loadFromGame){layout=HUDLayout.load(LAYOUT);skins.load(DIRECTORY);}
        JPanel header=new JPanel(new BorderLayout());JLabel title=new JLabel("HUD STUDIO");title.setFont(new Font("SansSerif",Font.BOLD,24));header.add(title,BorderLayout.WEST);
        JPanel files=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));button(files,"Open layout",this::openLayout);button(files,"Export layout",this::exportLayout);
        button(files,"Save & Apply",this::saveApply);button(files,"Reload from game",()->{if(discard()){layout=HUDLayout.load(LAYOUT);skins.load(DIRECTORY);pending.clear();history.clear();future.clear();dirty=false;refresh();}});
        header.add(files,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        Inspector controls=new Inspector();controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));
        controls.add(new JLabel("LINKED HUD PIECE"));controls.add(pieces);
        pieces.addActionListener(e->{if(!refreshing){refreshSlots();refresh();}});
        controls.add(Box.createVerticalStrut(12));controls.add(new JLabel("ARTWORK LAYER"));controls.add(slots);controls.add(artwork);
        slots.addActionListener(e->{if(!refreshing){scope.setSelectedItem(Scope.SELECTED_LAYER);refresh();}});
        JPanel imageButtons=new JPanel(new GridLayout(1,2,4,0));button(imageButtons,"Upload image",this::upload);
        button(imageButtons,"Remove image",()->{UISkins.Slot slot=slot();if(skins.images.containsKey(slot))mutate(()->{skins.images.remove(slot);pending.add(slot);});});controls.add(imageButtons);
        controls.add(Box.createVerticalStrut(12));controls.add(new JLabel("EDIT TARGET"));controls.add(scope);scope.addActionListener(e->{if(!refreshing)refresh();});
        row(controls,xTitle,x);row(controls,yTitle,y);
        row(controls,"Width scale",sx);row(controls,"Height scale",sy);controls.add(locked);row(controls,"Rotation (degrees)",rotation);
        row(controls,"Opacity",opacity);row(controls,"Text size scale",font);controls.add(visible);controls.add(text);controls.add(defaults);
        JPanel colorButtons=new JPanel(new GridLayout(1,2,4,0));button(colorButtons,"Text color",this::color);
        button(colorButtons,"Default color",()->mutate(()->placement().color=null));controls.add(colorButtons);
        button(controls,"Reset selection",()->mutate(()->{if(layerScope())layout.layers.put(slot(),HUDLayout.newLayer(slot()));else layout.placements.put(piece(),new HUDLayout.Placement(piece()));}));
        JPanel edits=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));edits.add(undo);edits.add(redo);button(edits,"Reset view",()->{preview.panX=preview.panY=0;preview.zoom=1;preview.repaint();});controls.add(edits);
        undo.addActionListener(e->travel(false));redo.addActionListener(e->travel(true));
        controls.add(Box.createVerticalStrut(16));controls.add(new JLabel("PREVIEW LIVE VALUES"));controls.add(new JLabel("Health %"));controls.add(health);
        controls.add(new JLabel("Stamina %"));controls.add(stamina);controls.add(showLan);controls.add(showDebug);
        controls.add(new JLabel("<html><div style='width:205px'>Edit target chooses the whole piece or one artwork layer. Select a frame, fill, background, or label to edit it independently. Whole-piece edits keep all layer adjustments.<br><br>Layer X/Y are offsets inside the piece; drag follows its rotation and scale.<br><br>Middle-drag pans; scrolling zooms the preview.<br><br>Save &amp; Apply writes the layout and changed artwork to ui-assets. Restart the game to load it.</div></html>"));
        for(Component c:controls.getComponents())if(c instanceof JComponent){JComponent component=(JComponent)c;component.setAlignmentX(LEFT_ALIGNMENT);component.setMaximumSize(new Dimension(Integer.MAX_VALUE,component.getPreferredSize().height+5));}
        JScrollPane inspector=new JScrollPane(controls);inspector.setPreferredSize(new Dimension(335,700));inspector.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);inspector.getVerticalScrollBar().setUnitIncrement(18);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,preview,inspector);split.setResizeWeight(1);split.setBorder(null);add(split,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);
        x.addChangeListener(e->{if(!refreshing)mutate(()->{placement().x=value(x);placement().positioned=true;});});
        y.addChangeListener(e->{if(!refreshing)mutate(()->{placement().y=value(y);placement().positioned=true;});});
        sx.addChangeListener(e->{if(!refreshing)mutate(()->{placement().scaleX=value(sx);if(locked.isSelected())placement().scaleY=value(sx);});});
        sy.addChangeListener(e->{if(!refreshing)mutate(()->{placement().scaleY=value(sy);if(locked.isSelected())placement().scaleX=value(sy);});});
        rotation.addChangeListener(e->{if(!refreshing)mutate(()->placement().rotation=value(rotation));});
        opacity.addChangeListener(e->{if(!refreshing)mutate(()->placement().opacity=value(opacity));});font.addChangeListener(e->{if(!refreshing)mutate(()->placement().fontScale=value(font));});
        visible.addActionListener(e->{if(!refreshing)mutate(()->placement().visible=visible.isSelected());});text.addActionListener(e->{if(!refreshing)mutate(()->placement().text=text.isSelected());});
        defaults.addActionListener(e->{if(!refreshing)mutate(()->placement().defaults=defaults.isSelected());});
        health.addChangeListener(e->{healthFillGhost.update(0,health.getValue(),100);preview.repaint();});stamina.addChangeListener(e->preview.repaint());showDebug.addActionListener(e->preview.repaint());showLan.addActionListener(e->preview.repaint());
        bind("control Z","undo",()->travel(false));bind("control Y","redo",()->travel(true));bind("control shift Z","redo2",()->travel(true));bind("control S","save",this::saveApply);
        healthFillGhost.reset(health.getValue(),100);refreshSlots();refresh();
    }
    @Override public void addNotify(){super.addNotify();previewPrevious=System.nanoTime();ghostTimer.start();}
    @Override public void removeNotify(){ghostTimer.stop();super.removeNotify();}
    private static JSpinner number(double initial,double min,double max,double step){return new JSpinner(new SpinnerNumberModel(initial,min,max,step));}
    private static double value(JSpinner spinner){return ((Number)spinner.getValue()).doubleValue();}
    private static void button(JPanel panel,String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->action.run());panel.add(b);}
    private static void row(JPanel panel,String title,JComponent field){JPanel row=new JPanel(new BorderLayout(8,0));row.add(new JLabel(title),BorderLayout.WEST);row.add(field,BorderLayout.CENTER);panel.add(row);}
    private static void row(JPanel panel,JLabel title,JComponent field){JPanel row=new JPanel(new BorderLayout(8,0));row.add(title,BorderLayout.WEST);row.add(field,BorderLayout.CENTER);panel.add(row);}
    private HUDLayout.Piece piece(){return (HUDLayout.Piece)pieces.getSelectedItem();}private UISkins.Slot slot(){return (UISkins.Slot)slots.getSelectedItem();}
    private boolean layerScope(){return scope.getSelectedItem()==Scope.SELECTED_LAYER;}
    private HUDLayout.Placement placement(){return layerScope()?layout.get(slot()):layout.get(piece());}
    private void refreshSlots(){refreshing=true;slots.removeAllItems();for(UISkins.Slot s:UISkins.Slot.values())if(s.piece()==piece())slots.addItem(s);refreshing=false;}
    private void refresh(){refreshing=true;HUDLayout.Placement p=placement();x.setValue(p.x);y.setValue(p.y);sx.setValue(p.scaleX);sy.setValue(p.scaleY);rotation.setValue(p.rotation);opacity.setValue(p.opacity);font.setValue(p.fontScale);
        visible.setSelected(p.visible);text.setSelected(p.text);defaults.setSelected(p.defaults);
        visible.setText(layerScope()?"Show this artwork layer":"Show this HUD piece");xTitle.setText(layerScope()?"X offset":"X position");yTitle.setText(layerScope()?"Y offset":"Y position");
        BufferedImage image=skins.images.get(slot());artwork.setText(image==null?"No uploaded image":image.getWidth()+" x "+image.getHeight()+" image assigned");
        undo.setEnabled(!history.isEmpty());redo.setEnabled(!future.isEmpty());refreshing=false;preview.repaint();}
    private State snapshot() throws IOException {State s=new State();s.layout=layout.encode();s.images=new EnumMap<>(skins.images);s.pending=pending.clone();return s;}
    private void remember(State s){history.addLast(s);while(history.size()>40)history.removeFirst();future.clear();}
    private void restore(State s) throws IOException {layout=HUDLayout.decode(s.layout);skins.images.clear();skins.images.putAll(s.images);pending.clear();pending.addAll(s.pending);}
    private void mutate(Runnable action){try{State before=snapshot();action.run();remember(before);dirty=true;refresh();}catch(Exception ex){error(ex);}}
    private void travel(boolean forward){Deque<State> from=forward?future:history,to=forward?history:future;if(from.isEmpty()||preview.drag!=null)return;
        try{State before=snapshot();restore(from.removeLast());to.addLast(before);dirty=true;refresh();}catch(Exception ex){error(ex);}}
    private void bind(String key,String name,Runnable action){getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),name);getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    private void upload(){JFileChooser c=new JFileChooser("ui-assets");c.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPEG, GIF first frame)","png","jpg","jpeg","gif"));
        if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{BufferedImage image=ParticleEffect.readImage(c.getSelectedFile());UISkins.Slot slot=slot();mutate(()->{skins.images.put(slot,image);pending.add(slot);});}catch(Exception ex){error(ex);}}
    private void color(){Color original=placement().color;ParticleColorEditor picker=new ParticleColorEditor(original==null?Color.WHITE:original,c->{});
        if(JOptionPane.showOptionDialog(this,picker,"Live HUD text color",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Apply","Cancel"},"Apply")==0){Color color=picker.chooser.getColor();ParticleColorEditor.remember(color);mutate(()->placement().color=color);}}
    private void saveApply(){try{
        Files.createDirectories(DIRECTORY);
        for(UISkins.Slot slot:pending){Path target=DIRECTORY.resolve(slot+".png");BufferedImage image=skins.images.get(slot);
            if(image==null){Files.deleteIfExists(target);continue;}
            Path temp=Files.createTempFile(DIRECTORY,"hud-image-",".tmp");try{ImageIO.write(image,"png",temp.toFile());Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}}
        layout.save(LAYOUT);pending.clear();history.clear();future.clear();dirty=false;refresh();status.setText("Saved HUD and artwork. Restart the game to load your layout.");
    }catch(Exception ex){error(ex);}}
    private boolean discard(){if(!dirty)return true;int result=JOptionPane.showConfirmDialog(this,"Save & Apply your HUD changes before continuing?","Unsaved HUD",JOptionPane.YES_NO_CANCEL_OPTION);
        if(result==JOptionPane.NO_OPTION)return true;if(result==JOptionPane.YES_OPTION){saveApply();return !dirty;}return false;}
    private void openLayout(){if(!discard())return;JFileChooser c=new JFileChooser("ui-assets");c.setFileFilter(new FileNameExtensionFilter("HUD layout (*.properties)","properties"));
        if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{Path path=c.getSelectedFile().toPath();if(Files.size(path)>65536)throw new IOException("HUD layout is too large.");HUDLayout loaded=HUDLayout.decode(Files.readAllBytes(path));mutate(()->layout=loaded);status.setText("Layout opened. Existing artwork is retained; Save & Apply to use in game.");}catch(Exception ex){error(ex);}}
    private void exportLayout(){JFileChooser c=new JFileChooser("ui-assets");c.setSelectedFile(new File("my-hud.properties"));if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path path=c.getSelectedFile().toPath();if(Files.exists(path)&&JOptionPane.showConfirmDialog(this,"Replace "+path.getFileName()+"?","Export layout",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;
        try{layout.save(path);status.setText("Exported layout; copy its PNG artwork too when sharing.");}catch(Exception ex){error(ex);}}
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"HUD Studio",JOptionPane.ERROR_MESSAGE);}
    private final class Preview extends JComponent {
        double zoom=1,panX,panY;Point drag;boolean panning;Point2D.Double dragWorld;double dragX,dragY;State before;boolean moved;UISkins.Slot hitSlot;
        Preview(){setPreferredSize(new Dimension(850,650));setFocusable(true);MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){requestFocusInWindow();if(drag!=null)return;
                if(SwingUtilities.isMiddleMouseButton(e)){drag=e.getPoint();panning=true;setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));return;}
                if(!SwingUtilities.isLeftMouseButton(e))return;Point2D.Double point=world(e.getX(),e.getY());HUDLayout.Piece hit=hit(point);if(hit==null)return;
                UISkins.Slot selected=hitSlot;pieces.setSelectedItem(hit);if(layerScope()&&selected!=null){refreshing=true;slots.setSelectedItem(selected);refreshing=false;refresh();}
                drag=e.getPoint();panning=false;dragWorld=dragPoint(point);dragX=placement().x;dragY=placement().y;moved=false;
                try{before=snapshot();}catch(IOException ex){drag=null;error(ex);}}
            public void mouseDragged(MouseEvent e){if(drag==null)return;
                if(panning){panX+=e.getX()-drag.x;panY+=e.getY()-drag.y;drag=e.getPoint();repaint();return;}
                Point2D.Double point=dragPoint(world(e.getX(),e.getY()));HUDLayout.Placement p=placement();
                if(point.distance(dragWorld)<.01)return;p.x=Math.max(-2000,Math.min(3000,dragX+point.x-dragWorld.x));p.y=Math.max(-1400,Math.min(2100,dragY+point.y-dragWorld.y));p.positioned=true;moved=true;refresh();}
            public void mouseReleased(MouseEvent e){if(drag==null)return;if(panning&&!SwingUtilities.isMiddleMouseButton(e)||!panning&&!SwingUtilities.isLeftMouseButton(e))return;
                if(!panning&&moved){remember(before);dirty=true;}drag=null;before=null;panning=false;setCursor(Cursor.getDefaultCursor());refresh();}
            public void mouseWheelMoved(MouseWheelEvent e){e.consume();if(drag!=null)return;Point2D.Double point=world(e.getX(),e.getY());zoom=Math.max(.4,Math.min(4,zoom*Math.pow(1.12,-e.getPreciseWheelRotation())));
                panX=e.getX()-getWidth()/2.0-(point.x-500)*scale();panY=e.getY()-getHeight()/2.0-(point.y-350)*scale();repaint();}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        double scale(){return Math.max(.01,Math.min(getWidth()/1000.0,getHeight()/700.0)*.94*zoom);}
        double ox(){return getWidth()/2.0-500*scale()+panX;}double oy(){return getHeight()/2.0-350*scale()+panY;}
        Point2D.Double world(double x,double y){return new Point2D.Double((x-ox())/scale(),(y-oy())/scale());}
        Point2D.Double dragPoint(Point2D.Double point){if(!layerScope())return point;try{Point2D.Double local=new Point2D.Double();layout.transform(piece(),1000,700).inverseTransform(point,local);return local;}catch(NoninvertibleTransformException ex){throw new IllegalStateException(ex);}}
        boolean contains(UISkins.Slot slot,Point2D.Double point){try{return new Rectangle2D.Double(0,0,slot.width,slot.height).contains(layout.transform(slot,1000,700).inverseTransform(point,null));}catch(NoninvertibleTransformException ex){return false;}}
        HUDLayout.Piece hit(Point2D.Double point){hitSlot=null;
            if(layerScope()&&contains(slot(),point)){hitSlot=slot();return piece();}
            HUDLayout.Piece[] values=HUDLayout.Piece.values();for(int i=values.length-1;i>=0;i--){HUDLayout.Piece piece=values[i];
            if(piece==HUDLayout.Piece.DEBUG&&!showDebug.isSelected()||piece==HUDLayout.Piece.LAN&&!showLan.isSelected())continue;
            if(!layout.get(piece).visible&&piece!=piece())continue;
            if(layerScope()){UISkins.Slot[] layers=HUDLayout.order(piece);for(int j=layers.length-1;j>=0;j--)if(layout.get(layers[j]).visible&&contains(layers[j],point)){hitSlot=layers[j];return piece;}}
            else try{Point2D local=layout.transform(piece,1000,700).inverseTransform(point,null);if(layout.bounds(piece).contains(local))return piece;}catch(NoninvertibleTransformException ignored){}}
            return null;}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();g.setColor(new Color(14,20,29));g.fillRect(0,0,getWidth(),getHeight());g.translate(ox(),oy());g.scale(scale(),scale());
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setPaint(new GradientPaint(0,0,new Color(40,61,79),0,700,new Color(23,32,41)));g.fillRect(0,0,1000,700);
            g.setColor(new Color(49,65,78));for(int i=0;i<1000;i+=50)g.drawLine(i,0,i,700);for(int j=0;j<700;j+=50)g.drawLine(0,j,1000,j);
            HUDRenderer.Data data=new HUDRenderer.Data();data.health=health.getValue()/100.0*data.maxHealth;data.stamina=stamina.getValue();data.lan=showLan.isSelected();data.debug=showDebug.isSelected();data.cooldowns[1]=12.5;
            data.healthGhost=healthFillGhost.fraction();
            new HUDRenderer(skins,layout).draw(g,1000,700,data);
            g.setColor(new Color(104,217,245));g.setStroke(new BasicStroke((float)(1.5/scale())));
            Shape outline=layerScope()?layout.transform(slot(),1000,700).createTransformedShape(new Rectangle2D.Double(0,0,slot().width,slot().height)):layout.transform(piece(),1000,700).createTransformedShape(layout.bounds(piece()));g.draw(outline);
            g.dispose();}
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->{ParticleEditor.theme();JFrame frame=new JFrame("HUD Studio");HUDEditor editor=new HUDEditor();frame.setContentPane(editor);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(editor.discard())frame.dispose();}});
        frame.setSize(1300,860);frame.setMinimumSize(new Dimension(1050,700));frame.setLocationRelativeTo(null);frame.setVisible(true);});}
}
