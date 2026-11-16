import java.awt.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Ordered effect steps; delay is relative to the previous step's start. */
final class ParticleTimeline extends JPanel {
    final DefaultListModel<ParticleSequence.Step> model=new DefaultListModel<>();
    final JList<ParticleSequence.Step> list=new JList<>(model);
    final JSpinner delay=new JSpinner(new SpinnerNumberModel(0.0,0.0,30.0,.05));
    final JTextField name=new JTextField(12);
    final JProgressBar progress=new JProgressBar(0,1000);
    final Supplier<ParticleEffect> current;final Consumer<ParticleEffect> select;final Runnable changed;
    int selected;boolean updating;
    ParticleTimeline(Supplier<ParticleEffect> current,Consumer<ParticleEffect> select,Runnable changed){
        super(new BorderLayout(5,5));this.current=current;this.select=select;this.changed=changed;
        javax.swing.border.TitledBorder border=BorderFactory.createTitledBorder("Effect timeline");border.setTitleColor(new Color(222,233,243));setBorder(border);setPreferredSize(new Dimension(600,180));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);list.setBackground(new Color(29,37,49));list.setForeground(new Color(222,233,243));delay.setEditor(new JSpinner.NumberEditor(delay,"0.00"));delay.setPreferredSize(new Dimension(80,24));list.setVisibleRowCount(3);
        list.setCellRenderer(new DefaultListCellRenderer(){public Component getListCellRendererComponent(JList<?> l,Object value,int index,boolean selected,boolean focus){double start=0;for(int i=0;i<=index;i++)start+=model.get(i).wait;return super.getListCellRendererComponent(l,String.format(Locale.ROOT,"%.2fs  |  %s",start,value),index,selected,focus);}});
        add(new JScrollPane(list),BorderLayout.CENTER);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT,4,0));
        button(actions,"Add",()->add(ParticleEffect.preset(ParticleEffect.Pattern.SPARKLE),"Effect "+(model.size()+1)));
        button(actions,"Duplicate",()->add(current.get().copy(),name.getText()+" copy"));button(actions,"Import effect",this::importEffect);
        button(actions,"Remove",this::remove);button(actions,"Up",()->move(-1));button(actions,"Down",()->move(1));add(actions,BorderLayout.NORTH);
        JPanel bottom=new JPanel(new BorderLayout());JPanel fields=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));fields.add(new JLabel("Name"));fields.add(name);fields.add(new JLabel("Wait after previous start (s)"));fields.add(delay);bottom.add(fields,BorderLayout.NORTH);progress.setStringPainted(true);bottom.add(progress,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        list.addListSelectionListener(e->{if(!updating&&!e.getValueIsAdjusting()&&list.getSelectedIndex()>=0){commit();selected=list.getSelectedIndex();select.accept(model.get(selected).effect);fields();}});
        delay.addChangeListener(e->{if(!updating){model.get(selected).wait=((Number)delay.getValue()).doubleValue();list.repaint();changed.run();}});
        name.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){update();}public void removeUpdate(DocumentEvent e){update();}public void changedUpdate(DocumentEvent e){update();}void update(){if(!updating){String text=name.getText();model.get(selected).name=text.substring(0,Math.min(64,text.length()));list.repaint();changed.run();}}});
        load(current.get());
    }
    static void button(JPanel p,String title,Runnable action){JButton b=new JButton(title);b.addActionListener(e->action.run());p.add(b);}
    void commit(){if(!model.isEmpty())model.get(selected).effect=current.get();}
    void fields(){updating=true;name.setText(model.get(selected).name);delay.setValue(model.get(selected).wait);list.setSelectedIndex(selected);updating=false;}
    void load(ParticleEffect effect){updating=true;model.clear();if(effect.sequence==null)model.addElement(new ParticleSequence.Step("Effect 1",0,effect));else for(ParticleSequence.Step s:effect.sequence)model.addElement(s);selected=0;updating=false;fields();}
    ParticleEffect document(){commit();if(model.size()==1&&model.get(0).wait==0&&model.get(0).name.equals("Effect 1"))return model.get(0).effect;
        ParticleEffect root=new ParticleEffect();root.sequence=new ArrayList<>();for(int i=0;i<model.size();i++)root.sequence.add(model.get(i));root.interval=model.get(0).effect.interval;return root;}
    double cycle(){ParticleEffect root=document();return root.sequence==null?root.interval:ParticleSequence.duration(root)+root.interval;}
    void add(ParticleEffect effect,String title){if(model.size()>=16)return;commit();updating=true;model.addElement(new ParticleSequence.Step(title.substring(0,Math.min(64,title.length())),.5,effect));selected=model.size()-1;updating=false;select.accept(effect);fields();changed.run();}
    void remove(){if(model.size()==1)return;commit();updating=true;model.remove(selected);selected=Math.min(selected,model.size()-1);updating=false;select.accept(model.get(selected).effect);fields();changed.run();}
    void move(int direction){int next=selected+direction;if(next<0||next>=model.size())return;commit();updating=true;ParticleSequence.Step step=model.remove(selected);model.add(next,step);selected=next;updating=false;fields();changed.run();}
    void importEffect(){JFileChooser c=new JFileChooser("particle-effects");c.setFileFilter(new FileNameExtensionFilter("Particle effect (*.pfx)","pfx"));if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;try{ParticleEffect e=ParticleEffect.load(c.getSelectedFile().toPath());if(e.sequence!=null)throw new java.io.IOException("Use Open effect to open a whole timeline. Import a single effect here.");add(e,c.getSelectedFile().getName());}catch(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Import effect",JOptionPane.ERROR_MESSAGE);}}
    void playhead(double seconds){double total=cycle();progress.setValue((int)(1000*Math.min(1,seconds/total)));progress.setString(String.format(Locale.ROOT,"Timeline %.2fs / %.2fs | first wait is from Play; loops after all effects finish",seconds,total));}
}
