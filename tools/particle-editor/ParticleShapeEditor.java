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
        button(toolbar,"New cube",()->edit(ParticleMesh.cube()));button(toolbar,"Use built-in shape",()->edit(null));
        button(toolbar,"Undo",()->travel(undo,redo));button(toolbar,"Redo",()->travel(redo,undo));
        button(toolbar,"Frame shape",view::frame);add(toolbar,BorderLayout.NORTH);
        table.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
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
        notes.add(new JLabel("Drag to orbit; scroll to zoom. Edit X/Y/Z cells. Save the effect to keep its shape."));
        notes.add(new JLabel("Triangulated OBJ: max 512 vertices / 256 triangles. Geometry only; uses the effect color. Custom shape overrides sprite."));
        add(notes,BorderLayout.SOUTH);
        bind("control Z",()->travel(undo,redo));bind("control Y",()->travel(redo,undo));refresh();
    }
    void bind(String key,Runnable action){getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key),key);getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){action.run();}});}
    static void button(JPanel panel,String title,Runnable action){JButton b=new JButton(title);b.addActionListener(e->action.run());panel.add(b);}
    double value(int i){return ((Number)transforms[i].getValue()).doubleValue();}
    void edit(ParticleMesh mesh){undo.add(get.get());if(undo.size()>50)undo.remove(0);redo.clear();set.accept(mesh);refresh();view.frame();}
    void travel(ArrayList<ParticleMesh> from,ArrayList<ParticleMesh> to){if(from.isEmpty())return;if(table.isEditing())table.getCellEditor().cancelCellEditing();to.add(get.get());set.accept(from.remove(from.size()-1));refresh();view.frame();}
    void reset(){undo.clear();redo.clear();refresh();view.frame();}
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
        View(){setBackground(new Color(17,23,32));setPreferredSize(new Dimension(650,550));MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){drag=e.getPoint();}
            public void mouseReleased(MouseEvent e){drag=null;}
            public void mouseDragged(MouseEvent e){if(drag!=null){yaw+=(e.getX()-drag.x)*.01;pitch=Math.max(-1.55,Math.min(1.55,pitch+(e.getY()-drag.y)*.01));drag=e.getPoint();repaint();}}
            public void mouseWheelMoved(MouseWheelEvent e){distance=Math.max(.1,Math.min(10000000,distance*Math.exp(e.getPreciseWheelRotation()*.12)));repaint();}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        void frame(){double radius=.5;ParticleMesh mesh=get.get();if(mesh!=null)for(double[] v:mesh.vertices)radius=Math.max(radius,Math.sqrt(v[0]*v[0]+v[1]*v[1]+v[2]*v[2]));distance=radius*3;repaint();}
        protected void paintComponent(Graphics graphics){super.paintComponent(graphics);if(getWidth()<1||getHeight()<1)return;ParticleMesh mesh=get.get();if(mesh==null)return;
            java.util.List<SceneDepthRenderer.Face> faces=new ArrayList<>();
            for(int[] tri:mesh.triangles){double[][] vertices=mesh.face(tri,0,0,0,1,0);double[][] camera=new double[3][5];
                for(int i=0;i<3;i++){double[] v=vertices[i];double forward=v[0]*Math.sin(yaw)+v[2]*Math.cos(yaw);camera[i]=new double[]{v[0]*Math.cos(yaw)-v[2]*Math.sin(yaw),v[1]*Math.cos(pitch)-forward*Math.sin(pitch),distance-forward*Math.cos(pitch)-v[1]*Math.sin(pitch),0,0};}
                SceneDepthRenderer.Face f=SceneDepthRenderer.project(camera,getWidth()*.5,getHeight()*.5,Math.min(getWidth(),getHeight())*.95,ParticleMesh.shade(color.get(),vertices),mesh);if(f!=null)faces.add(f);
            }renderer.render(getWidth(),getHeight(),faces,Collections.emptySet());graphics.drawImage(renderer.image,0,0,null);
        }
    }

}
