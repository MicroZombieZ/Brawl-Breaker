import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.event.*;

/** Live color editing with Swing's RGB/HSV controls, hex input, and session history. */
final class ParticleColorEditor extends JPanel {
    private static final java.util.List<Color> recent=new ArrayList<>(Arrays.asList(
            new Color(255,198,91),new Color(167,219,255),new Color(255,112,112),
            new Color(155,255,190),new Color(190,145,255),Color.WHITE));
    final JColorChooser chooser;
    final JTextField hex=new JTextField(8);
    private final JPanel swatch=new JPanel();
    private final JLabel validation=new JLabel("Enter six hex digits, for example #FFC65B.");
    private boolean syncing;

    ParticleColorEditor(Color initial,Consumer<Color> preview) {
        super(new BorderLayout(8,8));setBorder(BorderFactory.createEmptyBorder(12,12,12,12));
        chooser=new JColorChooser(initial);
        chooser.setPreviewPanel(new JPanel());
        JPanel top=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        top.add(new JLabel("Hex color"));top.add(hex);
        swatch.setPreferredSize(new Dimension(90,30));swatch.setBorder(BorderFactory.createLineBorder(Color.GRAY));top.add(swatch);
        add(top,BorderLayout.NORTH);add(chooser,BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(4,6));
        JPanel history=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));history.add(new JLabel("Recent / palette"));
        for(Color color:recent) {
            JButton button=new JButton();button.setPreferredSize(new Dimension(28,28));
            button.setIcon(new javax.swing.Icon(){
                public int getIconWidth(){return 18;}public int getIconHeight(){return 18;}
                public void paintIcon(Component c,Graphics g,int x,int y){g.setColor(color);g.fillRect(x,y,18,18);}
            });
            button.setToolTipText(format(color));button.addActionListener(e->chooser.setColor(color));history.add(button);
        }
        bottom.add(history,BorderLayout.NORTH);bottom.add(validation,BorderLayout.SOUTH);add(bottom,BorderLayout.SOUTH);
        chooser.getSelectionModel().addChangeListener(e->{
            if(syncing)return;
            sync();preview.accept(chooser.getColor());
        });
        hex.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){editHex();}
            public void removeUpdate(DocumentEvent e){editHex();}
            public void changedUpdate(DocumentEvent e){editHex();}
            private void editHex(){
                if(syncing)return;
                Color color=parse(hex.getText());
                if(color==null){validation.setText("Use #RRGGBB (six hex digits). The previous color is kept.");return;}
                // Do not replace text inside its document notification.
                syncing=true;
                try{chooser.setColor(color);swatch.setBackground(color);validation.setText("Color previews live. Apply to keep it; Cancel to restore.");}
                finally{syncing=false;}
                preview.accept(color);
            }
        });
        sync();
    }
    private void sync(){
        syncing=true;
        try{hex.setText(format(chooser.getColor()));swatch.setBackground(chooser.getColor());
            validation.setText("Color previews live. Apply to keep it; Cancel to restore.");}
        finally{syncing=false;}
    }
    static Color parse(String value){
        String text=value.trim();if(text.startsWith("#"))text=text.substring(1);
        if(!text.matches("[0-9a-fA-F]{6}"))return null;
        return new Color(Integer.parseInt(text,16));
    }
    static String format(Color color){return String.format(Locale.ROOT,"#%02X%02X%02X",color.getRed(),color.getGreen(),color.getBlue());}
    static void remember(Color color){recent.remove(color);recent.add(0,color);while(recent.size()>8)recent.remove(recent.size()-1);}
}
