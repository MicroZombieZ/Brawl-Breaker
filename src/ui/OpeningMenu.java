import java.awt.*;
import javax.swing.*;

/** Entry points for local practice and LAN play. */
final class OpeningMenu extends JPanel {
    final JButton host=new JButton("Host"),join=new JButton("Join"),quickMatch=new JButton("Quick Match"),practice=new JButton("Practice");
    final MapPicker mapPicker=new MapPicker();
    final JButton playMap=new JButton("Start Practice"),cancelMap=new JButton("Back");
    final JPanel mapSelection=new JPanel();
    final JButton reload=new JButton("Reload Game");
    final JButton quit=new JButton("Quit");
    OpeningMenu(Runnable onHost,Runnable onJoin,Runnable onPractice,Runnable onQuit){
        setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
        setBackground(new Color(24,34,49));
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(72,108,137)),BorderFactory.createEmptyBorder(32,48,32,48)));
        JLabel title=new JLabel("CUBE MOVEMENT");title.setFont(new Font(Font.SANS_SERIF,Font.BOLD,32));title.setForeground(Color.WHITE);
        JLabel subtitle=new JLabel("Play together. Train your moves.");subtitle.setForeground(new Color(174,195,215));
        for(JComponent c:new JComponent[]{title,subtitle,host,join,quickMatch,practice,reload,quit}){
            c.setAlignmentX(Component.CENTER_ALIGNMENT);add(c);add(Box.createVerticalStrut(16));
        }
        for(JButton b:new JButton[]{host,join,quickMatch,practice,reload,quit}){b.setMaximumSize(new Dimension(310,46));b.setPreferredSize(new Dimension(310,46));b.setFont(new Font(Font.SANS_SERIF,Font.BOLD,18));}
        mapSelection.setOpaque(false);mapSelection.setLayout(new BoxLayout(mapSelection,BoxLayout.Y_AXIS));mapPicker.message.setForeground(Color.WHITE);mapSelection.add(mapPicker);mapSelection.add(playMap);mapSelection.add(cancelMap);add(mapSelection);mapSelection.setVisible(false);
        playMap.addActionListener(e->onPractice.run());cancelMap.addActionListener(e->showMaps(false));
        quit.addActionListener(e->onQuit.run());
        quickMatch.setEnabled(false);quickMatch.setToolTipText("Quick Match is not available yet.");
        host.addActionListener(e->onHost.run());join.addActionListener(e->onJoin.run());practice.addActionListener(e->{mapPicker.reload();showMaps(true);});
        setVisible(false);
    }
    void showMaps(boolean show){for(JButton b:new JButton[]{host,join,quickMatch,practice,reload,quit})b.setVisible(!show);mapSelection.setVisible(show);revalidate();repaint();}
}
