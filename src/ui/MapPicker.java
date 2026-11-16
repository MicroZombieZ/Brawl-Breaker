import java.awt.*;
import javax.swing.*;

/** File choices are loaded at match start, with failures kept in the menu. */
final class MapPicker extends JPanel {
    final JComboBox<MapStorage.Choice> maps=new JComboBox<>();
    final JButton refresh=new JButton("Refresh maps");
    final JLabel message=new JLabel("Choose a map, or Random.");
    MapPicker(){super(new BorderLayout(6,6));setOpaque(false);JPanel row=new JPanel(new BorderLayout(6,0));row.setOpaque(false);row.add(maps,BorderLayout.CENTER);row.add(refresh,BorderLayout.EAST);add(row,BorderLayout.NORTH);add(message,BorderLayout.SOUTH);refresh.addActionListener(e->reload());reload();}
    void reload(){String previous=maps.getSelectedItem()==null?"Random":maps.getSelectedItem().toString();
        try{java.util.List<MapStorage.Choice> choices=MapStorage.choices(MapStorage.DIRECTORY);maps.removeAllItems();for(MapStorage.Choice c:choices){maps.addItem(c);if(c.label.equals(previous))maps.setSelectedItem(c);}message.setText("Choose a map, or Random.");}
        catch(java.io.IOException ex){message.setText("Cannot list maps: "+ex.getMessage());}}
    MapDefinition load() throws java.io.IOException {MapStorage.Choice choice=(MapStorage.Choice)maps.getSelectedItem();if(choice==null)throw new java.io.IOException("No map selected.");return choice.load();}
    void available(boolean enabled){maps.setEnabled(enabled);refresh.setEnabled(enabled);}
}
