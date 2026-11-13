import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;

/** Shape workspace owned by Particle Studio; edits replace immutable meshes. */
final class ParticleShapeEditor extends JPanel {
    final Supplier<ParticleMesh> get;
    final Consumer<ParticleMesh> set;
    final Supplier<Color> color;
    final ArrayList<ParticleMesh> undo=new ArrayList<>(),redo=new ArrayList<>();
    final VertexTable model=new VertexTable();
    final JTable table=new JTable(model);
    final View view=new View();
    final JLabel info=new JLabel();
    final JSpinner[] transforms=new JSpinner[6];
    ParticleShapeEditor(Supplier<ParticleMesh> get,Consumer<ParticleMesh> set,Supplier<Color> color){
        super(new BorderLayout(8,8));this.get=get;this.set=set;this.color=color;
        JPanel toolbar=new JPanel(new FlowLayout(FlowLayout.LEFT));
        button(toolbar,"Import OBJ",this::importMesh);button(toolbar,"Export OBJ",this::exportMesh);
        JComboBox<ParticleMesh.Primitive> primitive=new JComboBox<>(ParticleMesh.Primitive.values());toolbar.add(primitive);
        button(toolbar,"Create shape",()->edit(ParticleMesh.primitive((ParticleMesh.Primitive)primitive.getSelectedItem())));button(toolbar,"Use built-in shape",()->edit(null));
        button(toolbar,"Undo",()->travel(undo,redo));button(toolbar,"Redo",()->travel(redo,undo));
        button(toolbar,"Frame shape",view::frame);add(toolbar,BorderLayout.NORTH);
        table.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e->view.repaint());
        table.setBackground(new Color(29,37,49));table.setForeground(new Color(222,233,243));table.setGridColor(new Color(70,87,104));
        JScrollPane vertices=new JScrollPane(table);vertices.setColumnHeaderView(table.getTableHeader());
        vertices.getViewport().setBackground(new Color(29,37,49));
        JPanel details=new JPanel(new BorderLayout(4,4));details.add(vertices,BorderLayout.CENTER);
        JPanel controls=new JPanel(new GridLayout(0,2,6,5));String[] labels={"Scale X","Scale Y","Scale Z","Rotate X (deg)","Rotate Y (deg)","Rotate Z (deg)"};
        for(int i=0;i<6;i++){transforms[i]=new JSpinner(new SpinnerNumberModel(i<3?1.0:0.0,i<3?.01:-360.0,i<3?100.0:360.0,i<3?.1:5.0));controls.add(new JLabel(labels[i]));controls.add(transforms[i]);}
        button(controls,"Apply transform",()->{if(get.get()!=null)try{for(JSpinner s:transforms)s.commitEdit();edit(get.get().transform(value(0),value(1),value(2),value(3),value(4),value(5)));for(int i=0;i<6;i++)transforms[i].setValue(i<3?1.0:0.0);}catch(Exception ex){error(ex);}});
        button(controls,"Center / normalize",()->{if(get.get()!=null)try{edit(get.get().normalize());}catch(IOException ex){error(ex);}});
        details.add(controls,BorderLayout.SOUTH);details.setPreferredSize(new Dimension(310,500));
        JSplitPane split=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,view,details);split.setResizeWeight(1);add(split,BorderLayout.CENTER);
        JPanel notes=new JPanel(new GridLayout(0,1));notes.add(info);
        notes.add(new JLabel("Left-drag a vertex to move it. Right/middle-drag or drag empty space to orbit; scroll to zoom; Esc cancels a drag."));
        notes.add(new JLabel("Triangulated OBJ: max 512 vertices / 256 triangles. Geometry only; uses the effect color. Custom shape overrides sprite."));
        add(notes,BorderLayout.SOUTH);
        bind("ESCAPE",view::cancelVertex);bind("control Z",()->travel(undo,redo));bind("control Y",()->travel(redo,undo));refresh();
    }
    void bind(String key,Runnable action){getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),key);getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    static void button(JPanel panel,String title,Runnable action){JButton b=new JButton(title);b.addActionListener(e->action.run());panel.add(b);}
    double value(int i){return ((Number)transforms[i].getValue()).doubleValue();}
    void edit(ParticleMesh mesh){undo.add(get.get());if(undo.size()>50)undo.remove(0);redo.clear();set.accept(mesh);refresh();view.frame();}
    void travel(ArrayList<ParticleMesh> from,ArrayList<ParticleMesh> to){view.finishVertex();if(from.isEmpty())return;if(table.isEditing())table.getCellEditor().cancelCellEditing();to.add(get.get());set.accept(from.remove(from.size()-1));refresh();view.frame();}
    void reset(){view.cancelVertex();undo.clear();redo.clear();refresh();view.frame();}
    void refresh(){model.fireTableDataChanged();ParticleMesh m=get.get();info.setText(m==null?"No custom shape: the effect uses its image or built-in shape.":m.vertices.length+" vertices / "+m.triangles.length+" triangles — active particle shape");view.repaint();}
    JFileChooser chooser(){JFileChooser c=new JFileChooser("particle-effects");c.setFileFilter(new FileNameExtensionFilter("Triangulated Wavefront OBJ","obj"));return c;}
    void importMesh(){JFileChooser c=chooser();if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{ParticleMesh m=ParticleMesh.read(c.getSelectedFile().toPath()).normalize();edit(m);}catch(IOException ex){error(ex);}}
    void exportMesh(){if(get.get()==null)return;JFileChooser c=chooser();if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;Path p=c.getSelectedFile().toPath();if(!p.toString().toLowerCase(Locale.ROOT).endsWith(".obj"))p=Paths.get(p+".obj");if(Files.exists(p)&&JOptionPane.showConfirmDialog(this,"Replace "+p.getFileName()+"?","Export shape",JOptionPane.YES_NO_OPTION)!=JOptionPane.YES_OPTION)return;try{Files.writeString(p,get.get().obj());}catch(IOException ex){error(ex);}}
    void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Particle shape",JOptionPane.ERROR_MESSAGE);}
    final class VertexTable extends AbstractTableModel {
        public int getRowCount(){return get==null||get.get()==null?0:get.get().vertices.length;}
        public int getColumnCount(){return 4;}
        public String getColumnName(int c){return new String[]{"Vertex","X","Y","Z"}[c];}
        public Object getValueAt(int r,int c){if(c==0)return r+1;return get.get().vertices[r][c-1];}
        public boolean isCellEditable(int r,int c){return c>0;}
        public void setValueAt(Object value,int r,int c){try{ParticleMesh m=get.get();double[][] vertices=new double[m.vertices.length][];for(int i=0;i<vertices.length;i++)vertices[i]=m.vertices[i].clone();vertices[r][c-1]=Double.parseDouble(value.toString());edit(new ParticleMesh(vertices,m.triangles));}catch(Exception ex){error(ex);}}
    }
    final class View extends JPanel {
        final SceneDepthRenderer renderer=new SceneDepthRenderer();double yaw=.6,pitch=.4,distance=3;Point drag;
        ParticleMesh original;int grabbed=-1;Point start;boolean moved;
        View(){setFocusable(true);setBackground(new Color(17,23,32));setPreferredSize(new Dimension(650,550));MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){requestFocusInWindow();if(table.isEditing())table.getCellEditor().stopCellEditing();
                int vertex=SwingUtilities.isLeftMouseButton(e)?pick(e.getPoint()):-1;
                if(vertex>=0){grabbed=vertex;original=get.get();start=e.getPoint();moved=false;table.setRowSelectionInterval(vertex,vertex);table.scrollRectToVisible(table.getCellRect(vertex,0,true));repaint();}else drag=e.getPoint();}
            public void mouseReleased(MouseEvent e){finishVertex();drag=null;}
            public void mouseDragged(MouseEvent e){if(original!=null){moveVertex(e.getPoint());return;}if(drag!=null){yaw+=(e.getX()-drag.x)*.01;pitch=Math.max(-1.55,Math.min(1.55,pitch+(e.getY()-drag.y)*.01));drag=e.getPoint();repaint();}}
            public void mouseWheelMoved(MouseWheelEvent e){if(original!=null)return;distance=Math.max(.1,Math.min(10000000,distance*Math.exp(e.getPreciseWheelRotation()*.12)));repaint();}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        void frame(){finishVertex();double radius=.5;ParticleMesh mesh=get.get();if(mesh!=null)for(double[] v:mesh.vertices)radius=Math.max(radius,Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]));distance=radius*3;repaint();}
        double[] camera(double[] v){double forward=v[0]*Math.sin(yaw)+v[2]*Math.cos(yaw);return new double[]{v[0]*Math.cos(yaw)-v[2]*Math.sin(yaw),v[1]*Math.cos(pitch)-forward*Math.sin(pitch),distance-forward*Math.cos(pitch)-v[1]*Math.sin(pitch),0,0};}
        double[] screen(double[] v){double[] c=camera(v);double focal=Math.min(getWidth(),getHeight())*.95;return new double[]{getWidth()*.5+c[0]*focal/c[2],getHeight()*.5-c[1]*focal/c[2],c[2]};}
        java.util.List<SceneDepthRenderer.Face> faces(ParticleMesh mesh){java.util.List<SceneDepthRenderer.Face> faces=new ArrayList<>();
            for(int[] tri:mesh.triangles){double[][] vertices=mesh.face(tri,0,0,0,1,0),cam=new double[3][];for(int i=0;i<3;i++)cam[i]=camera(vertices[i]);SceneDepthRenderer.Face f=SceneDepthRenderer.project(cam,getWidth()*.5,getHeight()*.5,Math.min(getWidth(),getHeight())*.95,ParticleMesh.shade(color.get(),vertices),mesh);if(f!=null)faces.add(f);}return faces;}
        boolean visible(double[] p,java.util.List<SceneDepthRenderer.Face> faces){if(p[2]<.1||p[0]<0||p[1]<0||p[0]>=getWidth()||p[1]>=getHeight())return false;for(SceneDepthRenderer.Face f:faces)if(SceneDepthRenderer.at(f,p[0],p[1])>1/p[2]+1e-7)return false;return true;}
        int pick(Point point){ParticleMesh mesh=get.get();if(mesh==null)return -1;java.util.List<SceneDepthRenderer.Face> faces=faces(mesh);int picked=-1;double best=81,depth=Double.POSITIVE_INFINITY;
            for(int i=0;i<mesh.vertices.length;i++){double[] p=screen(mesh.vertices[i]);double d=point.distanceSq(p[0],p[1]);if(visible(p,faces)&&(d<best-1e-6||Math.abs(d-best)<1e-6&&p[2]<depth)){picked=i;best=d;depth=p[2];}}return picked;}
        void moveVertex(Point point){if(original==null)return;double scale=camera(original.vertices[grabbed])[2]/(Math.min(getWidth(),getHeight())*.95);double dx=(point.x-start.x)*scale,dy=-(point.y-start.y)*scale;
            double[][] v=new double[original.vertices.length][];for(int i=0;i<v.length;i++)v[i]=original.vertices[i].clone();
            v[grabbed][0]+=dx*Math.cos(yaw)-dy*Math.sin(yaw)*Math.sin(pitch);v[grabbed][1]+=dy*Math.cos(pitch);v[grabbed][2]+=-dx*Math.sin(yaw)-dy*Math.cos(yaw)*Math.sin(pitch);
            try{set.accept(new ParticleMesh(v,original.triangles));moved=!point.equals(start);model.fireTableRowsUpdated(grabbed,grabbed);repaint();}catch(IOException ex){cancelVertex();error(ex);}}
        void finishVertex(){if(original!=null&&moved){undo.add(original);if(undo.size()>50)undo.remove(0);redo.clear();}original=null;grabbed=-1;moved=false;}
        void cancelVertex(){if(original!=null){set.accept(original);original=null;grabbed=-1;moved=false;model.fireTableDataChanged();repaint();}drag=null;}
        protected void paintComponent(Graphics graphics){super.paintComponent(graphics);if(getWidth()<1||getHeight()<1)return;ParticleMesh mesh=get.get();if(mesh==null)return;
            java.util.List<SceneDepthRenderer.Face> faces=faces(mesh);
            renderer.render(getWidth(),getHeight(),faces,Collections.emptySet());graphics.drawImage(renderer.image,0,0,null);
            for(int i=0;i<mesh.vertices.length;i++){double[] p=screen(mesh.vertices[i]);if(!visible(p,faces))continue;int x=(int)Math.round(p[0]),y=(int)Math.round(p[1]);boolean selected=table.getSelectedRow()==i;
                graphics.setColor(Color.BLACK);graphics.fillOval(x-5,y-5,10,10);graphics.setColor(selected?Color.YELLOW:new Color(140,225,255));graphics.fillOval(x-3,y-3,6,6);if(selected)graphics.drawString("Vertex "+(i+1),x+8,y-8);}

        }
    }

}
