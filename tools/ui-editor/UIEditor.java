import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Layered raster UI authoring and local HUD skin assignment. */
public class UIEditor extends JPanel {
    enum Tool { BRUSH, ERASER, FILL, RECTANGLE, ELLIPSE, LINE, MOVE_LAYER, TEXT, EYEDROPPER }
    UIDrawing drawing=new UIDrawing(512,256);
    private final Canvas canvas=new Canvas();
    private final DefaultListModel<UIDrawing.Layer> layerModel=new DefaultListModel<>();
    private final JList<UIDrawing.Layer> layerList=new JList<>(layerModel);
    private final JComboBox<Tool> tools=new JComboBox<>(Tool.values());
    private final JComboBox<UISkins.Slot> slots=new JComboBox<>(UISkins.Slot.values());
    private final JSpinner brush=new JSpinner(new SpinnerNumberModel(8,1,128,1));
    private final JSlider alpha=new JSlider(0,100,100),layerAlpha=new JSlider(0,100,100),zoom=new JSlider(25,400,100);
    private final JCheckBox filled=new JCheckBox("Fill shapes",true),visible=new JCheckBox("Layer visible",true);
    private final JLabel colorLabel=new JLabel("#FFC65B"),status=new JLabel("Draw on a transparent canvas. Save layers as .uidraw; export or assign PNG artwork.");
    private final JButton undo=new JButton("Undo"),redo=new JButton("Redo");
    private Color color=new Color(255,198,91);
    private boolean refreshing,dirty,opacityGesture;
    private Path currentFile;
    private static final class Sidebar extends JPanel implements Scrollable {
        public Dimension getPreferredScrollableViewportSize(){return new Dimension(260,650);}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 18;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return Math.max(18,r.height-30);}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return false;}
    }

    public UIEditor(){
        super(new BorderLayout(10,10));setBorder(BorderFactory.createEmptyBorder(14,14,12,14));
        JPanel header=new JPanel(new BorderLayout());JLabel title=new JLabel("UI STUDIO");title.setFont(new Font("SansSerif",Font.BOLD,24));header.add(title,BorderLayout.WEST);
        JPanel files=new JPanel(new FlowLayout(FlowLayout.RIGHT,4,0));
        button(files,"New",this::newDrawing);button(files,"Open",this::open);button(files,"Save",()->save(false));button(files,"Save as",()->save(true));
        button(files,"Import image",this::importImage);button(files,"Export PNG",this::exportPNG);header.add(files,BorderLayout.EAST);add(header,BorderLayout.NORTH);
        JPanel left=new JPanel();left.setLayout(new BoxLayout(left,BoxLayout.Y_AXIS));left.setPreferredSize(new Dimension(185,550));
        left.add(new JLabel("DRAWING TOOL"));left.add(tools);left.add(Box.createVerticalStrut(12));left.add(new JLabel("Brush / outline / text size"));left.add(brush);
        left.add(filled);left.add(new JLabel("Paint opacity"));left.add(alpha);
        button(left,"Edit color",this::editColor);colorLabel.setOpaque(true);left.add(colorLabel);
        left.add(Box.createVerticalStrut(12));left.add(new JLabel("Zoom 25-400%"));left.add(zoom);
        button(left,"Reset view",()->canvas.resetView());
        JPanel history=new JPanel(new FlowLayout(FlowLayout.LEFT,3,0));history.add(undo);history.add(redo);left.add(history);
        undo.addActionListener(e->history(false));redo.addActionListener(e->history(true));
        JLabel hint=new JLabel("<html><div style='width:115px'>Middle-drag: pan<br>Scroll: zoom at cursor<br><br>Drag to paint or draw a shape.<br><br>Hold Shift for squares and circles.<br><br>Move Layer drags the selected layer.<br><br>Ctrl+Z: undo<br>Ctrl+Y / Ctrl+Shift+Z: redo<br>Ctrl+S: save</div></html>");left.add(hint);left.add(Box.createVerticalGlue());
        for(Component c:left.getComponents())if(c instanceof JComponent){((JComponent)c).setAlignmentX(LEFT_ALIGNMENT);((JComponent)c).setMaximumSize(new Dimension(185,c.getPreferredSize().height+6));}
        JScrollPane view=new JScrollPane(canvas);view.getViewport().setBackground(new Color(16,22,30));
        view.setWheelScrollingEnabled(false);
        JPanel right=new Sidebar();right.setLayout(new BoxLayout(right,BoxLayout.Y_AXIS));
        right.add(new JLabel("LAYERS (top is drawn last)"));layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.setBackground(new Color(24,32,43));layerList.setForeground(new Color(223,233,243));
        JScrollPane layerScroll=new JScrollPane(layerList);layerScroll.setPreferredSize(new Dimension(250,220));right.add(layerScroll);
        layerList.addListSelectionListener(e->{if(!refreshing&&!e.getValueIsAdjusting()&&layerList.getSelectedIndex()>=0){drawing.selected=drawing.layers.size()-1-layerList.getSelectedIndex();refresh();}});
        JPanel manage=new JPanel(new GridLayout(2,3,3,3));
        button(manage,"Add",()->edit(()->drawing.addLayer("Layer "+(drawing.layers.size()+1))));button(manage,"Copy",()->edit(drawing::duplicate));
        button(manage,"Delete",()->edit(()->{if(drawing.layers.size()==1)throw new java.io.IOException("Keep at least one layer.");drawing.checkpoint();drawing.layers.remove(drawing.selected);drawing.selected=Math.min(drawing.selected,drawing.layers.size()-1);}));
        button(manage,"Up",()->moveLayer(1));button(manage,"Down",()->moveLayer(-1));button(manage,"Rename",()->{
            String name=JOptionPane.showInputDialog(this,"Layer name",drawing.active().name);
            if(name!=null&&!name.trim().isEmpty())edit(()->{if(name.length()>100)throw new java.io.IOException("Use at most 100 characters.");drawing.checkpoint();drawing.active().name=name.trim();});});right.add(manage);
        right.add(visible);visible.addActionListener(e->{if(!refreshing)edit(()->{drawing.checkpoint();drawing.active().visible=visible.isSelected();});});
        right.add(new JLabel("Layer opacity"));right.add(layerAlpha);
        layerAlpha.addChangeListener(e->{if(refreshing)return;try{
            if(!opacityGesture){drawing.checkpoint();opacityGesture=true;}
            drawing.active().opacity=layerAlpha.getValue()/100f;dirty=true;canvas.repaint();
            if(!layerAlpha.getValueIsAdjusting()){opacityGesture=false;refresh();}
        }catch(Exception ex){opacityGesture=false;error(ex);}});
        button(right,"Clear layer",()->edit(()->{drawing.checkpoint();drawing.active().image=new BufferedImage(drawing.width,drawing.height,BufferedImage.TYPE_INT_ARGB);}));
        right.add(Box.createVerticalStrut(20));right.add(new JLabel("REPLACE GAME UI"));right.add(slots);
        button(right,"New canvas for slot",()->newCanvas(((UISkins.Slot)slots.getSelectedItem()).width,((UISkins.Slot)slots.getSelectedItem()).height));
        button(right,"Assign artwork",()->{try{drawing.exportPNG(assignment());status.setText("Assigned "+slots.getSelectedItem()+". Restart the game to load it.");}catch(Exception ex){error(ex);}});
        button(right,"Open assigned artwork",()->{try{
            BufferedImage image=ParticleEffect.readImage(assignment().toFile());drawing.importImage(image);changed();
        }catch(Exception ex){error(ex);}});
        button(right,"Restore game default",()->{try{Files.deleteIfExists(assignment());status.setText("Default restored for "+slots.getSelectedItem()+". Restart the game.");}catch(Exception ex){error(ex);}});
        right.add(new JLabel("<html><div style='width:160px'>Artwork scales to the selected slot. Bar fills clip to the current value; labels stay live. Frame art overlays the bar. Transparent interiors work best.<br><br>Assignments are in ui-assets. Restart the game after assigning.</div></html>"));
        for(Component c:right.getComponents())if(c instanceof JComponent){JComponent item=(JComponent)c;item.setAlignmentX(LEFT_ALIGNMENT);
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE,item==layerScroll?260:item.getPreferredSize().height+4));}
        JScrollPane inspector=new JScrollPane(right);inspector.setPreferredSize(new Dimension(280,650));inspector.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);inspector.getVerticalScrollBar().setUnitIncrement(18);
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,view,inspector);split.setResizeWeight(1);split.setBorder(null);
        JPanel workspace=new JPanel(new BorderLayout(8,0));workspace.add(left,BorderLayout.WEST);workspace.add(split,BorderLayout.CENTER);add(workspace,BorderLayout.CENTER);add(status,BorderLayout.SOUTH);
        zoom.addChangeListener(e->canvas.updateZoom());
        bind("control Z","undo",()->history(false));bind("control Y","redo",()->history(true));bind("control shift Z","redo2",()->history(true));bind("control S","save",()->save(false));
        refresh();
    }
    private interface Action {void run() throws Exception;}
    private void edit(Action action){try{action.run();changed();}catch(Exception ex){error(ex);}}
    private void changed(){dirty=true;refresh();}
    private void refresh(){
        refreshing=true;layerModel.clear();for(int i=drawing.layers.size()-1;i>=0;i--)layerModel.addElement(drawing.layers.get(i));
        layerList.setSelectedIndex(drawing.layers.size()-1-drawing.selected);visible.setSelected(drawing.active().visible);layerAlpha.setValue(Math.round(drawing.active().opacity*100));
        undo.setEnabled(drawing.canUndo());redo.setEnabled(drawing.canRedo());colorLabel.setText(ParticleColorEditor.format(color));colorLabel.setBackground(color);
        colorLabel.setForeground(color.getRed()*.299+color.getGreen()*.587+color.getBlue()*.114>150?Color.BLACK:Color.WHITE);
        refreshing=false;canvas.revalidate();canvas.repaint();
    }
    private void moveLayer(int delta){int next=drawing.selected+delta;if(next<0||next>=drawing.layers.size())return;
        edit(()->{drawing.checkpoint();java.util.Collections.swap(drawing.layers,drawing.selected,next);drawing.selected=next;});}
    private void history(boolean forward){if(canvas.start!=null||!(forward?drawing.canRedo():drawing.canUndo()))return;edit(()->{if(forward)drawing.redo();else drawing.undo();});}
    private void bind(String key,String name,Runnable action){getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),name);
        getActionMap().put(name,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    private static void button(JPanel panel,String text,Runnable action){JButton b=new JButton(text);b.addActionListener(e->action.run());panel.add(b);}
    private void editColor(){Color original=color;ParticleColorEditor picker=new ParticleColorEditor(color,c->{color=c;refresh();});
        int result=JOptionPane.showOptionDialog(this,picker,"Paint color",JOptionPane.OK_CANCEL_OPTION,JOptionPane.PLAIN_MESSAGE,null,new String[]{"Apply","Cancel"},"Apply");
        color=result==0?picker.chooser.getColor():original;if(result==0)ParticleColorEditor.remember(color);refresh();}
    private boolean discard(){if(!dirty)return true;int choice=JOptionPane.showConfirmDialog(this,"Save your layered drawing before continuing?","Unsaved drawing",JOptionPane.YES_NO_CANCEL_OPTION);
        return choice==JOptionPane.NO_OPTION||choice==JOptionPane.YES_OPTION&&save(false);}
    private void newCanvas(int w,int h){if(discard()){drawing=new UIDrawing(w,h);dirty=false;currentFile=null;refresh();canvas.resetView();status.setText(w+" x "+h+" transparent canvas");}}
    private void newDrawing(){JSpinner w=new JSpinner(new SpinnerNumberModel(512,16,1024,1)),h=new JSpinner(new SpinnerNumberModel(256,16,1024,1));JPanel panel=new JPanel(new GridLayout(2,2,8,8));
        panel.add(new JLabel("Width"));panel.add(w);panel.add(new JLabel("Height"));panel.add(h);
        if(JOptionPane.showConfirmDialog(this,panel,"New canvas (16-1024 pixels)",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION)newCanvas((int)w.getValue(),(int)h.getValue());}
    private JFileChooser chooser(String title,String extension){JFileChooser c=new JFileChooser("ui-assets");c.setDialogTitle(title);c.setFileFilter(new FileNameExtensionFilter(title,""+extension));return c;}
    private void open(){if(!discard())return;JFileChooser c=chooser("Layered drawing (*.uidraw)","uidraw");if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        try{UIDrawing loaded=UIDrawing.load(c.getSelectedFile().toPath());drawing=loaded;currentFile=c.getSelectedFile().toPath();dirty=false;refresh();canvas.resetView();status.setText("Opened "+currentFile.getFileName());}catch(Exception ex){error(ex);}}
    private boolean save(boolean saveAs){Path path=currentFile;if(saveAs||path==null){path=savePath("Layered drawing (*.uidraw)","uidraw");if(path==null)return false;}
        try{drawing.save(path);currentFile=path;dirty=false;status.setText("Saved layers to "+path.getFileName());return true;}catch(Exception ex){error(ex);return false;}}
    private Path savePath(String title,String extension){JFileChooser c=chooser(title,extension);if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return null;
        Path p=c.getSelectedFile().toPath();if(!p.toString().toLowerCase(java.util.Locale.ROOT).endsWith("."+extension))p=Paths.get(p+"."+extension);
        if(Files.exists(p)&&JOptionPane.showConfirmDialog(this,"Replace "+p.getFileName()+"?","Save",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return null;return p;}
    private void importImage(){JFileChooser c=new JFileChooser();c.setFileFilter(new FileNameExtensionFilter("Images (PNG, JPEG, GIF first frame)","png","jpg","jpeg","gif"));
        if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;edit(()->drawing.importImage(ParticleEffect.readImage(c.getSelectedFile())));}
    private void exportPNG(){Path path=savePath("Flattened image (*.png)","png");if(path!=null)try{drawing.exportPNG(path);status.setText("Exported visible layers to "+path.getFileName());}catch(Exception ex){error(ex);}}
    private Path assignment(){return Paths.get("ui-assets",slots.getSelectedItem()+".png");}
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"UI Studio",JOptionPane.ERROR_MESSAGE);}

    private final class Canvas extends JComponent implements Scrollable {
        Point start,last,end;int originalX,originalY;boolean square;Tool activeTool;
        Point panStart,zoomAnchor;double panX,panY,panOriginalX,panOriginalY,viewScale=1;
        Canvas(){setFocusable(true);MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){
                if(SwingUtilities.isMiddleMouseButton(e)&&start==null){panStart=e.getPoint();panOriginalX=panX;panOriginalY=panY;setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));return;}
                if(panStart!=null||!SwingUtilities.isLeftMouseButton(e))return;requestFocusInWindow();Point p=point(e);
                if(p.x<0||p.y<0||p.x>=drawing.width||p.y>=drawing.height)return;
                activeTool=(Tool)tools.getSelectedItem();
                if(activeTool==Tool.EYEDROPPER){color=new Color(drawing.flatten().getRGB(p.x,p.y),true);color=new Color(color.getRed(),color.getGreen(),color.getBlue());refresh();return;}
                if(!drawing.active().visible){status.setText("Show the selected layer before drawing.");return;}
                if(activeTool==Tool.FILL){try{int count=drawing.floodFill(p.x,p.y,color,alpha.getValue()/100f);
                    if(count>0)changed();status.setText("Filled "+count+" connected pixels on "+drawing.active().name+".");
                }catch(Exception ex){error(ex);}return;}
                if(activeTool==Tool.TEXT){String text=JOptionPane.showInputDialog(UIEditor.this,"Text to draw");if(text!=null&&!text.isEmpty())edit(()->{
                    drawing.checkpoint();Graphics2D g=paintGraphics();g.setFont(new Font("SansSerif",Font.BOLD,Math.max(8,(int)brush.getValue())));g.drawString(text,p.x,p.y);g.dispose();});return;}
                try{drawing.checkpoint();}catch(Exception ex){error(ex);return;}
                start=last=end=p;square=e.isShiftDown();originalX=drawing.active().x;originalY=drawing.active().y;
                if(activeTool==Tool.BRUSH||activeTool==Tool.ERASER)stroke(p,p);repaint();}
            public void mouseDragged(MouseEvent e){if(panStart!=null){panX=panOriginalX+e.getX()-panStart.x;panY=panOriginalY+e.getY()-panStart.y;repaint();return;}
                if(start==null)return;end=point(e);square=e.isShiftDown();
                if(activeTool==Tool.BRUSH||activeTool==Tool.ERASER)stroke(last,end);
                if(activeTool==Tool.MOVE_LAYER){drawing.active().x=Math.max(-4096,Math.min(4096,originalX+end.x-start.x));drawing.active().y=Math.max(-4096,Math.min(4096,originalY+end.y-start.y));}
                last=end;repaint();}
            public void mouseReleased(MouseEvent e){if(SwingUtilities.isMiddleMouseButton(e)){panStart=null;setCursor(Cursor.getDefaultCursor());return;}
                if(!SwingUtilities.isLeftMouseButton(e)||start==null)return;
                if(activeTool==Tool.RECTANGLE||activeTool==Tool.ELLIPSE||activeTool==Tool.LINE){Graphics2D g=paintGraphics();shape(g,shape());g.dispose();}
                start=last=end=null;changed();}
            public void mouseWheelMoved(MouseWheelEvent e){e.consume();if(start!=null||panStart!=null)return;
                zoomAnchor=e.getPoint();try{zoom.setValue((int)Math.round(Math.max(zoom.getMinimum(),Math.min(zoom.getMaximum(),zoom.getValue()*Math.pow(1.12,-e.getPreciseWheelRotation())))));}
                finally{zoomAnchor=null;}}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        double scale(){return viewScale;}
        double originX(){return (getWidth()-drawing.width*scale())/2+panX;}
        double originY(){return (getHeight()-drawing.height*scale())/2+panY;}
        Point2D.Double imagePoint(double x,double y){return new Point2D.Double((x-originX())/scale(),(y-originY())/scale());}
        Point point(MouseEvent e){Point2D.Double p=imagePoint(e.getX(),e.getY());return new Point((int)Math.floor(p.x),(int)Math.floor(p.y));}
        void updateZoom(){Point anchor=zoomAnchor==null?new Point(getWidth()/2,getHeight()/2):zoomAnchor;
            Point2D.Double before=imagePoint(anchor.x,anchor.y);viewScale=zoom.getValue()/100.0;
            panX=anchor.x-getWidth()/2.0-(before.x-drawing.width/2.0)*scale();
            panY=anchor.y-getHeight()/2.0-(before.y-drawing.height/2.0)*scale();repaint();}
        void resetView(){if(start!=null||panStart!=null)return;
            int fit=(int)Math.floor(85*Math.min((double)Math.max(1,getWidth())/drawing.width,(double)Math.max(1,getHeight())/drawing.height));
            zoom.setValue(Math.max(zoom.getMinimum(),Math.min(zoom.getMaximum(),fit)));panX=panY=0;repaint();}
        @Override public Dimension getPreferredSize(){return new Dimension(700,600);}
        public Dimension getPreferredScrollableViewportSize(){return getPreferredSize();}
        public int getScrollableUnitIncrement(Rectangle r,int orientation,int direction){return 20;}
        public int getScrollableBlockIncrement(Rectangle r,int orientation,int direction){return 100;}
        public boolean getScrollableTracksViewportWidth(){return true;}
        public boolean getScrollableTracksViewportHeight(){return true;}
        Graphics2D paintGraphics(){Graphics2D g=drawing.active().image.createGraphics();g.translate(-drawing.active().x,-drawing.active().y);setup(g);return g;}
        void setup(Graphics2D g){g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(color);g.setComposite(AlphaComposite.SrcOver.derive(alpha.getValue()/100f));
            g.setStroke(new BasicStroke((int)brush.getValue(),BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));}
        void stroke(Point a,Point b){Graphics2D g=paintGraphics();if(activeTool==Tool.ERASER)g.setComposite(AlphaComposite.DstOut.derive(alpha.getValue()/100f));
            if(a.equals(b)){double size=(int)brush.getValue();g.fill(new Ellipse2D.Double(a.x-size/2,a.y-size/2,size,size));}else g.draw(new Line2D.Double(a,b));g.dispose();}
        Shape shape(){int dx=end.x-start.x,dy=end.y-start.y;if(square&&activeTool!=Tool.LINE){int side=Math.max(Math.abs(dx),Math.abs(dy));dx=dx<0?-side:side;dy=dy<0?-side:side;}
            if(activeTool==Tool.LINE)return new Line2D.Double(start.x,start.y,start.x+dx,start.y+dy);
            double x=Math.min(start.x,start.x+dx),y=Math.min(start.y,start.y+dy),w=Math.abs(dx),h=Math.abs(dy);
            return activeTool==Tool.ELLIPSE?new Ellipse2D.Double(x,y,w,h):new Rectangle2D.Double(x,y,w,h);}
        void shape(Graphics2D g,Shape s){if(activeTool!=Tool.LINE&&filled.isSelected())g.fill(s);else g.draw(s);}
        @Override protected void paintComponent(Graphics graphics){Graphics2D g=(Graphics2D)graphics.create();
            g.setColor(new Color(16,22,30));g.fillRect(0,0,getWidth(),getHeight());g.translate(originX(),originY());g.scale(scale(),scale());
            g.clipRect(0,0,drawing.width,drawing.height);for(int y=0;y<drawing.height;y+=12)for(int x=0;x<drawing.width;x+=12){g.setColor((x/12+y/12)%2==0?new Color(80,87,96):new Color(57,64,74));g.fillRect(x,y,12,12);}
            g.drawImage(drawing.flatten(),0,0,null);
            if(start!=null&&(activeTool==Tool.RECTANGLE||activeTool==Tool.ELLIPSE||activeTool==Tool.LINE)){setup(g);shape(g,shape());}g.dispose();}
    }
    public static void main(String[] args){SwingUtilities.invokeLater(()->{ParticleEditor.theme();JFrame frame=new JFrame("UI Studio");UIEditor editor=new UIEditor();frame.setContentPane(editor);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);frame.addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){if(editor.discard())frame.dispose();}});
        frame.setSize(1250,820);frame.setMinimumSize(new Dimension(1050,660));frame.setLocationRelativeTo(null);frame.setVisible(true);});}
}
